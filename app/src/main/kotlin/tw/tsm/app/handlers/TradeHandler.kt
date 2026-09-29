package tw.tsm.app.handlers

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.PacketHandler
import tw.tsm.gamelogic.systems.TradeSystem
import tw.tsm.protocol.PacketReader

private val logger = KotlinLogging.logger {}

/**
 * Bộ xử lý giao dịch người chơi (mainKind = 25)
 *
 * Nguồn: protocal.lua protocolTable[25]
 *
 * Giao dịch vật phẩm: C:025-001 khởi tạo, C:025-002 tải vật phẩm lên, C:025-003 xác nhận/hủy
 * Giao dịch tướng: C:025-010 khởi tạo, C:025-011 tải tướng lên, C:025-012 xác nhận/hủy
 * Hệ thống chuyển phát nhanh: C:025-020~021 (TODO: subsystem độc lập)
 */
class TradeHandler(
    private val tradeSystem: TradeSystem
) : PacketHandler {

    override val mainKind: Int = Opcodes.TRADE  // 25

    override suspend fun handle(session: GameSession, subKind: Int, data: ByteArray) {
        if (session.state != SessionState.IN_GAME) return

        when (subKind) {
            1 -> handleRequestTrade(session, data)
            2 -> handleUploadItems(session, data)
            3 -> handleConfirmTrade(session, data)
            10 -> handleRequestPetTrade(session, data)
            11 -> handleUploadPet(session, data)
            12 -> handleConfirmPetTrade(session, data)
            20 -> { /* TODO: hệ thống chuyển phát nhanh*/ }
            21 -> { /* TODO: hủy chuyển phát nhanh*/ }
            else -> logger.warn { "Sub-protocol giao dịch chưa xử lý: subKind=$subKind" }
        }
    }

    /**
     * C:025-001 Bắt đầu giao dịch vật phẩm
     *
     * Định dạng: +playerID(8)
     */
    private fun handleRequestTrade(session: GameSession, data: ByteArray) {
        val reader = PacketReader(data)
        val targetId = reader.readULongLE()

        logger.info { "${session.characterName} mở giao dịch → targetId=$targetId" }
        tradeSystem.requestTrade(session, targetId)
    }

    /**
     * C:025-002 Tải lên vật phẩm giao dịch
     *
     * Định dạng: +money(4) <<+bagIndex(1)>>
     */
    private fun handleUploadItems(session: GameSession, data: ByteArray) {
        val reader = PacketReader(data)
        val money = reader.readIntLE()
        if (money < 0) return
        val bagIndices = mutableListOf<Int>()

        while (reader.isReadable) {
            bagIndices.add(reader.readUByte())
        }

        logger.info { "${session.characterName} gửi giao dịch: tiền=$money vật phẩm=${bagIndices.size} món" }
        tradeSystem.uploadItems(session.playerId, money, bagIndices)
    }

    /**
     * C:025-003 Xác nhận/huỷ giao dịch vật phẩm
     *
     * Định dạng: +result(1) [1=xác nhận, 2=hủy]
     */
    private fun handleConfirmTrade(session: GameSession, data: ByteArray) {
        val reader = PacketReader(data)
        val result = reader.readUByte()

        logger.info { "${session.characterName} thao tác giao dịch: result=$result (${if (result == 1) "確認" else "取消"})" }
        tradeSystem.confirmOrCancel(session.playerId, result, TradeSystem.MODE_ITEM)
    }

    /**
     * C:025-010 Bắt đầu giao dịch tướng
     *
     * Định dạng: +playerID(8)
     */
    private fun handleRequestPetTrade(session: GameSession, data: ByteArray) {
        val reader = PacketReader(data)
        val targetId = reader.readULongLE()

        logger.info { "${session.characterName} mở giao dịch tướng → targetId=$targetId" }
        tradeSystem.requestTrade(session, targetId, TradeSystem.MODE_PET)
    }

    /**
     * C:025-011 Tải lên tướng giao dịch
     *
     * Định dạng: +money(4) +followIndex(1) (0 = chỉ đưa tiền vàng, UITrade.lua:375-379)
     */
    private fun handleUploadPet(session: GameSession, data: ByteArray) {
        val reader = PacketReader(data)
        val money = reader.readIntLE()
        if (money < 0) return
        val petSlot = reader.readUByte()

        logger.info { "${session.characterName} gửi giao dịch tướng: tiền=$money petSlot=$petSlot" }
        tradeSystem.uploadPet(session.playerId, money, petSlot)
    }

    /**
     * C:025-012 Xác nhận/huỷ giao dịch tướng
     *
     * Định dạng: +result(1) [1=xác nhận, 2=hủy]
     */
    private fun handleConfirmPetTrade(session: GameSession, data: ByteArray) {
        val reader = PacketReader(data)
        val result = reader.readUByte()

        logger.info { "${session.characterName} thao tác giao dịch tướng: result=$result (${if (result == 1) "確認" else "取消"})" }
        tradeSystem.confirmOrCancel(session.playerId, result, TradeSystem.MODE_PET)
    }
}
