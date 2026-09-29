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
 * Bộ xử lý kho báu (mainKind = 102)
 *
 * Nguồn: protocal.lua protocolTable[102]
 * C:102-001 nhận vật phẩm, C:102-002 gửi vật phẩm
 *
 * Kho báu chạy song song với Tiền Trang, có thêm các hạn chế:
 * - restrict bit 32 cấm gửi vào
 * - trang bị (fitType > 0) cấm gửi vào
 * - vật phẩm có deleteTime cấm gửi vào
 *
 * @param itemDefs bảng định nghĩa vật phẩm, dùng để kiểm tra các hạn chế
 */
class StorageHandler(
    private val inventorySystem: InventorySystem,
    private val responseSender: ResponseSender,
    private val itemDefs: Map<Int, ItemDef>,
    private val roleCountSystem: RoleCountSystem
) : PacketHandler {

    override val mainKind: Int = Opcodes.STORAGE  // 102

    companion object {
        /** Số thao tác tối đa mỗi gói tin (chống gói tin độc hại)*/
        private const val MAX_BATCH = 50
    }

    override suspend fun handle(session: GameSession, subKind: Int, data: ByteArray) {
        if (session.state != SessionState.IN_GAME) return

        when (subKind) {
            1 -> handleWithdraw(session, data)
            2 -> handleDeposit(session, data)
            else -> logger.warn { "Sub-protocol kho báu chưa xử lý: subKind=$subKind" }
        }
    }

    /**
     * C:102-001 Kho báu nhận vật phẩm
     *
     * Định dạng: <<+index(1) +quantity(4)>>
     * Nguồn: UIBank.lua:479
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
     * C:102-002 Kho báu gửi vật phẩm
     *
     * Định dạng: <<+index(2) +quantity(4)>>
     * Nguồn: UIBank.lua:480
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

            // Kiểm tra hạn chế của kho báu: restrict bit 32, trang bị, vật phẩm hết hạn
            val checkResult = inventorySystem.canDepositToStorage(bagItem)
            if (checkResult != BankResult.SUCCESS) {
                responseSender.sendStorageFail(session, checkResult)
                continue
            }

            val depositQty = minOf(quantity, bagItem.quantity)
            // Số ô động: (Storage_1+Storage_2)×5 (private server mặc định Storage_1=14 → 70 ô)
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
