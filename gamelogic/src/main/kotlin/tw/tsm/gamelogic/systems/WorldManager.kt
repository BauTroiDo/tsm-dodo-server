package tw.tsm.gamelogic.systems

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CompletableDeferred
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.ResponseSender
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet

private val logger = KotlinLogging.logger {}
private data class MapInstanceKey(val mapId: Int, val instanceId: Int)

class WorldManager {
    lateinit var responseSender: ResponseSender
    lateinit var npcPatrolSystem: NpcPatrolSystem

    private val mapPlayers = ConcurrentHashMap<MapInstanceKey, CopyOnWriteArraySet<GameSession>>()
    private val onlinePlayers = ConcurrentHashMap<Long, GameSession>()
    private val nameIndex = ConcurrentHashMap<String, GameSession>()
    private val pendingSceneLoads = ConcurrentHashMap<Long, CompletableDeferred<Unit>>()

    fun enterMap(session: GameSession, mapId: Int, x: Int, y: Int, instanceId: Int = 0) {
        require(instanceId in 0..0xFFFF)

        val oldMapId = session.mapId
        val oldInstanceId = session.instanceId
        val changedScope = oldMapId != mapId || oldInstanceId != instanceId

        if (oldMapId > 0 && changedScope) {
            val oldKey = MapInstanceKey(oldMapId, oldInstanceId)
            mapPlayers[oldKey]?.remove(session)
            if (mapPlayers[oldKey]?.isEmpty() == true) mapPlayers.remove(oldKey)
            if (oldMapId != mapId && !hasPlayersInAnyInstance(oldMapId) && ::npcPatrolSystem.isInitialized) {
                npcPatrolSystem.deactivateMap(oldMapId)
            }
        }

        session.mapId = mapId
        session.instanceId = instanceId
        session.x = x
        session.y = y

        val key = MapInstanceKey(mapId, instanceId)
        val existingPlayers = mapPlayers[key]?.toList() ?: emptyList()
        val mapWasEmpty = oldMapId != mapId && !hasPlayersInAnyInstance(mapId)

        mapPlayers.computeIfAbsent(key) { CopyOnWriteArraySet() }.add(session)
        if (mapWasEmpty && ::npcPatrolSystem.isInitialized) npcPatrolSystem.activateMap(mapId)

        existingPlayers.forEach { other ->
            if (other !== session && other.loginFinished) responseSender.sendOtherPlayerAppear(session, other)
        }
        existingPlayers.forEach { other ->
            if (other !== session && other.loginFinished) responseSender.sendOtherPlayerAppear(other, session)
        }

        logger.debug {
            session.characterName + " vào bản đồ " + mapId + " instance=" + instanceId +
                " (" + x + "," + y + "), cùng instance " + existingPlayers.size + " người"
        }
    }

    fun leaveMap(session: GameSession) {
        if (session.mapId > 0) {
            val mapId = session.mapId
            val key = MapInstanceKey(mapId, session.instanceId)
            mapPlayers[key]?.remove(session)
            if (mapPlayers[key]?.isEmpty() == true) mapPlayers.remove(key)
            logger.debug { session.characterName + " rời bản đồ " + mapId + " instance=" + session.instanceId }
            session.mapId = 0
            session.instanceId = 0
            if (!hasPlayersInAnyInstance(mapId) && ::npcPatrolSystem.isInitialized) {
                npcPatrolSystem.deactivateMap(mapId)
            }
        } else {
            session.instanceId = 0
        }
    }

    fun registerPlayer(session: GameSession) {
        onlinePlayers[session.playerId] = session
        if (session.characterName.isNotEmpty()) nameIndex[session.characterName] = session
        logger.info { "Người chơi trực tuyến: " + session.characterName + " (id=" + session.playerId + ")" }
    }

    fun removePlayer(session: GameSession) {
        leaveMap(session)
        val removed = onlinePlayers.remove(session.playerId, session)
        if (removed) {
            if (session.characterName.isNotEmpty()) nameIndex.remove(session.characterName, session)
            logger.info { "Người chơi off: " + session.characterName + " (id=" + session.playerId + ")" }
        } else if (session.playerId != 0L) {
            logger.warn { "Người chơi " + session.characterName + " đã bị session mới thay thế, bỏ qua xóa" }
        } else {
            logger.debug { "Bỏ qua session kiểm tra cổng chưa đăng nhập" }
        }
    }

    fun getPlayersInMap(mapId: Int, instanceId: Int = 0): Set<GameSession> =
        mapPlayers[MapInstanceKey(mapId, instanceId)] ?: emptySet()

    fun getPlayersInAllInstances(mapId: Int): Set<GameSession> =
        mapPlayers.entries.asSequence()
            .filter { (key, players) -> key.mapId == mapId && players.isNotEmpty() }
            .flatMap { it.value.asSequence() }
            .toSet()

    fun getPlayerById(playerId: Long): GameSession? = onlinePlayers[playerId]
    fun getPlayerByName(name: String): GameSession? = nameIndex[name]
    fun getOnlineSessions(): Collection<GameSession> = onlinePlayers.values

    fun broadcastToMap(
        mapId: Int,
        data: ByteArray,
        exclude: GameSession? = null,
        instanceId: Int? = null,
    ) {
        val scopedInstance = instanceId ?: exclude?.instanceId ?: 0
        mapPlayers[MapInstanceKey(mapId, scopedInstance)]?.forEach { target ->
            if (target !== exclude && target.loginFinished) target.send(data)
        }
    }

    fun broadcastToAllInstancesOfMap(mapId: Int, data: ByteArray, exclude: GameSession? = null) {
        mapPlayers.entries.forEach { (key, players) ->
            if (key.mapId != mapId) return@forEach
            players.forEach { target ->
                if (target !== exclude && target.loginFinished) target.send(data)
            }
        }
    }

    fun broadcastToAll(data: ByteArray, exclude: GameSession? = null) {
        onlinePlayers.values.forEach { target ->
            if (target !== exclude && target.loginFinished) target.send(data)
        }
    }

    val onlineCount: Int get() = onlinePlayers.size

    fun createSceneLoadDeferred(playerId: Long): CompletableDeferred<Unit> {
        val deferred = CompletableDeferred<Unit>()
        pendingSceneLoads[playerId] = deferred
        return deferred
    }

    fun notifySceneLoadComplete(playerId: Long) {
        pendingSceneLoads.remove(playerId)?.complete(Unit)
    }

    private fun hasPlayersInAnyInstance(mapId: Int): Boolean =
        mapPlayers.any { (key, players) -> key.mapId == mapId && players.isNotEmpty() }
}
