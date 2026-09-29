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
 * Bộ xử lý Tiền Trang (mainKind = 30)
 *
 * Nguồn: protocal.lua protocolTable[30]
 * C:030-001 nhận vật phẩm, C:030-002 gửi vật phẩm, C:030-003 nâng cấp, C:030-008 đóng Tiền Trang
 *
 * @param itemDefs bảng định nghĩa vật phẩm, dùng để kiểm tra hợp lệ restrict
 */
class BankHandler(
    private val inventorySystem: InventorySystem,
    private val responseSender: ResponseSender,
    private val itemDefs: Map<Int, ItemDef>,
    private val roleCountSystem: RoleCountSystem
) : PacketHandler {

    override val mainKind: Int = Opcodes.BANK  // 30

    companion object {
        /** Số thao tác tối đa mỗi gói tin (chống gói tin độc hại)*/
        private const val MAX_BATCH = 50
    }

    override suspend fun handle(session: GameSession, subKind: Int, data: ByteArray) {
        if (session.state != SessionState.IN_GAME) return

        when (subKind) {
            1 -> handleWithdraw(session, data)
            2 -> handleDeposit(session, data)
            3 -> handleUpgrade(session)
            8 -> handleClose(session)
            else -> logger.warn { "Sub-protocol Tiền trang chưa xử lý: subKind=$subKind" }
        }
    }

    /**
     * C:030-001 Tiền Trang nhận vật phẩm
     *
     * Định dạng: <<+index(2) +quantity(4)>>
     * Nguồn: UIBank.lua:477
     */
    private suspend fun handleWithdraw(session: GameSession, data: ByteArray) {
        if (data.isEmpty() || data.size % 6 != 0) return
        val reader = PacketReader(data)
        val characterId = session.roleId.toLong()
        var processed = 0

        while (reader.remaining >= 6 && processed < MAX_BATCH) {
            processed++
            val bankSlot = reader.readUShortLE()
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
     * C:030-002 Tiền Trang gửi vật phẩm
     *
     * Định dạng: <<+index(2) +quantity(4)>>
     * Nguồn: UIBank.lua:478
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

            // Server kiểm tra hợp lệ bit 32 của restrict (UIBank.lua:165)
            if (!inventorySystem.canDepositToBank(bagItem.itemId)) {
                logger.debug { "${session.characterName} thử gửi vật phẩm bị cấm itemId=${bagItem.itemId}" }
                continue
            }

            val depositQty = minOf(quantity, bagItem.quantity)
            // Số ô động: 25 + (Bank_1+Bank_2+Bank_3)×5 (介消 sellId=1 mở rộng)
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
     * C:030-003 nâng cấp Tiền Trang
     *
     * Mở rộng Tiền Trang/kho đã được hệ thống 介消 xử lý (UISell.Launch(1)/Launch(56) → mainKind=84),
     * Gói tin này chưa thấy client sử dụng, giữ lại stub.
     */
    private fun handleUpgrade(session: GameSession) {
        logger.debug { "${session.characterName} nhận C:030-003 nâng cấp Tiền trang (phần mở rộng đi theo mainKind=84)" }
    }

    /**
     * C:030-008 đóng Tiền Trang
     *
     * Nguồn: UIBank.lua:126-127
     */
    private fun handleClose(session: GameSession) {
        logger.trace { "${session.characterName} đóng Tiền trang" }
    }
}
