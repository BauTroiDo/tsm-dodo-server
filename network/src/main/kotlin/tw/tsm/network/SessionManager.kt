package tw.tsm.network

import io.netty.channel.Channel
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

/**
 * Quản lý kết nối — Quản lý tất cả GameSession
 */
class SessionManager {
    private val sessions = ConcurrentHashMap<Channel, GameSessionImpl>()

    /** Số lượng kết nối hiện tại */
    val count: Int get() = sessions.size

    /**
     * Tạo phiên kết nối mới
     */
    fun createSession(channel: Channel): GameSessionImpl {
        val session = GameSessionImpl(channel)
        sessions[channel] = session
        logger.info { "Thiết lập kết nối: ${session.remoteAddress}, hiện có $count người" }
        return session
    }

    /**
     * Lấy phiên kết nối
     */
    fun getSession(channel: Channel): GameSessionImpl? = sessions[channel]

    /**
     * Gỡ bỏ phiên kết nối
     */
    fun removeSession(channel: Channel): GameSessionImpl? {
        val session = sessions.remove(channel)
        if (session != null) {
            logger.info { "Ngắt kết nối: ${session.remoteAddress} (${session.account}), hiện còn $count người" }
        }
        return session
    }

    /**
     * Lấy tất cả kết nối
     */
    fun allSessions(): Collection<GameSessionImpl> = sessions.values
}