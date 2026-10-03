package tw.tsm.tests

import io.mockk.*
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tw.tsm.app.handlers.DungeonHandler
import tw.tsm.app.handlers.PartyHandler
import tw.tsm.data.loaders.DungeonComboDatLoader
import tw.tsm.data.loaders.DungeonDatLoader
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.gamelogic.systems.ActiveDungeonRun
import tw.tsm.gamelogic.systems.DailyProgressSystem
import tw.tsm.gamelogic.systems.DungeonRoomRegistry
import tw.tsm.gamelogic.systems.DungeonSettlement
import tw.tsm.gamelogic.systems.DungeonSystem
import tw.tsm.gamelogic.systems.PartySystem
import tw.tsm.gamelogic.systems.QuestSystem
import tw.tsm.gamelogic.systems.SpecialSceneTeleportService
import tw.tsm.gamelogic.systems.WorldManager
import tw.tsm.protocol.PacketReader

class DungeonRewardLeaveRegressionTest {
    private fun dataBytes(vararg parts: String): ByteArray =
        Files.readAllBytes(Path.of("..", "File.dat", *parts))

    private fun system(
        special: SpecialSceneTeleportService = mockk(relaxed = true),
        clock: () -> Long = { 1_000_000L },
    ) = DungeonSystem(
        DungeonDatLoader.load(dataBytes("Data-ios", "IOS_Dungeon_C.dat")),
        mockk<QuestSystem>(relaxed = true),
        mockk<PartySystem>(relaxed = true),
        special,
        comboDefs = DungeonComboDatLoader.load(dataBytes("Data-ios", "IOS_DungeonCombo_C.dat")),
        clockMillis = clock,
    )

    @Test
    fun timedRewardTiersMatchDvtDungeonBags() {
        val system = system()

        assertEquals(emptyList<Pair<Int, Int>>(), system.resolveRewards(1, 0, 0, true))
        assertEquals(listOf(46921 to 1), system.resolveRewards(1, 0, 599, true))
        assertEquals(listOf(46921 to 5), system.resolveRewards(1, 0, 600, true))
        assertEquals(listOf(46921 to 10), system.resolveRewards(1, 0, 900, true))

        assertEquals(listOf(46923 to 3), system.resolveRewards(15, 0, 1000, true))
        assertEquals(listOf(46924 to 3), system.resolveRewards(16, 0, 1000, true))
        assertEquals(listOf(32649 to 3), system.resolveRewards(17, 0, 1000, true))
    }

    @Test
    fun terminalBossWinSelectsHundredPercentDamageRow() {
        val system = system()
        assertEquals(listOf(45665 to 1), system.resolveRewards(2, 0, 0, true))
        assertEquals(emptyList<Pair<Int, Int>>(), system.resolveRewards(2, 0, 0, false))
    }

    @Test
    fun weeklyFixedRewardTablesArePreserved() {
        val system = system()
        assertEquals(listOf(29845 to 1), system.resolveRewards(18, 0, 1800, true))
        assertEquals(listOf(29846 to 1), system.resolveRewards(63, 0, 1800, true))
        assertEquals(listOf(29846 to 20), system.resolveRewards(82, 0, 1800, true))
    }

    @Test
    fun activeDungeonRemembersOriginAndCanExit() = runBlocking {
        val special = mockk<SpecialSceneTeleportService>()
        coEvery { special.teleport(any(), any(), any(), any(), any()) } just Runs
        val system = system(special)

        val session = mockk<GameSession>(relaxed = true)
        every { session.playerId } returns 991L
        every { session.roleId } returns 991
        every { session.mapId } returns 12003
        every { session.x } returns 70
        every { session.y } returns 580
        every { session.instanceId } returns 0
        every { session.battleId } returns 0L

        system.markSingleStarted(session, 2, 46000.0)
        assertEquals(12003, system.getActiveRun(991L)?.originMapId)
        assertTrue(system.exitActiveDungeon(session))
        coVerify(exactly = 1) { special.teleport(session, 12003, 70, 580, 0) }
        assertNull(system.getActiveRun(991L))
    }

    @Test
    fun c13LeavePrefersActiveDungeonOverPartyLeave() = runBlocking {
        val party = mockk<PartySystem>(relaxed = true)
        val world = mockk<WorldManager>(relaxed = true)
        val sender = mockk<ResponseSender>(relaxed = true)
        val dungeon = mockk<DungeonSystem>()
        val rooms = mockk<DungeonRoomRegistry>(relaxed = true)
        val session = mockk<GameSession>(relaxed = true)
        every { session.playerId } returns 992L
        every { dungeon.getActiveRun(992L) } returns ActiveDungeonRun(2, 46000.0, 0)
        coEvery { dungeon.exitActiveDungeon(session) } returns true

        val handler = PartyHandler(
            party,
            world,
            sender,
            mockk<DailyProgressSystem>(relaxed = true),
            dungeon,
            rooms,
        )
        handler.handle(session, 4, ByteArray(8))

        coVerify(exactly = 1) { dungeon.exitActiveDungeon(session) }
        verify(exactly = 0) { party.leaveParty(any()) }
    }

    @Test
    fun dungeonResultPacketCarriesUInt32RewardQuantities() {
        val handler = DungeonHandler(mockk(relaxed = true))
        val packet = slot<ByteArray>()
        val session = mockk<GameSession>(relaxed = true)
        every { session.send(capture(packet)) } just Runs

        handler.sendDungeonResult(
            session,
            DungeonSettlement(1, result = 0, rewards = listOf(46921 to 10, 65412 to 70)),
        )

        val r = PacketReader(packet.captured)
        assertEquals(47, r.readUByte())
        assertEquals(12, r.readUByte())
        assertEquals(0, r.readUByte())
        assertEquals(0, r.readUByte())
        assertEquals(1, r.readUShortLE())
        assertEquals(2, r.readUByte())
        assertEquals(46921, r.readUShortLE())
        assertEquals(10, r.readIntLE())
        assertEquals(65412, r.readUShortLE())
        assertEquals(70, r.readIntLE())
        assertEquals(0, r.remaining)
    }
}
