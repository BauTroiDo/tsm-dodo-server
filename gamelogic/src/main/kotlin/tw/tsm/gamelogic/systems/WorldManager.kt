package tw.tsm.gamelogic.systems

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CompletableDeferred
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.ResponseSender
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet

private val logger = KotlinLogging.logger {}

/**
 * Bộ quản lý thế giới — theo dõi người chơi trên tất cả bản đồ
 *
 * Phụ trách:
 * - Theo dõi người chơi vào/rời bản đồ
 * - Phát rộng cho người chơi cùng bản đồ
 * - Lấy tất cả người chơi của bản đồ chỉ định
 */
class WorldManager {
    /** Inject chậm (phá vỡ phụ thuộc vòng với ResponseSender)*/
    lateinit var responseSender: ResponseSender

    /** Inject chậm hệ thống tuần tra NPC*/
    lateinit var npcPatrolSystem: NpcPatrolSystem

    /** mapId → tất cả session trên bản đồ đó*/
    private val mapPlayers = ConcurrentHashMap<Int, CopyOnWriteArraySet<GameSession>>()

    /** Tất cả người chơi trực tuyến (playerId → session)*/
    private val onlinePlayers = ConcurrentHashMap<Long, GameSession>()

    /** Chỉ mục ngược theo tên nhân vật (name → session), tránh truy vấn O(n)*/
    private val nameIndex = ConcurrentHashMap<String, GameSession>()

    /** Tín hiệu chờ tải cảnh xong (playerId → deferred)*/
    private val pendingSceneLoads = ConcurrentHashMap<Long, CompletableDeferred<Unit>>()

    /**
     * Người chơi vào bản đồ
     *
     * Sẽ tự động bị loại khỏi bản đồ cũ.
     */
    fun enterMap(session: GameSession, mapId: Int, x: Int, y: Int) {
        // Trước tiên loại khỏi bản đồ cũ
        if (session.mapId > 0 && session.mapId != mapId) {
            val oldMapId = session.mapId
            mapPlayers[oldMapId]?.remove(session)
            // Tắt tuần tra NPC khi người cuối cùng rời bản đồ cũ
            if (mapPlayers[oldMapId]?.isEmpty() == true && ::npcPatrolSystem.isInitialized) {
                npcPatrolSystem.deactivateMap(oldMapId)
            }
        }

        // Cập nhật toạ độ session
        session.mapId = mapId
        session.x = x
        session.y = y

        // Lấy người chơi đã có trên bản đồ (chụp snapshot trước khi thêm)
        val existingPlayers = mapPlayers[mapId]?.toList() ?: emptyList()

        // Thêm vào bản đồ mới (bật tuần tra NPC khi có người chơi đầu tiên)
        val wasEmpty = existingPlayers.isEmpty()
        mapPlayers.computeIfAbsent(mapId) { CopyOnWriteArraySet() }.add(session)
        if (wasEmpty && ::npcPatrolSystem.isInitialized) {
            npcPatrolSystem.activateMap(mapId)
        }

        // Gửi từng người chơi đang có mặt cho người chơi mới
        existingPlayers.forEach { other ->
            if (other !== session && other.loginFinished) {
                responseSender.sendOtherPlayerAppear(session, other)
            }
        }

        // Phát rộng sự xuất hiện của người chơi mới cho người chơi đang có mặt
        existingPlayers.forEach { other ->
            if (other !== session && other.loginFinished) {
                responseSender.sendOtherPlayerAppear(other, session)
            }
        }

        logger.debug { "${session.characterName} vào bản đồ $mapId ($x, $y), trên sàn ${existingPlayers.size} người" }
    }

    /**
     * Người chơi rời bản đồ (mất kết nối/đăng xuất)
     */
    fun leaveMap(session: GameSession) {
        if (session.mapId > 0) {
            val mapId = session.mapId
            mapPlayers[mapId]?.remove(session)
            logger.debug { "${session.characterName} rời bản đồ $mapId" }
            session.mapId = 0
            // Tắt tuần tra NPC khi người chơi cuối cùng rời đi
            if (mapPlayers[mapId]?.isEmpty() == true && ::npcPatrolSystem.isInitialized) {
                npcPatrolSystem.deactivateMap(mapId)
            }
        }
    }

    /**
     * Đăng ký người chơi trực tuyến
     */
    fun registerPlayer(session: GameSession) {
        onlinePlayers[session.playerId] = session
        if (session.characterName.isNotEmpty()) {
            nameIndex[session.characterName] = session
        }
        logger.info { "Người chơi trực tuyến: ${session.characterName} (id=${session.playerId})" }
    }

    /**
     * Loại bỏ người chơi trực tuyến
     *
     * Dùng remove(key, value) để đảm bảo chỉ loại bỏ session của chính mình,
     * Tránh khi dọn dẹp session cũ xoá nhầm entry đã bị đăng nhập mới ghi đè.
     */
    fun removePlayer(session: GameSession) {
        leaveMap(session)
        val removed = onlinePlayers.remove(session.playerId, session)
        if (removed) {
            if (session.characterName.isNotEmpty()) {
                nameIndex.remove(session.characterName, session)
            }
            logger.info { "Người chơi off: ${session.characterName} (id=${session.playerId})" }
        } else if (session.playerId != 0L) {
            logger.warn { "Người chơi ${session.characterName} (id=${session.playerId}) đã bị session mới thay thế, bỏ qua xóa" }
        } else {
            logger.debug { "Bỏ qua session kiểm tra cổng chưa đăng nhập" }
        }
    }

    /**
     * Lấy tất cả người chơi trên bản đồ chỉ định
     */
    fun getPlayersInMap(mapId: Int): Set<GameSession> =
        mapPlayers[mapId] ?: emptySet()

    /**
     * Lấy người chơi trực tuyến theo playerId
     */
    fun getPlayerById(playerId: Long): GameSession? =
        onlinePlayers[playerId]

    /**
     * Lấy người chơi trực tuyến theo tên nhân vật
     */
    fun getPlayerByName(name: String): GameSession? =
        nameIndex[name]

    /**
     * Lấy tất cả session người chơi trực tuyến (dùng cho lưu tự động định kỳ)
     */
    fun getOnlineSessions(): Collection<GameSession> =
        onlinePlayers.values

    /**
     * Phát rộng gói tin cho tất cả người chơi cùng bản đồ
     *
     * @param mapId ID bản đồ đích
     * @param data dữ liệu gói tin
     * @param exclude session bị loại trừ (thường là chính người gửi)
     */
    fun broadcastToMap(mapId: Int, data: ByteArray, exclude: GameSession? = null) {
        mapPlayers[mapId]?.forEach { session ->
            if (session !== exclude && session.loginFinished) {
                session.send(data)
            }
        }
    }

    /**
     * Phát rộng gói tin cho tất cả người chơi trực tuyến
     */
    fun broadcastToAll(data: ByteArray, exclude: GameSession? = null) {
        onlinePlayers.values.forEach { session ->
            if (session !== exclude && session.loginFinished) {
                session.send(data)
            }
        }
    }

    /** Số người chơi trực tuyến*/
    val onlineCount: Int get() = onlinePlayers.size

    // ── Chờ tải cảnh ──

    /**
     * Tạo tín hiệu chờ tải cảnh
     *
     * EveDoorWarpService gọi, treo chờ sau khi gửi gói tin chuyển cảnh
     * Được [notifySceneLoadComplete] kích hoạt khi gói client C:012-001 (tải cảnh xong) tới nơi.
     */
    fun createSceneLoadDeferred(playerId: Long): CompletableDeferred<Unit> {
        val deferred = CompletableDeferred<Unit>()
        pendingSceneLoads[playerId] = deferred
        return deferred
    }

    /**
     * Thông báo tải cảnh xong
     *
     * RelocateHandler gọi khi nhận C:012-001.
     */
    fun notifySceneLoadComplete(playerId: Long) {
        pendingSceneLoads.remove(playerId)?.complete(Unit)
    }
}
