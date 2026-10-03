package tw.tsm.gamelogic.systems

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import tw.tsm.domain.interfaces.GameSession

data class DungeonRoomMember(
    val session: GameSession,
    var prepared: Boolean = false,
)

data class DungeonRoomState(
    val id: Long,
    val dungeonId: Int,
    val instanceId: Int,
    var name: String,
    var ownerId: Long,
    var password: String,
    val maxPlayers: Int,
    val members: LinkedHashMap<Long, DungeonRoomMember> = linkedMapOf(),
    var starting: Boolean = false,
    var started: Boolean = false,
) {
    val isLocked: Boolean get() = password.isNotEmpty()
}

data class DungeonRoomLeaveResult(
    val room: DungeonRoomState,
    val removed: DungeonRoomMember,
    val newOwnerId: Long?,
    val disbanded: Boolean,
)

class DungeonRoomRegistry {
    private val nextRoomId = AtomicLong(1L)
    private val nextInstanceId = AtomicInteger(1)
    private val rooms = LinkedHashMap<Long, DungeonRoomState>()
    private val playerRoom = HashMap<Long, Long>()

    @Synchronized
    fun create(owner: GameSession, dungeonId: Int, maxPlayers: Int, password: String): DungeonRoomState {
        check(playerRoom[owner.playerId] == null) { "player already in dungeon room" }

        val roomId = nextAvailableRoomId()
        val room = DungeonRoomState(
            id = roomId,
            dungeonId = dungeonId,
            instanceId = nextAvailableInstanceId(),
            name = owner.characterName,
            ownerId = owner.playerId,
            password = password,
            maxPlayers = maxPlayers,
        )
        room.members[owner.playerId] = DungeonRoomMember(owner)
        rooms[roomId] = room
        playerRoom[owner.playerId] = roomId
        return room
    }

    @Synchronized
    fun snapshotRooms(): List<DungeonRoomState> =
        rooms.values.filter { !it.starting && !it.started }.map { it.snapshot() }

    @Synchronized
    fun room(roomId: Long): DungeonRoomState? = rooms[roomId]

    @Synchronized
    fun roomOf(playerId: Long): DungeonRoomState? =
        playerRoom[playerId]?.let(rooms::get)

    @Synchronized
    fun join(roomId: Long, session: GameSession): DungeonRoomState? {
        val room = rooms[roomId] ?: return null
        if (room.starting || room.started) return null
        if (playerRoom[session.playerId] != null) return null
        if (room.members.size >= room.maxPlayers) return null

        room.members[session.playerId] = DungeonRoomMember(session)
        playerRoom[session.playerId] = roomId
        return room
    }

    @Synchronized
    fun togglePrepared(playerId: Long): Pair<DungeonRoomState, Boolean>? {
        val room = roomOf(playerId) ?: return null
        if (room.ownerId == playerId || room.starting || room.started) return null
        val member = room.members[playerId] ?: return null
        member.prepared = !member.prepared
        return room to member.prepared
    }

    @Synchronized
    fun rename(ownerId: Long, name: String): DungeonRoomState? {
        val room = roomOf(ownerId) ?: return null
        if (room.ownerId != ownerId || room.starting || room.started) return null
        room.name = name
        return room
    }

    @Synchronized
    fun beginStart(ownerId: Long): DungeonRoomState? {
        val room = roomOf(ownerId) ?: return null
        if (room.ownerId != ownerId || room.starting || room.started) return null
        if (room.members.values.any { it.session.playerId != ownerId && !it.prepared }) return null
        room.starting = true
        return room.snapshot()
    }

    @Synchronized
    fun commitStart(roomId: Long, ownerId: Long): DungeonRoomState? {
        val room = rooms[roomId] ?: return null
        if (room.ownerId != ownerId || !room.starting || room.started) return null
        if (room.members.values.any { it.session.playerId != ownerId && !it.prepared }) {
            room.starting = false
            return null
        }
        room.starting = false
        room.started = true
        return room.snapshot()
    }

    @Synchronized
    fun leave(playerId: Long): DungeonRoomLeaveResult? {
        val roomId = playerRoom.remove(playerId) ?: return null
        val room = rooms[roomId] ?: return null
        val removed = room.members.remove(playerId) ?: return null
        room.starting = false

        var newOwnerId: Long? = null
        var disbanded = false

        if (room.members.isEmpty()) {
            rooms.remove(roomId)
            disbanded = true
        } else if (room.ownerId == playerId) {
            newOwnerId = room.members.keys.first()
            room.ownerId = newOwnerId
            room.members[newOwnerId]?.prepared = false
        }

        return DungeonRoomLeaveResult(room.snapshot(), removed, newOwnerId, disbanded)
    }

    @Synchronized
    fun kick(ownerId: Long, targetId: Long): DungeonRoomLeaveResult? {
        val room = roomOf(ownerId) ?: return null
        if (room.ownerId != ownerId || room.starting || room.started || targetId == ownerId) return null
        if (!room.members.containsKey(targetId)) return null
        return leave(targetId)
    }

    @Synchronized
    fun clear() {
        rooms.clear()
        playerRoom.clear()
        nextRoomId.set(1L)
        nextInstanceId.set(1)
    }

    private fun nextAvailableRoomId(): Long {
        repeat(Int.MAX_VALUE) {
            val candidate = nextRoomId.getAndUpdate { current ->
                if (current >= 0xFFFF_FFFEL) 1L else current + 1L
            }
            if (candidate !in rooms && candidate in 1L..0xFFFF_FFFEL) return candidate
        }
        error("No dungeon room id available")
    }

    private fun nextAvailableInstanceId(): Int {
        repeat(0xFFFF) {
            val candidate = nextInstanceId.getAndUpdate { current ->
                if (current >= 0xFFFF) 1 else current + 1
            }
            if (candidate != 0 && rooms.values.none { it.instanceId == candidate }) return candidate
        }
        error("No dungeon instance id available")
    }

    private fun DungeonRoomState.snapshot(): DungeonRoomState =
        copy(
            members = LinkedHashMap(
                members.mapValues { (_, member) ->
                    DungeonRoomMember(member.session, member.prepared)
                }
            )
        )
}
