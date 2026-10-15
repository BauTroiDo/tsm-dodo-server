package tw.tsm.app.handlers

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.PacketHandler
import tw.tsm.protocol.PacketWriter
import tw.tsm.gamelogic.systems.OnlineWelfareDaySystem

private val logger = KotlinLogging.logger {}

/**
 * Bộ xử lý heartbeat (mainKind = 10)
 *
 * client gửi heartbeat mỗi 20 giây, server trả lời bằng chính gói tin đó
 */
class KeepaliveHandler(private val onlineWelfare: OnlineWelfareDaySystem? = null) : PacketHandler {
    override val mainKind: Int = Opcodes.KEEPALIVE

    override suspend fun handle(session: GameSession, subKind: Int, data: ByteArray) {
        session.send(KEEPALIVE_RESPONSE)
        onlineWelfare?.onHeartbeat(session)
    }

    companion object {
        /** cache gói tin trả lời heartbeat bất biến, tránh tạo ByteArray mới mỗi lần*/
        private val KEEPALIVE_RESPONSE = PacketWriter(3)
            .writeByte(Opcodes.KEEPALIVE)
            .writeByte(0)
            .writeByte(0)
            .toByteArray()
    }
}
