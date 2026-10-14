package tw.tsm.tests

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.gamelogic.systems.HouseVisitSystem

class DvtHouseVisitSafetyTest {
    private fun session(playerId: Long, charId: Int, instanceId: Int = 0): GameSession =
        mockk(relaxed = true) {
            every { this@mockk.playerId } returns playerId
            every { roleId } returns charId
            every { mapId } returns 12861
            every { x } returns 1191
            every { y } returns 1530
            every { this@mockk.instanceId } returns instanceId
        }

    @Test
    fun ownHomeIsolatedAndOutdoorPositionSurvivesAutosaveAndDisconnect() {
        val visits = HouseVisitSystem()
        val a = session(13, 31)
        val b = session(14, 32)
        val aVisit = visits.beginOwnHouse(a)!!
        val bVisit = visits.beginOwnHouse(b)!!
        assertNotEquals(aVisit.roomId, bVisit.roomId)
        assertEquals(13L, aVisit.ownerId) // S001-002 / Lua Role.playerId, not charId=31
        assertEquals(12861, aVisit.origin.mapId)
        assertEquals(1191, aVisit.origin.x)
        assertEquals(1530, aVisit.origin.y)

        every { a.mapId } returns HouseVisitSystem.STARTER_MAP
        every { a.instanceId } returns aVisit.roomId
        every { a.x } returns 900
        every { a.y } returns 600
        assertTrue(visits.isInsideOwnHouse(a))
        assertEquals(aVisit.origin, visits.persistedLocation(a))
        assertEquals(aVisit.origin, visits.finish(a))
        assertFalse(visits.isInsideOwnHouse(a))
    }

    @Test
    fun changingToSecondFloorRetainsOnePrivateRoomAndOriginalReturnPoint() {
        val visits = HouseVisitSystem()
        val player = session(42, 31)
        val room = visits.beginOwnHouse(player)!!
        val origin = room.origin
        every { player.mapId } returns HouseVisitSystem.STARTER_MAP
        every { player.instanceId } returns room.roomId
        assertTrue(visits.isInsideOwnHouse(player))
        assertTrue(visits.moveWithinHouse(player, 1, 2))
        assertEquals(2, visits.getVisit(player.playerId)?.floor)
        assertTrue(visits.isInsideOwnHouse(player))
        assertEquals(origin, visits.persistedLocation(player))

        // Buildings have separate scenes; a stale old scene cannot authorize
        // house commands while the new map transition is in progress.
        assertTrue(visits.moveWithinHouse(player, 2, 1))
        assertFalse(visits.isInsideOwnHouse(player))
        every { player.mapId } returns HouseVisitSystem.mapForBuilding(2)!!
        assertTrue(visits.isInsideOwnHouse(player))
        assertEquals(origin, visits.persistedLocation(player))
        assertEquals(origin, visits.finish(player))
        assertFalse(visits.isInsideOwnHouse(player))
    }

    @Test
    fun lockedOrInvalidHouseTransitionCannotChangeVisitState() {
        val visits = HouseVisitSystem()
        val player = session(43, 31)
        val room = visits.beginOwnHouse(player)!!
        assertFalse(visits.moveWithinHouse(player, 1, 2)) // still outdoors
        every { player.mapId } returns HouseVisitSystem.STARTER_MAP
        every { player.instanceId } returns room.roomId
        assertFalse(visits.moveWithinHouse(player, 5, 1))
        assertFalse(visits.moveWithinHouse(player, 1, 0))
        assertFalse(visits.moveWithinHouse(player, 1, 3))
        assertEquals(1, visits.getVisit(player.playerId)?.building)
        assertEquals(1, visits.getVisit(player.playerId)?.floor)
    }

    @Test
    fun invalidInstanceAndReentrantHouseEntryNeverEraseOutdoorOrigin() {
        val visits = HouseVisitSystem()
        assertNull(visits.beginOwnHouse(session(13, 31, instanceId = 7)))
        val player = session(13, 31)
        val first = visits.beginOwnHouse(player)!!
        every { player.mapId } returns HouseVisitSystem.STARTER_MAP
        every { player.instanceId } returns first.roomId
        assertEquals(first, visits.beginOwnHouse(player))
        assertEquals(first.origin, visits.persistedLocation(player))
        visits.cleanup(player.playerId)
        assertNull(visits.getVisit(player.playerId))
    }
}
