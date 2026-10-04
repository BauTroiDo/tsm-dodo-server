package tw.tsm.tests

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.gamelogic.systems.DungeonRoomMember
import tw.tsm.gamelogic.systems.DungeonRoomState
import tw.tsm.gamelogic.systems.DungeonSystem
import tw.tsm.gamelogic.systems.PartySystem
import tw.tsm.gamelogic.systems.QuestSystem
import tw.tsm.gamelogic.systems.SpecialSceneTeleportService

class DungeonActiveRunTest {
    private fun session(id: Long): GameSession =
        mockk<GameSession>(relaxed = true).also {
            every { it.playerId } returns id
        }

    private fun system() = DungeonSystem(
        emptyMap(),
        mockk<QuestSystem>(),
        mockk<PartySystem>(),
        mockk<SpecialSceneTeleportService>(),
    )

    @Test
    fun `single active run retains dungeon id and start time`() = runBlocking {
        val system = system()
        val player = session(6001)
        system.markSingleStarted(player, 2, 46000.25)

        val run = system.getActiveRun(player.playerId)!!
        assertEquals(2, run.dungeonId)
        assertEquals(46000.25, run.startTime)
        assertEquals(0, run.instanceId)

        system.clearActiveRun(player)
        assertNull(system.getActiveRun(player.playerId))
    }

    @Test
    fun `multiplayer members share dungeon start and instance`() = runBlocking {
        val system = system()
        val owner = session(6101)
        val member = session(6102)
        val room = DungeonRoomState(
            id = 77,
            dungeonId = 1,
            instanceId = 12,
            name = "room",
            ownerId = owner.playerId,
            password = "",
            maxPlayers = 5,
            members = linkedMapOf(
                owner.playerId to DungeonRoomMember(owner),
                member.playerId to DungeonRoomMember(member, prepared = true),
            ),
            started = true,
        )

        system.markRoomStarted(room, 46001.5)

        assertEquals(1, system.getActiveRun(owner.playerId)?.dungeonId)
        assertEquals(12, system.getActiveRun(owner.playerId)?.instanceId)
        assertEquals(46001.5, system.getActiveRun(member.playerId)?.startTime)

        system.clearRoomRuns(room)
        assertNull(system.getActiveRun(owner.playerId))
        assertNull(system.getActiveRun(member.playerId))
    }
}
