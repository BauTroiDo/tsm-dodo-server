package tw.tsm.gamelogic.systems

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CompletableDeferred
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.ResponseSender
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet

private val logger = KotlinLogging.logger {}

/**
 * 世界管理器 — 追蹤所有地圖上的玩家
 *
 * 負責：
 * - 玩家進入/離開地圖的追蹤
 * - 同地圖玩家廣播
 * - 取得指定地圖的所有玩家
 */
class WorldManager {
    /** 延遲注入（打破與 ResponseSender 的循環依賴） */
    lateinit var responseSender: ResponseSender

    /** 延遲注入 NPC 巡邏系統 */
    lateinit var npcPatrolSystem: NpcPatrolSystem

    /** mapId → 該地圖上的所有 session */
    private val mapPlayers = ConcurrentHashMap<Int, CopyOnWriteArraySet<GameSession>>()

    /** 所有在線玩家（playerId → session） */
    private val onlinePlayers = ConcurrentHashMap<Long, GameSession>()

    /** 角色名稱反向索引（name → session），避免 O(n) 查詢 */
    private val nameIndex = ConcurrentHashMap<String, GameSession>()

    /** 等待場景載入完畢的信號（playerId → deferred） */
    private val pendingSceneLoads = ConcurrentHashMap<Long, CompletableDeferred<Unit>>()

    /**
     * 玩家進入地圖
     *
     * 會自動從舊地圖移除。
     */
    fun enterMap(session: GameSession, mapId: Int, x: Int, y: Int) {
        // 先從舊地圖移除
        if (session.mapId > 0 && session.mapId != mapId) {
            val oldMapId = session.mapId
            mapPlayers[oldMapId]?.remove(session)
            // 舊地圖最後一人離開時停用 NPC 巡邏
            if (mapPlayers[oldMapId]?.isEmpty() == true && ::npcPatrolSystem.isInitialized) {
                npcPatrolSystem.deactivateMap(oldMapId)
            }
        }

        // 更新 session 座標
        session.mapId = mapId
        session.x = x
        session.y = y

        // 取得地圖上已存在的玩家（加入前先快照）
        val existingPlayers = mapPlayers[mapId]?.toList() ?: emptyList()

        // 加入新地圖（第一個玩家時啟動 NPC 巡邏）
        val wasEmpty = existingPlayers.isEmpty()
        mapPlayers.computeIfAbsent(mapId) { CopyOnWriteArraySet() }.add(session)
        if (wasEmpty && ::npcPatrolSystem.isInitialized) {
            npcPatrolSystem.activateMap(mapId)
        }

        // 向新玩家發送已在場的每個玩家
        existingPlayers.forEach { other ->
            if (other !== session && other.loginFinished) {
                responseSender.sendOtherPlayerAppear(session, other)
            }
        }

        // 向已在場玩家廣播新玩家出現
        existingPlayers.forEach { other ->
            if (other !== session && other.loginFinished) {
                responseSender.sendOtherPlayerAppear(other, session)
            }
        }

        logger.debug { "${session.characterName} 進入地圖 $mapId ($x, $y)，場上 ${existingPlayers.size} 人" }
    }

    /**
     * 玩家離開地圖（斷線/登出）
     */
    fun leaveMap(session: GameSession) {
        if (session.mapId > 0) {
            val mapId = session.mapId
            mapPlayers[mapId]?.remove(session)
            logger.debug { "${session.characterName} 離開地圖 $mapId" }
            session.mapId = 0
            // 最後一個玩家離開時停用 NPC 巡邏
            if (mapPlayers[mapId]?.isEmpty() == true && ::npcPatrolSystem.isInitialized) {
                npcPatrolSystem.deactivateMap(mapId)
            }
        }
    }

    /**
     * 註冊在線玩家
     */
    fun registerPlayer(session: GameSession) {
        onlinePlayers[session.playerId] = session
        if (session.characterName.isNotEmpty()) {
            nameIndex[session.characterName] = session
        }
        logger.info { "玩家上線：${session.characterName} (id=${session.playerId})" }
    }

    /**
     * 移除在線玩家
     *
     * 使用 remove(key, value) 確保只移除自己的 session，
     * 避免舊 session 清理時誤刪已被新登入覆蓋的 entry。
     */
    fun removePlayer(session: GameSession) {
        leaveMap(session)
        val removed = onlinePlayers.remove(session.playerId, session)
        if (removed) {
            if (session.characterName.isNotEmpty()) {
                nameIndex.remove(session.characterName, session)
            }
            logger.info { "玩家下線：${session.characterName} (id=${session.playerId})" }
        } else if (session.playerId != 0L) {
            logger.warn { "玩家 ${session.characterName} (id=${session.playerId}) 已被新 session 取代，跳過移除" }
        } else {
            logger.debug { "Bỏ qua session kiểm tra cổng chưa đăng nhập" }
        }
    }

    /**
     * 取得指定地圖上的所有玩家
     */
    fun getPlayersInMap(mapId: Int): Set<GameSession> =
        mapPlayers[mapId] ?: emptySet()

    /**
     * 根據 playerId 取得在線玩家
     */
    fun getPlayerById(playerId: Long): GameSession? =
        onlinePlayers[playerId]

    /**
     * 根據角色名稱取得在線玩家
     */
    fun getPlayerByName(name: String): GameSession? =
        nameIndex[name]

    /**
     * 取得所有在線玩家 session（用於定期自動存檔）
     */
    fun getOnlineSessions(): Collection<GameSession> =
        onlinePlayers.values

    /**
     * 廣播封包給同地圖所有玩家
     *
     * @param mapId 目標地圖 ID
     * @param data 封包資料
     * @param exclude 排除的 session（通常是發送者自己）
     */
    fun broadcastToMap(mapId: Int, data: ByteArray, exclude: GameSession? = null) {
        mapPlayers[mapId]?.forEach { session ->
            if (session !== exclude && session.loginFinished) {
                session.send(data)
            }
        }
    }

    /**
     * 廣播封包給所有在線玩家
     */
    fun broadcastToAll(data: ByteArray, exclude: GameSession? = null) {
        onlinePlayers.values.forEach { session ->
            if (session !== exclude && session.loginFinished) {
                session.send(data)
            }
        }
    }

    /** 在線玩家數量 */
    val onlineCount: Int get() = onlinePlayers.size

    // ── 場景載入等待 ──

    /**
     * 建立場景載入等待信號
     *
     * EveDoorWarpService 呼叫，在發送場景切換封包後掛起等待
     * 客戶端 C:012-001（場景載入完畢）到達時由 [notifySceneLoadComplete] 觸發。
     */
    fun createSceneLoadDeferred(playerId: Long): CompletableDeferred<Unit> {
        val deferred = CompletableDeferred<Unit>()
        pendingSceneLoads[playerId] = deferred
        return deferred
    }

    /**
     * 通知場景載入完畢
     *
     * RelocateHandler 收到 C:012-001 時呼叫。
     */
    fun notifySceneLoadComplete(playerId: Long) {
        pendingSceneLoads.remove(playerId)?.complete(Unit)
    }
}
