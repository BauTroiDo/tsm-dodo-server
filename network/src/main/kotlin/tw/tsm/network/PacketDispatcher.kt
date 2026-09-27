package tw.tsm.network

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.PacketHandler

private val logger = KotlinLogging.logger {}

/**
 * Bộ điều phối (Dispatcher) gói tin cấp 1
 *
 * Tìm kiếm PacketHandler tương ứng dựa theo mainKind,
 * sau đó bên trong handler sẽ dùng `when(subKind)` để phân phối cấp 2.
 */
class PacketDispatcher {
    private val handlers = mutableMapOf<Int, PacketHandler>()

    /**
     * Đăng ký handler
     */
    fun register(handler: PacketHandler) {
        val existing = handlers.put(handler.mainKind, handler)
        if (existing != null) {
            logger.warn { "Ghi đè Handler: mainKind=${handler.mainKind} ${existing::class.simpleName} →${handler::class.simpleName}" }
        }
        logger.debug { "Đăng ký Handler: mainKind=${handler.mainKind} →${handler::class.simpleName}" }
    }

    /**
     * Đăng ký hàng loạt
     */
    fun registerAll(vararg handlerList: PacketHandler) {
        handlerList.forEach { register(it) }
    }

    /**
     * Phân phối gói tin đến handler tương ứng
     *
     * @param session Phiên kết nối
     * @param mainKind Opcode chính
     * @param subKind Opcode phụ
     * @param data Dữ liệu gói tin (Không bao gồm 3 bytes header)
     */
    suspend fun dispatch(session: GameSession, mainKind: Int, subKind: Int, data: ByteArray) {
        val handler = handlers[mainKind]
        if (handler != null) {
            handler.handle(session, subKind, data)
        } else {
            logger.warn { "Chưa đăng ký mainKind=$mainKind subKind=$subKind (${data.size} bytes)" }
        }
    }
}