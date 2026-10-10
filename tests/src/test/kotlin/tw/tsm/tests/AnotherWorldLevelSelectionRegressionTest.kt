package tw.tsm.tests

import io.mockk.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.data.loaders.AnotherWorldLvDatLoader
import tw.tsm.data.loaders.LimitTimeDungeonDatLoader
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.domain.interfaces.repositories.MissionRecord
import tw.tsm.domain.interfaces.repositories.QuestRepository
import tw.tsm.gamelogic.systems.*
import java.nio.file.Files
import java.nio.file.Path

class AnotherWorldLevelSelectionRegressionTest {
    private val root by lazy {
        generateSequence(Path.of("").toAbsolutePath()) { it.parent }
            .first { Files.exists(it.resolve("File.dat/Data-ios/IOS_AnotherWorldLv_C.dat")) }
    }
    private val tiers by lazy {
        AnotherWorldLvDatLoader.load(
            Files.readAllBytes(root.resolve("File.dat/Data-ios/IOS_AnotherWorldLv_C.dat"))
        )
    }
    private val def by lazy {
        LimitTimeDungeonDatLoader.load(
            Files.readAllBytes(root.resolve("File.dat/Data-ios/IOS_LimitTimeDungeon_C.dat"))
        ).getValue(1)
    }

    @Test
    fun `DVT tier table has exactly 15 monster levels per rebirth and id4 is Lv55`() {
        assertEquals(60, tiers.size)
        for (turn in 0..3) {
            val byTurn = tiers.values.filter { it.turn == turn }.sortedBy { it.id }
            assertEquals(15, byTurn.size)
            assertEquals(10, byTurn.first().level)
            assertEquals(55, byTurn[3].level)
            assertEquals(turn * 15 + 4, byTurn[3].id)
        }
        assertEquals(13137, def.missionId)
        assertEquals(49942, def.sceneId)
    }

    private data class Fixture(
        val system: LimitTimeDungeonSystem,
        val session: GameSession,
        val quest: QuestSystem,
        val sender: ResponseSender,
        val count: RoleCountSystem,
    )

    private fun setup(turn: Int = 0, map: Int = 49942, battle: Long = 0L): Fixture {
        val quest = mockk<QuestSystem>()
        val sender = mockk<ResponseSender>(relaxed = true)
        val count = mockk<RoleCountSystem>()
        val session = mockk<GameSession>(relaxed = true)
        every { session.roleId } returns 31
        every { session.mapId } returns map
        every { session.turn } returns turn
        every { session.battleId } returns battle
        every { session.gmLevel } returns 0
        every { count.getValue(31L, 27) } returns 0
        val system = LimitTimeDungeonSystem(
            mapOf(1 to def), count, sender, mockk(),
            CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            AdminBypassPolicy(), quest, tiers
        )
        return Fixture(system, session, quest, sender, count)
    }

    @Test
    fun `choosing Lv55 while turn0 changes mission from step1 to step4`() = runBlocking {
        val f = setup()
        coEvery { f.quest.selectTrainingMissionStage(31L, 13137, 4) } returns 1
        assertTrue(f.system.selectAnotherWorldLevel(f.session, 4))
        coVerify(exactly = 1) { f.quest.selectTrainingMissionStage(31L, 13137, 4) }
        verify(exactly = 1) { f.sender.sendAddMissionStep(f.session, 13137, 3) }
        verify(exactly = 0) { f.sender.sendDecMissionStep(any(), any(), any()) }
    }

    @Test
    fun `choosing lower monster level sends decrement without completing mission`() = runBlocking {
        val f = setup()
        coEvery { f.quest.selectTrainingMissionStage(31L, 13137, 2) } returns 4
        assertTrue(f.system.selectAnotherWorldLevel(f.session, 2))
        verify(exactly = 1) { f.sender.sendDecMissionStep(f.session, 13137, 2) }
        verify(exactly = 0) { f.sender.sendAddMissionStep(any(), any(), any()) }
    }

    @Test
    fun `invalid tier or forged rebirth level is never persisted`() = runBlocking {
        val f = setup(turn = 0)
        assertFalse(f.system.selectAnotherWorldLevel(f.session, 19)) // Lv55 belongs to turn1
        assertFalse(f.system.selectAnotherWorldLevel(f.session, 0))
        assertFalse(f.system.selectAnotherWorldLevel(f.session, 61))
        coVerify(exactly = 0) { f.quest.selectTrainingMissionStage(any(), any(), any()) }
    }

    @Test
    fun `selection requires training scene and no active combat`() = runBlocking {
        for (f in listOf(setup(map = 10701), setup(battle = 11L))) {
            assertFalse(f.system.selectAnotherWorldLevel(f.session, 4))
            coVerify(exactly = 0) { f.quest.selectTrainingMissionStage(any(), any(), any()) }
        }
    }

    @Test
    fun `expired training time forbids level switch`() = runBlocking {
        val f = setup()
        every { f.count.getValue(31L, 27) } returns 120
        assertFalse(f.system.selectAnotherWorldLevel(f.session, 4))
        coVerify(exactly = 0) { f.quest.selectTrainingMissionStage(any(), any(), any()) }
    }

    @Test
    fun `quest stage mutation updates once and preserves already selected stage`() = runBlocking {
        val repo = mockk<QuestRepository>()
        val quests = QuestSystem(repo, emptyMap())
        coEvery { repo.getMissions(31L) } returns listOf(MissionRecord(13137, 1))
        coEvery { repo.upsertMission(31L, 13137, 4) } just Runs
        assertEquals(1, quests.selectTrainingMissionStage(31L, 13137, 4))
        coVerify(exactly = 1) { repo.upsertMission(31L, 13137, 4) }

        coEvery { repo.getMissions(31L) } returns listOf(MissionRecord(13137, 4))
        assertEquals(4, quests.selectTrainingMissionStage(31L, 13137, 4))
        coVerify(exactly = 1) { repo.upsertMission(any(), any(), any()) }
    }
}
