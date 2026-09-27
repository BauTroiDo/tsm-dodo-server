package tw.tsm.network

import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.timeout.IdleStateHandler
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import tw.tsm.protocol.FrameDecoder
import tw.tsm.protocol.FrameEncoder
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger {}

/**
 * Máy chủ game TCP Netty
 */
class NettyGameServer(
    private val sessionManager: SessionManager,
    private val dispatcher: PacketDispatcher,
    private val scope: CoroutineScope,
    private val onDisconnect: ((GameSessionImpl) -> Unit)? = null
) {
    private val bossGroup = NioEventLoopGroup(1)
    private val workerGroup = NioEventLoopGroup()

    /**
     * Khởi động máy chủ
     *
     * @param host Địa chỉ bind (ràng buộc)
     * @param port Cổng (port) lắng nghe
     */
    fun start(host: String, port: Int) {
        val bootstrap = ServerBootstrap()
            .group(bossGroup, workerGroup)
            .channel(NioServerSocketChannel::class.java)
            .option(ChannelOption.SO_BACKLOG, 128)
            .childOption(ChannelOption.TCP_NODELAY, true)
            .childOption(ChannelOption.SO_KEEPALIVE, true)
            .childHandler(object : ChannelInitializer<SocketChannel>() {
                override fun initChannel(ch: SocketChannel) {
                    ch.pipeline().apply {
                        addLast("frameDecoder", FrameDecoder())
                        addLast("frameEncoder", FrameEncoder())
                        // 90 giây không có hoạt động đọc sẽ bị coi là mất kết nối (khoảng thời gian heartbeat 20 giây × 4 lần dung sai + thời gian bù trừ)
                        addLast("idleState", IdleStateHandler(90, 0, 0, TimeUnit.SECONDS))
                        addLast("dispatcher", PacketDispatchHandler(sessionManager, dispatcher, scope, onDisconnect))
                    }
                }
            })

        val future = bootstrap.bind(host, port).sync()
        logger.info { "Máy chủ khởi động: $host:$port" }
        future.channel().closeFuture().addListener {
            logger.info { "Máy chủ đã đóng" }
        }
    }

    /**
     * Tắt máy chủ
     *
     * Sử dụng .sync() để chờ EventLoopGroup thực sự đóng, đảm bảo rằng tác vụ writeAndFlush
     * gửi gói tin S:000-000 của bên gọi (Application.shutdown) có thể thực thi xong trước khi EventLoop dừng.
     */
    fun shutdown() {
        logger.info { "Đang tắt máy chủ..." }
        bossGroup.shutdownGracefully().sync()
        workerGroup.shutdownGracefully().sync()
    }
}