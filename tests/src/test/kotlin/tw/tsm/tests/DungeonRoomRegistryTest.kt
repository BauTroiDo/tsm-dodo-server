package tw.tsm.tests

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.gamelogic.systems.DungeonRoomRegistry

class DungeonRoomRegistryTest {
    private fun session(id: Long, name: String): GameSession =
        mockk<GameSession>(relaxed = true).also {
            every { it.playerId } returns id
            every { it.characterName } returns name
        }

    @Test
    fun `owner leave migrates ownership and clears new owner prepare state`() {
        val rooms = DungeonRoomRegistry()
        val owner = session(1001, "Owner")
        val second = session(1002, "Second")
        val third = session(1003, "Third")

        val room = rooms.create(owner, dungeonId = 1, maxPlayers = 5, password = "")
        rooms.join(room.id, second)
        rooms.join(room.id, third)

        val prepared = rooms.togglePrepared(second.playerId)
        assertEquals(true, prepared?.second)

        val left = rooms.leave(owner.playerId)!!
        assertFalse(left.disbanded)
        assertEquals(second.playerId, left.newOwnerId)
        assertEquals(second.playerId, left.room.ownerId)
        assertEquals(false, left.room.members.getValue(second.playerId).prepared)
        assertNull(rooms.roomOf(owner.playerId))
        assertEquals(room.id, rooms.roomOf(third.playerId)?.id)
    }

    @Test
    fun `room capacity kick and final leave are consistent`() {
        val rooms = DungeonRoomRegistry()
        val owner = session(2001, "Owner")
        val member = session(2002, "Member")
        val extra = session(2003, "Extra")

        val room = rooms.create(owner, dungeonId = 1, maxPlayers = 2, password = "1234")
        assertTrue(room.isLocked)
        assertEquals(room.id, rooms.join(room.id, member)?.id)
        assertNull(rooms.join(room.id, extra))

        val kicked = rooms.kick(owner.playerId, member.playerId)!!
        assertEquals(member.playerId, kicked.removed.session.playerId)
        assertNull(rooms.roomOf(member.playerId))

        val last = rooms.leave(owner.playerId)!!
        assertTrue(last.disbanded)
        assertNull(rooms.room(room.id))
    }
}
