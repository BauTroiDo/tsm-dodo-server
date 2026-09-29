package tw.tsm.app.handlers

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.PacketHandler
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.domain.interfaces.repositories.HotkeyRepository
import tw.tsm.protocol.PacketReader

private val logger = KotlinLogging.logger {}

/**
 * Handler phím tắt (mainKind=40)
 *
 * C:040-002 Thiết lập phím tắt: dataIndex(1B) + pageIndex(1B) + keyIndex(1B) + useId(2B LE)
 */
class HotkeyHandler(
    private val hotkeyRepository: HotkeyRepository,
    private val responseSender: ResponseSender
) : PacketHandler {
    override val mainKind: Int = Opcodes.HOTKEY

    override suspend fun handle(session: GameSession, subKind: Int, data: ByteArray) {
        if (session.state != SessionState.IN_GAME) return
        when (subKind) {
            2 -> handleSetHotkey(session, data)
            else -> logger.warn { "Sub-protocol phím tắt chưa xử lý: subKind=$subKind" }
        }
    }

    /**
     * C:040-002 Thiết lập phím tắt
     *
     * Định dạng: dataIndex(1B) + pageIndex(1B) + keyIndex(1B) + useId(2B LE)
     * dataIndex: 1=PlayerMain(kỹ năng), 2=PlayerSub(vật phẩm)
     * pageIndex: chỉ số trang (thường=1)
     * keyIndex: vị trí phím 1-5
     * useId: ID kỹ năng hoặc vật phẩm (0=xoá)
     */
    private suspend fun handleSetHotkey(session: GameSession, data: ByteArray) {
        if (data.size < 5) return
        val reader = PacketReader(data)
        val dataIndex = reader.readUByte()
        val pageIndex = reader.readUByte()  // Thường=1, không lưu
        val keyIndex = reader.readUByte()
        val useId = reader.readUShortLE()

        // Kiểm tra hợp lệ khoảng
        if (dataIndex !in 1..2 || keyIndex !in 1..5) {
            logger.warn { "${session.characterName} tham số phím tắt không hợp lệ: dataIndex=$dataIndex keyIndex=$keyIndex" }
            return
        }

        logger.debug { "${session.characterName} đặt phím tắt: dataIndex=$dataIndex page=$pageIndex key=$keyIndex useId=$useId" }

        // Lưu vào DB
        hotkeyRepository.save(session.roleId.toLong(), dataIndex, keyIndex, useId)

        // Phản hồi S:040-002 result=0 (thành công) + tham số đầy đủ để client cập nhật UI tức thì
        responseSender.sendHotkeySetResult(session, 0, dataIndex, pageIndex, keyIndex, useId)
    }
}
