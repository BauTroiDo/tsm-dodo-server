package tw.tsm.app

import com.typesafe.config.ConfigFactory
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import org.koin.core.context.startKoin
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import tw.tsm.data.GameDataManager
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.gamelogic.systems.AutoSaveService
import tw.tsm.gamelogic.systems.NpcPatrolSystem
import tw.tsm.network.NettyGameServer
import tw.tsm.network.SessionManager
import tw.tsm.persistence.DatabaseFactory
import java.nio.file.Paths

private val logger = KotlinLogging.logger {}

/**
 * Điểm khởi chạy của máy chủ
 */
fun main() {
    logger.info { "=== tsm_dodo Thôn Phệ Thiên Địa M Máy chủ ===" }

    val config = ConfigFactory.load()

    // Khởi tạo cơ sở dữ liệu
    DatabaseFactory.init(
        url = config.getString("database.url"),
        user = config.getString("database.user"),
        password = config.getString("database.password"),
        maxPoolSize = config.getInt("database.maximumPoolSize")
    )

    // Tải dữ liệu tĩnh của game
    val gameDataManager = GameDataManager(
        basePath = Paths.get(config.getString("game.gamedataPath")),
        gamedataPath = Paths.get(config.getString("game.serverDataPath"))
    )
    gameDataManager.initialize()

    val host = config.getString("server.host")
    val port = config.getInt("server.port")

    // Khởi động Koin DI
    startKoin {
        modules(serverModule(config, gameDataManager))
    }

    // Khởi động máy chủ
    val app = Application()
    app.start(host, port)

    // Đóng máy chủ an toàn (Graceful shutdown)
    Runtime.getRuntime().addShutdownHook(Thread {
        logger.info { "Đã nhận được tín hiệu đóng..." }
        app.shutdown()
    })
}

/**
 * Lớp chính của ứng dụng
 */
class Application : KoinComponent {
    private val server: NettyGameServer by inject()
    private val sessionManager: SessionManager by inject()
    private val responseSender: ResponseSender by inject()
    private val scope: CoroutineScope by inject()
    @Suppress("unused") // Kích hoạt khởi tạo lười (lazy) của Koin, khởi động vòng lặp (tick) tuần tra của NPC
    private val npcPatrolSystem: NpcPatrolSystem by inject()
    @Suppress("unused") // Kích hoạt khởi tạo lười (lazy) của Koin, khởi động tự động lưu định kỳ
    private val autoSaveService: AutoSaveService by inject()

    /** Khởi động máy chủ */
    fun start(host: String, port: Int) {
        // Kích hoạt hệ thống tuần tra NPC + Khởi tạo tự động lưu (Koin lazy singleton)
        npcPatrolSystem
        autoSaveService
        server.start(host, port)
        logger.info { "Máy chủ đã sẵn sàng, đang chờ kết nối..." }
    }

    /**
     * Tắt máy chủ
     *
     * Gửi thông báo S:000-000 cause=60 (Bảo trì đóng máy chủ) tới tất cả client đang trực tuyến trước,
     * để client hiển thị bình thường thông báo đóng máy rồi quay về màn hình đăng nhập, sau đó mới đóng Netty.
     */
    fun shutdown() {
        val sessions = sessionManager.allSessions().toList()  // Bản chụp (snapshot), tránh lỗi ConcurrentModification
        val count = sessions.size
        logger.info { "Đang thông báo cho $count client rằng máy chủ sắp tắt..." }

        // ① Gửi thông báo ngắt kết nối
        for (session in sessions) {
            responseSender.sendDisconnect(session, 60)  // 60 = Bảo trì đóng máy chủ
        }

        // ② Đóng tất cả kết nối (kích hoạt logic lưu trữ channelInactive → onDisconnect)
        for (session in sessions) {
            session.close()
        }

        // ③ Chờ lưu dữ liệu hoàn tất (onDisconnect sử dụng scope.launch để lưu bất đồng bộ)
        Thread.sleep(3000)
        logger.info { "Đã chờ lưu xong, tiến hành đóng máy chủ" }

        // ④ Dọn dẹp
        scope.cancel("Máy chủ đóng")
        server.shutdown()
    }
}