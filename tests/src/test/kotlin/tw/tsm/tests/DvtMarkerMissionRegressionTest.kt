package tw.tsm.tests

import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.data.loaders.MarkDatLoader
import tw.tsm.data.loaders.MarkStepDatLoader
import tw.tsm.domain.data.EveResult
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.gamelogic.systems.*
import java.nio.file.Files
import java.nio.file.Path

class DvtMarkerMissionRegressionTest {
    private fun file(vararg parts: String): ByteArray {
        val direct = Path.of(parts.first(), *parts.drop(1).toTypedArray())
        val path = if (Files.exists(direct)) direct else Path.of("..", *parts)
        return Files.readAllBytes(path)
    }

    @Test
    fun markerMission11121UsesCompletionFlagFromDvtData() = runTest {
        val markDefs = MarkDatLoader.load(file("File.dat", "Data-ios", "IOS_Mark_C.dat")).first
        val stepMissionIds = MarkStepDatLoader.loadMissionIds(
            file("File.dat", "Data-ios", "IOS_MarkStep_C.dat")
        )
        assertEquals(588, markDefs[11121]?.bitId)
        assertFalse(11121 in stepMissionIds)
        assertTrue(11120 in stepMissionIds)

        val response = mockk<ResponseSender>(relaxed = true)
        val quests = mockk<QuestSystem>(relaxed = true)
        val session = mockk<GameSession>(relaxed = true) {
            every { roleId } returns 31
            every { characterName } returns "MarkerTester"
        }

        val executor = EveResultExecutor(
            response, quests, mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
            mockk(relaxed = true), emptyMap(), markDefs, emptyMap(), emptyMap(),
            mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
            mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
            mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
            stepMissionIds,
        )

        coEvery { quests.addMissionStep(31L, 11121, 1) } returns 0
        assertTrue(
            executor.executeResultClass(
                session,
                EveResult(0, 1, 0, 2, 11121, 1, 1, 0)
            )
        )

        coVerify(exactly = 1) { quests.setFlag(31L, 588, 1) }
        verify(exactly = 1) { response.sendSetMissionFlags(session, any()) }
    }

    @Test
    fun missionWithRealMarkStepDoesNotAutoSetCompletionFlag() = runTest {
        val markDefs = MarkDatLoader.load(file("File.dat", "Data-ios", "IOS_Mark_C.dat")).first
        val stepMissionIds = MarkStepDatLoader.loadMissionIds(
            file("File.dat", "Data-ios", "IOS_MarkStep_C.dat")
        )
        val response = mockk<ResponseSender>(relaxed = true)
        val quests = mockk<QuestSystem>(relaxed = true)
        val session = mockk<GameSession>(relaxed = true) {
            every { roleId } returns 31
            every { characterName } returns "MarkerTester"
        }
        val executor = EveResultExecutor(
            response, quests, mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
            mockk(relaxed = true), emptyMap(), markDefs, emptyMap(), emptyMap(),
            mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
            mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
            mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
            stepMissionIds,
        )

        coEvery { quests.addMissionStep(31L, 11120, 1) } returns 0
        assertTrue(
            executor.executeResultClass(
                session,
                EveResult(0, 1, 0, 2, 11120, 1, 1, 0)
            )
        )
        coVerify(exactly = 0) { quests.setFlag(any(), any(), any()) }
    }
    @Test
    fun staleMarkerStepIsBackfilledIntoCompletionFlagBeforeEveEvaluation() = runTest {
        val charId = 31L
        val mapId = 12992
        val markDefs = MarkDatLoader.load(file("File.dat", "Data-ios", "IOS_Mark_C.dat")).first
        val stepMissionIds = MarkStepDatLoader.loadMissionIds(
            file("File.dat", "Data-ios", "IOS_MarkStep_C.dat")
        )

        val quests = mockk<QuestSystem>(relaxed = true)
        coEvery { quests.getMissions(charId) } returns listOf(
            tw.tsm.domain.interfaces.repositories.MissionRecord(11121, 1)
        )
        coEvery { quests.getFlags(charId) } returns emptyList()

        val inventory = mockk<InventorySystem>(relaxed = true)
        every { inventory.getBag(charId) } returns emptyMap()
        every { inventory.getBank(charId) } returns emptyMap()
        every { inventory.getStorage(charId) } returns emptyMap()
        every { inventory.getEquipment(charId) } returns emptyMap()

        val pets = mockk<PetSystem>(relaxed = true)
        every { pets.getFollowPets(charId) } returns emptyMap()
        every { pets.getInnPets(charId) } returns emptyMap()
        every { pets.getCartPets(charId) } returns emptyMap()
        every { pets.getUnlockedFollowCapacity(charId) } returns 4

        val stateManager = mockk<PlayerStateManager>(relaxed = true)
        every { stateManager.getMoral(charId) } returns IntArray(5)
        every { stateManager.getKillNum(charId) } returns 0
        every { stateManager.getState(charId) } returns null

        val questRepo = mockk<tw.tsm.domain.interfaces.repositories.QuestRepository>(relaxed = true)
        coEvery { questRepo.getCompletedEventCounts(charId, mapId) } returns emptyMap()

        val builder = EveStateBuilder(
            questSystem = quests,
            inventorySystem = inventory,
            petSystem = pets,
            stateMachine = EveEventStateMachine(),
            playerStateManager = stateManager,
            markDefs = markDefs,
            roleCountSystem = mockk(relaxed = true),
            skillSystem = mockk(relaxed = true),
            moneySystem = mockk(relaxed = true),
            questRepository = questRepo,
            markStepMissionIds = stepMissionIds,
        )

        val state = builder.buildPlayerState(charId, mapId)

        coVerify(exactly = 1) { quests.setFlag(charId, 588, 1) }
        assertEquals(1, state.missionFlags[11121])
    }

}
