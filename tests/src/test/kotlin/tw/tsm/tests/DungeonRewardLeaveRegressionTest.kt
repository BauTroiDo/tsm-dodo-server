package tw.tsm.tests

import io.mockk.*
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tw.tsm.app.handlers.DungeonHandler
import tw.tsm.app.handlers.PartyHandler
import tw.tsm.app.handlers.RelocateHandler
import tw.tsm.data.loaders.DungeonComboDatLoader
import tw.tsm.data.loaders.DungeonDatLoader
import tw.tsm.data.loaders.SceneSetDatLoader
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.domain.interfaces.repositories.DungeonRunRecord
import tw.tsm.domain.interfaces.repositories.DungeonRunRepository
import tw.tsm.domain.util.OleDate
import tw.tsm.gamelogic.systems.DailyProgressSystem
import tw.tsm.gamelogic.systems.DungeonRoomRegistry
import tw.tsm.gamelogic.systems.DungeonSceneTimer
import tw.tsm.gamelogic.systems.DungeonSettlement
import tw.tsm.gamelogic.systems.DungeonSystem
import tw.tsm.gamelogic.systems.EveTriggerRouter
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
        sceneSetDefs = SceneSetDatLoader.load(dataBytes("Data", "SceneSet_C.dat")),
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
    fun persistedRunSurvivesServerRestartAndRestoresExactOrigin() = runBlocking {
        val defs = DungeonDatLoader.load(dataBytes("Data-ios", "IOS_Dungeon_C.dat"))
        val scenes = SceneSetDatLoader.load(dataBytes("Data", "SceneSet_C.dat"))
        val def = defs.getValue(2)
        val repo = mockk<DungeonRunRepository>()
        val persisted = slot<DungeonRunRecord>()
        coEvery { repo.save(capture(persisted)) } just Runs
        coEvery { repo.load(998L) } answers { persisted.captured }
        coEvery { repo.clear(998L) } just Runs

        val special = mockk<SpecialSceneTeleportService>()
        coEvery { special.teleport(any(), any(), any(), any(), any()) } just Runs

        val before = mockk<GameSession>(relaxed = true)
        every { before.playerId } returns 998L
        every { before.roleId } returns 998
        every { before.mapId } returns 12003
        every { before.x } returns 70
        every { before.y } returns 580
        every { before.instanceId } returns 0

        val firstRuntime = DungeonSystem(
            defs,
            mockk<QuestSystem>(relaxed = true),
            mockk<PartySystem>(relaxed = true),
            special,
            clockMillis = { 1_000_000L },
            sceneSetDefs = scenes,
            dungeonRunRepository = repo,
        )
        firstRuntime.markSingleStarted(before, def.id, 46_000.0)

        assertEquals(def.id, persisted.captured.dungeonId)
        assertEquals(12003, persisted.captured.originMapId)
        assertEquals(70, persisted.captured.originX)
        assertEquals(580, persisted.captured.originY)
        assertEquals(1_000_000L, persisted.captured.startedAtMillis)

        val after = mockk<GameSession>(relaxed = true)
        every { after.playerId } returns 998L
        every { after.roleId } returns 998
        every { after.characterName } returns "Restarted"
        every { after.mapId } returns def.sceneId
        every { after.instanceId } returns 0
        every { after.battleId } returns 0L
        every { after.state } returns SessionState.IN_GAME

        val secondRuntime = DungeonSystem(
            defs,
            mockk<QuestSystem>(relaxed = true),
            mockk<PartySystem>(relaxed = true),
            special,
            clockMillis = { 1_001_000L },
            sceneSetDefs = scenes,
            dungeonRunRepository = repo,
        )

        val timer = secondRuntime.onSceneReady(after, 47_000.0)
        assertEquals(def.id, timer?.dungeonId)
        assertEquals(46_000.0, timer?.startTime)
        assertEquals(true, timer?.recovered)
        assertEquals(12003, secondRuntime.getActiveRun(998L)?.originMapId)

        assertTrue(secondRuntime.exitDungeonFromHud(after))
        coVerify(exactly = 1) { special.teleport(after, 12003, 70, 580, 0) }
        coVerify(exactly = 1) { repo.load(998L) }
        coVerify(exactly = 1) { repo.clear(998L) }
        assertNull(secondRuntime.getActiveRun(998L))
    }

    @Test
    fun hudExitUsesSceneKindTwoEvenWhenMapIsNotInDungeonData() = runBlocking {
        val special = mockk<SpecialSceneTeleportService>()
        val dungeonSceneIds = DungeonDatLoader.load(dataBytes("Data-ios", "IOS_Dungeon_C.dat"))
            .values.map { it.sceneId }.toSet()
        val sceneId = SceneSetDatLoader.load(dataBytes("Data", "SceneSet_C.dat"))
            .entries.first { (id, def) -> def.kind == 2 && id !in dungeonSceneIds }
            .key
        val session = mockk<GameSession>(relaxed = true)
        every { session.playerId } returns 994L
        every { session.mapId } returns sceneId
        every { session.battleId } returns 0L
        coEvery { special.exitSpecialScene(session, sceneId) } returns true

        val system = system(special)
        assertNull(system.getActiveRun(994L))
        assertTrue(system.exitDungeonFromHud(session))

        coVerify(exactly = 1) { special.exitSpecialScene(session, sceneId) }
    }

    @Test
    fun c13LeavePrefersDungeonHudExitOverPartyLeave() = runBlocking {
        val party = mockk<PartySystem>(relaxed = true)
        val world = mockk<WorldManager>(relaxed = true)
        val sender = mockk<ResponseSender>(relaxed = true)
        val dungeon = mockk<DungeonSystem>()
        val rooms = mockk<DungeonRoomRegistry>(relaxed = true)
        val session = mockk<GameSession>(relaxed = true)
        every { session.playerId } returns 992L
        coEvery { dungeon.exitDungeonFromHud(session) } returns true

        val handler = PartyHandler(
            party,
            world,
            sender,
            mockk<DailyProgressSystem>(relaxed = true),
            dungeon,
            rooms,
        )
        handler.handle(session, 4, ByteArray(8))

        coVerify(exactly = 1) { dungeon.exitDungeonFromHud(session) }
        verify(exactly = 0) { party.leaveParty(any()) }
    }

    @Test
    fun c13LeaveStillFallsThroughToPartyOutsideDungeon() = runBlocking {
        val party = mockk<PartySystem>(relaxed = true)
        val dungeon = mockk<DungeonSystem>()
        val session = mockk<GameSession>(relaxed = true)
        every { session.playerId } returns 993L
        coEvery { dungeon.exitDungeonFromHud(session) } returns false

        val handler = PartyHandler(
            party,
            mockk<WorldManager>(relaxed = true),
            mockk<ResponseSender>(relaxed = true),
            mockk<DailyProgressSystem>(relaxed = true),
            dungeon,
            mockk<DungeonRoomRegistry>(relaxed = true),
        )
        handler.handle(session, 4, ByteArray(8))

        coVerify(exactly = 1) { dungeon.exitDungeonFromHud(session) }
        verify(exactly = 1) { party.leaveParty(993L) }
    }

    @Test
    fun sceneReadyRecoversTimedDungeonAndServerTimeoutAutoExits() = runBlocking {
        val defs = DungeonDatLoader.load(dataBytes("Data-ios", "IOS_Dungeon_C.dat"))
        val scenes = SceneSetDatLoader.load(dataBytes("Data", "SceneSet_C.dat"))
        val candidates = defs.values
            .filter {
                it.kind in setOf(DungeonSystem.KIND_NORMAL_MULTIPLAYER, DungeonSystem.KIND_SINGLE) &&
                    it.timeMinutes > 0 && scenes[it.sceneId]?.kind == DungeonSystem.SCENE_KIND_DYNAMIC_DUNGEON
            }
            .groupBy { it.sceneId }
            .values
            .filter { it.size == 1 }
            .flatten()
        val def = candidates.minBy { it.timeMinutes }

        val special = mockk<SpecialSceneTeleportService>()
        val session = mockk<GameSession>(relaxed = true)
        every { session.playerId } returns 996L
        every { session.roleId } returns 996
        every { session.characterName } returns "Timeout"
        every { session.mapId } returns def.sceneId
        every { session.instanceId } returns 0
        every { session.battleId } returns 0L
        every { session.state } returns SessionState.IN_GAME
        coEvery { special.exitSpecialScene(session, def.sceneId) } returns true

        val system = DungeonSystem(
            defs,
            mockk<QuestSystem>(relaxed = true),
            mockk<PartySystem>(relaxed = true),
            special,
            sceneSetDefs = scenes,
            scope = this,
            dungeonMinuteMillis = 1L,
        )

        val timer = system.onSceneReady(session, 46_000.25)
        assertEquals(def.id, timer?.dungeonId)
        assertEquals(46_000.25, timer?.startTime)
        assertEquals(true, timer?.recovered)

        delay(def.timeMinutes.toLong() + 100L)
        coVerify(exactly = 1) { special.exitSpecialScene(session, def.sceneId) }
        assertNull(system.getActiveRun(996L))
    }

    @Test
    fun relocateSceneReadySendsReconnectTimerWithDungeonId() = runBlocking {
        val session = mockk<GameSession>(relaxed = true)
        val packets = mutableListOf<ByteArray>()
        every { session.state } returns SessionState.IN_GAME
        every { session.playerId } returns 997L
        every { session.mapId } returns 62001
        every { session.instanceId } returns 0
        every { session.send(capture(packets)) } just Runs

        val world = mockk<WorldManager>(relaxed = true)
        every { world.getPlayersInMap(any(), any()) } returns emptySet()
        val dungeon = mockk<DungeonSystem>()
        val def = DungeonDatLoader.load(dataBytes("Data-ios", "IOS_Dungeon_C.dat")).getValue(2)
        coEvery { dungeon.onSceneReady(session, any()) } returns
            DungeonSceneTimer(dungeonId = 2, startTime = 46_123.5, recovered = true)
        every { dungeon.getDef(2) } returns def
        val eveTrigger = mockk<EveTriggerRouter>(relaxed = true)

        val handler = RelocateHandler(
            world,
            mockk<ResponseSender>(relaxed = true),
            dungeonSystem = dungeon,
            eveTriggerRouter = eveTrigger,
        )
        handler.handle(session, 1, ByteArray(0))

        assertEquals(1, packets.size)
        val packet = PacketReader(packets.single())
        assertEquals(47, packet.readUByte())
        assertEquals(18, packet.readUByte())
        assertEquals(0, packet.readUByte())
        assertEquals(46_123.5, packet.readDoubleLE())
        assertEquals(2, packet.readUShortLE())
        assertEquals(0, packet.remaining)
        coVerify(exactly = 1) { dungeon.onSceneReady(session, any()) }
        coVerify(exactly = 1) { eveTrigger.triggerDungeonEntry(session, def.missionFlag) }
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
