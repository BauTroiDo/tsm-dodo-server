package tw.tsm.tests

import io.mockk.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.data.loaders.EveDataLoader
import tw.tsm.data.loaders.LimitTimeDungeonDatLoader
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.domain.interfaces.repositories.MissionRecord
import tw.tsm.domain.interfaces.repositories.QuestRepository
import tw.tsm.gamelogic.systems.*
import java.nio.file.Files
import java.nio.file.Path

/** Regression for DVT LimitFightArea: scene 49942 has NO placed monsters. */
class LimitTraining49942EncounterRegressionTest {
    private val root by lazy {
        generateSequence(Path.of("").toAbsolutePath()) { it.parent }
            .first { Files.exists(it.resolve("File.dat/Data-ios/IOS_LimitTimeDungeon_C.dat")) }
    }

    private val def by lazy {
        LimitTimeDungeonDatLoader.load(
            Files.readAllBytes(root.resolve("File.dat/Data-ios/IOS_LimitTimeDungeon_C.dat"))
        ).getValue(1)
    }

    @Test
    fun `DVT scene49942 has mine-triggered fights requiring mission 13137 stage 1 to 60`() {
        assertEquals(49942, def.sceneId)
        assertEquals(13137, def.missionId)
        val scene = EveDataLoader.load(Files.readAllBytes(root.resolve("gamedata/Eve.emg"))).getValue(49942)
        assertTrue(scene.npcs.isEmpty(), "The DVT map deliberately has no static mob NPCs")
        val entireMapMine = scene.mines.getValue(1)
        assertEquals(1, entireMapMine.sizeKind)
        assertEquals(listOf(4, 2, 3, 6), entireMapMine.events)
        val stages = entireMapMine.events.flatMap { no ->
            scene.npcEvents.getValue(no).conditions.map { c ->
                assertEquals(2, c.conditionClass)
                assertEquals(13137, c.conditionParameter)
                assertEquals(5, c.conditionOps)
                assertTrue(c.results.isNotEmpty())
                assertTrue(c.results.all { it.resultType == 3 && it.resultMeanNo in scene.fightDatas })
                c.conditionValue
            }
        }
        assertEquals((1..60).toSet(), stages.toSet())
        assertFalse(stages.contains(0), "A fresh player at mission step 0 cannot trigger any fight")
    }

    @Test
    fun `first entry initializes mission and sends DVT mission step packet`() = runBlocking {
        val quest = mockk<QuestSystem>()
        coEvery { quest.ensureMissionStarted(31L, 13137) } returns true
        val response = mockk<ResponseSender>(relaxed = true)
        val session = mockk<GameSession>()
        every { session.roleId } returns 31
        val system = LimitTimeDungeonSystem(
            mapOf(def.index to def), mockk(), response, mockk(),
            CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            AdminBypassPolicy(), quest,
        )
        assertTrue(system.ensureEncounterMission(session, def))
        coVerify(exactly = 1) { quest.ensureMissionStarted(31L, 13137) }
        verify(exactly = 1) { response.sendAddMissionStep(session, 13137, 1) }
    }

    @Test
    fun `reentry never resets an already started training stage`() = runBlocking {
        val quest = mockk<QuestSystem>()
        coEvery { quest.ensureMissionStarted(31L, 13137) } returns false
        val response = mockk<ResponseSender>(relaxed = true)
        val session = mockk<GameSession>()
        every { session.roleId } returns 31
        val system = LimitTimeDungeonSystem(
            mapOf(def.index to def), mockk(), response, mockk(),
            CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            AdminBypassPolicy(), quest,
        )
        assertTrue(system.ensureEncounterMission(session, def))
        verify(exactly = 0) { response.sendAddMissionStep(any(), any(), any()) }
    }

    @Test
    fun `mission database failure rejects preparation before teleport`() = runBlocking {
        val quest = mockk<QuestSystem>()
        coEvery { quest.ensureMissionStarted(31L, 13137) } throws IllegalStateException("Test persistence failure")
        val response = mockk<ResponseSender>(relaxed = true)
        val session = mockk<GameSession>()
        every { session.roleId } returns 31
        val system = LimitTimeDungeonSystem(
            mapOf(def.index to def), mockk(), response, mockk(),
            CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            AdminBypassPolicy(), quest,
        )
        assertFalse(system.ensureEncounterMission(session, def))
        verify(exactly = 0) { response.sendAddMissionStep(any(), any(), any()) }
    }

    @Test
    fun `quest initialization persists once but never overwrites higher stage`() = runBlocking {
        val repo = mockk<QuestRepository>()
        val qs = QuestSystem(repo, emptyMap())
        coEvery { repo.getMissions(31L) } returns emptyList()
        coEvery { repo.upsertMission(31L, 13137, 1) } just Runs
        assertTrue(qs.ensureMissionStarted(31L, 13137))
        coVerify(exactly = 1) { repo.upsertMission(31L, 13137, 1) }

        coEvery { repo.getMissions(31L) } returns listOf(MissionRecord(13137, 45))
        assertFalse(qs.ensureMissionStarted(31L, 13137))
        coVerify(exactly = 1) { repo.upsertMission(any(), any(), any()) }
    }
}
