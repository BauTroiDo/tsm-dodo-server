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
 * 錢莊處理器（mainKind = 30）
 *
 * 來源：protocal.lua protocolTable[30]
 * C:030-001 領物品, C:030-002 存物品, C:030-003 升級, C:030-008 關閉錢莊
 *
 * @param itemDefs 物品定義表，用於 restrict 驗證
 */
class BankHandler(
    private val inventorySystem: InventorySystem,
    private val responseSender: ResponseSender,
    private val itemDefs: Map<Int, ItemDef>,
    private val roleCountSystem: RoleCountSystem
) : PacketHandler {

    override val mainKind: Int = Opcodes.BANK  // 30

    companion object {
        /** 單次封包最大操作筆數（防止惡意封包） */
        private const val MAX_BATCH = 50
    }

    override suspend fun handle(session: GameSession, subKind: Int, data: ByteArray) {
        if (session.state != SessionState.IN_GAME) return

        when (subKind) {
            1 -> handleWithdraw(session, data)
            2 -> handleDeposit(session, data)
            3 -> handleUpgrade(session)
            8 -> handleClose(session)
            else -> logger.warn { "未處理的錢莊子協定：subKind=$subKind" }
        }
    }

    /**
     * C:030-001 錢莊領物品
     *
     * 格式：<<+index(1) +quantity(4)>>
     * 來源：UIBank.lua:477
     */
    private suspend fun handleWithdraw(session: GameSession, data: ByteArray) {
        if (data.size < 5) return
        val reader = PacketReader(data)
        val characterId = session.roleId.toLong()
        var processed = 0

        while (reader.remaining >= 5 && processed < MAX_BATCH) {
            processed++
            val bankSlot = reader.readUByte()
            val quantity = reader.readIntLE()

            val bankItem = inventorySystem.getBankItem(characterId, bankSlot) ?: continue
            val withdrawQty = minOf(quantity, bankItem.quantity)

            val (errorCode, bagSlot, bagThing) = inventorySystem.withdrawFromBank(characterId, bankSlot, withdrawQty)
            if (errorCode == BankResult.SUCCESS && bagThing != null) {
                responseSender.sendBankReduceItem(session, bankSlot, withdrawQty)
                responseSender.sendSetBagItem(session, bagSlot, bagThing, showMsg = false)
            } else {
                responseSender.sendBankFail(session, errorCode)
            }
        }
    }

    /**
     * C:030-002 錢莊存物品
     *
     * 格式：<<+index(1) +quantity(4)>>
     * 來源：UIBank.lua:478
     */
    private suspend fun handleDeposit(session: GameSession, data: ByteArray) {
        if (data.size < 5) return
        val reader = PacketReader(data)
        val characterId = session.roleId.toLong()
        var processed = 0

        while (reader.remaining >= 5 && processed < MAX_BATCH) {
            processed++
            val bagSlot = reader.readUByte()
            val quantity = reader.readIntLE()

            val bagItem = inventorySystem.getBagItem(characterId, bagSlot) ?: continue

            // 伺服器端 restrict bit 32 驗證（UIBank.lua:165）
            if (!inventorySystem.canDepositToBank(bagItem.itemId)) {
                logger.debug { "${session.characterName} 嘗試存入禁止物品 itemId=${bagItem.itemId}" }
                continue
            }

            val depositQty = minOf(quantity, bagItem.quantity)
            // 動態格數：25 + (Bank_1+Bank_2+Bank_3)×5（介消 sellId=1 擴充）
            val maxSlots = roleCountSystem.getBankCapacity(characterId)

            val (errorCode, bankSlot, bankThing) = inventorySystem.depositToBank(characterId, bagSlot, depositQty, maxSlots)
            if (errorCode == BankResult.SUCCESS && bankThing != null) {
                responseSender.sendReduceBagItem(session, bagSlot, depositQty)
                responseSender.sendBankSetItem(session, bankSlot, bankThing)
            } else {
                responseSender.sendBankFail(session, errorCode)
            }
        }
    }

    /**
     * C:030-003 錢莊升級
     *
     * 錢莊/寶庫擴充已由介消系統處理（UISell.Launch(1)/Launch(56) → mainKind=84），
     * 此封包客戶端未發現使用案例，保留 stub。
     */
    private fun handleUpgrade(session: GameSession) {
        logger.debug { "${session.characterName} 收到 C:030-003 錢莊升級（擴充流程走 mainKind=84 介消）" }
    }

    /**
     * C:030-008 關閉錢莊
     *
     * 來源：UIBank.lua:126-127
     */
    private fun handleClose(session: GameSession) {
        logger.trace { "${session.characterName} 關閉錢莊" }
    }
}
