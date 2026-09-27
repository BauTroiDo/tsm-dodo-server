package tw.tsm.app.handlers

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.PacketHandler
import tw.tsm.protocol.PacketWriter

private val logger = KotlinLogging.logger {}

/**
 * 心跳處理器（mainKind = 10）
 *
 * 客戶端每 20 秒發送心跳，伺服器回應同樣的封包
 */
class KeepaliveHandler : PacketHandler {
    override val mainKind: Int = Opcodes.KEEPALIVE

    override suspend fun handle(session: GameSession, subKind: Int, data: ByteArray) {
        session.send(KEEPALIVE_RESPONSE)
    }

    companion object {
        /** 快取不變的心跳回應封包，避免每次建立新 ByteArray */
        private val KEEPALIVE_RESPONSE = PacketWriter(3)
            .writeByte(Opcodes.KEEPALIVE)
            .writeByte(0)
            .writeByte(0)
            .toByteArray()
    }
}
