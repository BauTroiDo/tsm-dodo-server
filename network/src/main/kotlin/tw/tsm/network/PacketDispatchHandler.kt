package tw.tsm.network

import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.handler.timeout.IdleState
import io.netty.handler.timeout.IdleStateEvent
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private val logger = KotlinLogging.logger {}

/**
 * Handler Inbound Netty — Phân tích header sau đó chuyển cho PacketDispatcher
 *
 * FrameDecoder đã cắt frame sẵn, dữ liệu nhận được ở đây là một gói tin (packet) hoàn chỉnh:
 * [MainKind 1B] [SubKind 1B] [Compress 1B] [Data...]
 */
class PacketDispatchHandler(
    private val sessionManager: SessionManager,
    private val dispatcher: PacketDispatcher,
    private val scope: CoroutineScope,
    private val onDisconnect: ((GameSessionImpl) -> Unit)? = null
) : SimpleChannelInboundHandler<ByteArray>() {

    override fun channelActive(ctx: ChannelHandlerContext) {
        sessionManager.createSession(ctx.channel())
    }

    override fun channelInactive(ctx: ChannelHandlerContext) {
        val session = sessionManager.removeSession(ctx.channel())
        if (session != null) {
            onDisconnect?.invoke(session)
        }
    }

    override fun channelRead0(ctx: ChannelHandlerContext, msg: ByteArray) {
        if (msg.size < HEADER_SIZE) {
            logger.warn { "Gói tin quá ngắn: ${msg.size} bytes" }
            return
        }

        val session = sessionManager.getSession(ctx.channel()) ?: return

        val mainKind = msg[0].toInt() and 0xFF
        val subKind = msg[1].toInt() and 0xFF
        // msg[2] = compress, hiện tại bỏ qua

        val data = if (msg.size > HEADER_SIZE) {
            msg.copyOfRange(HEADER_SIZE, msg.size)
        } else {
            ByteArray(0)
        }

        logger.debug { "Nhận gói tin: ${msg.size}B mainKind=$mainKind subKind=$subKind data=${data.size}B" }

        scope.launch {
            try {
                dispatcher.dispatch(session, mainKind, subKind, data)
            } catch (e: Exception) {
                logger.error(e) { "Xử lý gói tin thất bại: mainKind=$mainKind subKind=$subKind" }
            }
        }
    }

    /**
     * Xử lý IdleStateEvent — Đóng kết nối khi Client không có hoạt động đọc quá 90 giây
     */
    override fun userEventTriggered(ctx: ChannelHandlerContext, evt: Any) {
        if (evt is IdleStateEvent && evt.state() == IdleState.READER_IDLE) {
            logger.warn { "Đọc kết nối quá giờ (90s), đóng kết nối: ${ctx.channel().remoteAddress()}" }
            ctx.close()
        } else {
            super.userEventTriggered(ctx, evt)
        }
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        logger.error(cause) { "Lỗi kết nối bất thường: ${ctx.channel().remoteAddress()}" }
        ctx.close()
    }

    companion object {
        /** Kích thước Header: mainKind(1) + subKind(1) + compress(1) */
        private const val HEADER_SIZE = 3
    }
}