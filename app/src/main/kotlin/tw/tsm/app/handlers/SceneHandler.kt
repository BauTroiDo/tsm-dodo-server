package tw.tsm.app.handlers

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.PacketHandler
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.gamelogic.systems.WorldManager
import tw.tsm.protocol.PacketReader

private val logger = KotlinLogging.logger {}

/**
 * Bộ xử lý cảnh/phân khu (mainKind = 7)
 *
 * Nguồn: protocal.lua protocolTable[7]
 * C→S 0x07-0x01: yêu cầu danh sách phân khu — gói tin trống
 * C→S 0x07-0x02: chuyển phân khu — areaIndex(2B LE)
 * S→C 0x07-0x00: người chơi dịch chuyển tức thì — roleId(8) + sceneId(2) + x(2) + y(2)
 * S→C 0x07-0x01: danh sách phân khu — count(1) + [index(2) + current(2) + max(2)]...
 * S→C 0x07-0x02: kết quả đổi phân khu — resultCode(1)
 */
class SceneHandler(
    private val worldManager: WorldManager,
    private val responseSender: ResponseSender
) : PacketHandler {

    override val mainKind: Int = Opcodes.SCENE

    override suspend fun handle(session: GameSession, subKind: Int, data: ByteArray) {
        if (session.state != SessionState.IN_GAME) return

        when (subKind) {
            1 -> handleRequestAreaList(session)
            2 -> handleSwitchArea(session, data)
            else -> logger.warn { "Sub-protocol cảnh chưa xử lý: subKind=$subKind" }
        }
    }

    /**
     * C:007-001 Yêu cầu danh sách phân khu
     *
     * Phản hồi S:007-001 danh sách phân khu
     * Kiến trúc một server, chỉ trả về một phân khu
     */
    private fun handleRequestAreaList(session: GameSession) {
        responseSender.sendAreaList(session, worldManager.onlineCount)
    }

    /**
     * C:007-002 Chuyển phân khu
     *
     * Định dạng: +areaIndex(2 LE)
     * Kiến trúc một server, luôn trả về thông báo không thể chuyển khi cùng phân khu
     */
    private fun handleSwitchArea(session: GameSession, data: ByteArray) {
        val reader = PacketReader(data)
        val areaIndex = reader.readUShortLE()
        logger.info { "${session.characterName} thử chuyển khu vực $areaIndex" }

        // Một server: trả về thông báo không thể chuyển sang cùng phân khu
        responseSender.sendAreaSwitchResult(session, 1)
    }
}
