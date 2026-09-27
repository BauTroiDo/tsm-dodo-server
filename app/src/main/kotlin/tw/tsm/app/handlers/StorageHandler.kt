package tw.tsm.app.handlers

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.constants.BankResult
import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.data.ItemDef
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.PacketHandler
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.gamelogic.systems.InventorySystem
import tw.tsm.gamelogic.systems.RoleCountSystem
import tw.tsm.protocol.PacketReader

private val logger = KotlinLogging.logger {}

/**
 * 寶庫處理器（mainKind = 102）
 *
 * 來源：protocal.lua protocolTable[102]
 * C:102-001 領物品, C:102-002 存物品
 *
 * 寶庫與錢莊平行，額外限制：
 * - restrict bit 32 禁止存入
 * - 裝備（fitType > 0）禁止存入
 * - 有 deleteTime 的物品禁止存入
 *
 * @param itemDefs 物品定義表，用於限制驗證
 */
class StorageHandler(
    private val inventorySystem: InventorySystem,
    private val responseSender: ResponseSender,
    private val itemDefs: Map<Int, ItemDef>,
    private val roleCountSystem: RoleCountSystem
) : PacketHandler {

    override val mainKind: Int = Opcodes.STORAGE  // 102

    companion object {
        /** 單次封包最大操作筆數（防止惡意封包） */
        private const val MAX_BATCH = 50
    }

    override suspend fun handle(session: GameSession, subKind: Int, data: ByteArray) {
        if (session.state != SessionState.IN_GAME) return

        when (subKind) {
            1 -> handleWithdraw(session, data)
            2 -> handleDeposit(session, data)
            else -> logger.warn { "未處理的寶庫子協定：subKind=$subKind" }
        }
    }

    /**
     * C:102-001 寶庫領物品
     *
     * 格式：<<+index(1) +quantity(4)>>
     * 來源：UIBank.lua:479
     */
    private suspend fun handleWithdraw(session: GameSession, data: ByteArray) {
        if (data.size < 5) return
        val reader = PacketReader(data)
        val characterId = session.roleId.toLong()
        var processed = 0

        while (reader.remaining >= 5 && processed < MAX_BATCH) {
            processed++
            val storageSlot = reader.readUByte()
            val quantity = reader.readIntLE()

            val storageItem = inventorySystem.getStorageItem(characterId, storageSlot) ?: continue
            val withdrawQty = minOf(quantity, storageItem.quantity)

            val (errorCode, bagSlot, bagThing) = inventorySystem.withdrawFromStorage(characterId, storageSlot, withdrawQty)
            if (errorCode == BankResult.SUCCESS && bagThing != null) {
                responseSender.sendStorageReduceItem(session, storageSlot, withdrawQty)
                responseSender.sendSetBagItem(session, bagSlot, bagThing, showMsg = false)
            } else {
                responseSender.sendStorageFail(session, errorCode)
            }
        }
    }

    /**
     * C:102-002 寶庫存物品
     *
     * 格式：<<+index(2) +quantity(4)>>
     * 來源：UIBank.lua:480
     */
    private suspend fun handleDeposit(session: GameSession, data: ByteArray) {
        if (data.isEmpty() || data.size % 6 != 0) return
        val reader = PacketReader(data)
        val characterId = session.roleId.toLong()
        var processed = 0

        while (reader.remaining >= 6 && processed < MAX_BATCH) {
            processed++
            val bagSlot = reader.readUShortLE()
            val quantity = reader.readIntLE()

            val bagItem = inventorySystem.getBagItem(characterId, bagSlot) ?: continue

            // 寶庫限制驗證：restrict bit 32、裝備、過期物品
            val checkResult = inventorySystem.canDepositToStorage(bagItem)
            if (checkResult != BankResult.SUCCESS) {
                responseSender.sendStorageFail(session, checkResult)
                continue
            }

            val depositQty = minOf(quantity, bagItem.quantity)
            // 動態格數：(Storage_1+Storage_2)×5（私服預設 Storage_1=14 → 70 格）
            val maxSlots = roleCountSystem.getStorageCapacity(characterId)

            val (errorCode, storageSlot, storageThing) = inventorySystem.depositToStorage(
                characterId, bagSlot, depositQty, maxSlots
            )
            if (errorCode == BankResult.SUCCESS && storageThing != null) {
                responseSender.sendReduceBagItem(session, bagSlot, depositQty)
                responseSender.sendStorageSetItem(session, storageSlot, storageThing)
            } else {
                responseSender.sendStorageFail(session, errorCode)
            }
        }
    }
}
