package tw.tsm.gamelogic.systems

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import tw.tsm.domain.interfaces.GameSession

/** Original outdoor location remains the save point while visiting the house. */
data class HouseReturnLocation(val mapId: Int, val x: Int, val y: Int, val instanceId: Int)
data class HouseVisit(val ownerId: Long, val roomId: Int, val origin: HouseReturnLocation, var building: Int = 1, var floor: Int = 1)

/**
 * Phase-1 DVT home visit. Rooms are private to their owner; visitor permissions,
 * furniture persistence and paid expansions are NOT enabled by this policy.
 */
class HouseVisitSystem {
    companion object {
        const val STARTER_MAP = 54964
        const val FINAL_HOUSE_MAP = 54967
        fun isHouseMap(mapId: Int): Boolean = mapId in STARTER_MAP..FINAL_HOUSE_MAP
        fun mapForBuilding(building: Int): Int? =
            if (building in 1..4) STARTER_MAP + building - 1 else null
        const val STARTER_BUILDING = 1
        const val STARTER_FLOOR = 1
        const val STARTER_LEVEL = 1
        // The starting house occupies the SceneSet 54964 region.
        const val ENTRY_X = 900
        const val ENTRY_Y = 600
    }

    private val ownerRoomIds = ConcurrentHashMap<Long, Int>()
    private val nextRoom = AtomicInteger(1)
    private val activeVisits = ConcurrentHashMap<Long, HouseVisit>()

    fun getVisit(playerId: Long): HouseVisit? = activeVisits[playerId]

    fun moveWithinHouse(session: GameSession, building: Int, floor: Int): Boolean {
        if (!isInsideOwnHouse(session) || mapForBuilding(building) == null || floor !in 1..2) return false
        val visit = activeVisits[session.playerId] ?: return false
        visit.building = building
        visit.floor = floor
        return true
    }

    fun beginOwnHouse(session: GameSession): HouseVisit? {
        if (session.playerId <= 0 || session.roleId <= 0) return null
        activeVisits[session.playerId]?.let { return it }
        if (session.mapId <= 0 || isHouseMap(session.mapId) || session.instanceId != 0) return null
        // DVT Role.playerId is the network/world ID, not the DB character ID.
        val ownerId = session.playerId
        val room = ownerRoomIds.computeIfAbsent(ownerId) { nextRoom.getAndIncrement() }
        if (room !in 1..0xFFFF) return null
        val visit = HouseVisit(ownerId, room, HouseReturnLocation(session.mapId, session.x, session.y, session.instanceId))
        return activeVisits.putIfAbsent(session.playerId, visit) ?: visit
    }

    fun isInsideOwnHouse(session: GameSession): Boolean =
        activeVisits[session.playerId]?.let {
            it.ownerId == session.playerId &&
                session.mapId == mapForBuilding(it.building) && session.instanceId == it.roomId
        } ?: false

    /** Never persist a transient house map as a login location. */
    fun persistedLocation(session: GameSession): HouseReturnLocation {
        val visit = activeVisits[session.playerId]
        return if (visit != null && isHouseMap(session.mapId)) visit.origin
        else HouseReturnLocation(session.mapId, session.x, session.y, session.instanceId)
    }

    fun finish(session: GameSession): HouseReturnLocation? =
        if (isInsideOwnHouse(session)) activeVisits.remove(session.playerId)?.origin else null

    fun cleanup(playerId: Long) { activeVisits.remove(playerId) }
}
