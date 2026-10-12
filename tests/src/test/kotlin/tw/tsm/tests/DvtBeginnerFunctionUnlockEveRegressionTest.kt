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
import tw.tsm.domain.interfaces.repositories.MissionFlagRecord
import tw.tsm.gamelogic.systems.*
import java.nio.file.Files
import java.nio.file.Path

/**
 * Original DVT Eve10851/10853 emits ADD_STEP (class2, pStyle1, +1) for four
 * beginner UI markers. Existing server executor must send their original
 * Mark_C bits as S024-005 immediately, not only reconcile them at relog.
 * Eve source ownership/conditions are independently locked by Python audit.
 */
class DvtBeginnerFunctionUnlockEveRegressionTest {
    private fun file(vararg parts: String): ByteArray {
        val direct = Path.of(parts.first(), *parts.drop(1).toTypedArray())
        return Files.readAllBytes(if (Files.exists(direct)) direct else Path.of("..", *parts))
    }

    @Test
    fun firstLegitimateSourceMarkerResultSendsItsExactDvtBit() = runTest {
        val marks = MarkDatLoader.load(file("File.dat", "Data-ios", "IOS_Mark_C.dat")).first
        val realSteps = MarkStepDatLoader.loadMissionIds(
            file("File.dat", "Data-ios", "IOS_MarkStep_C.dat")
        )
        val grants = mapOf(11804 to 936, 12283 to 1375, 12285 to 1376, 12396 to 1434)
        for ((missionId, bitId) in grants) {
            assertEquals(bitId, marks[missionId]?.bitId)
            assertFalse(missionId in realSteps)
            val characterId = 41L
            val response = mockk<ResponseSender>(relaxed = true)
            val quests = mockk<QuestSystem>(relaxed = true)
            val session = mockk<GameSession>(relaxed = true) {
                every { roleId } returns characterId.toInt()
                every { characterName } returns "OriginalDvtMarker"
            }
            val executor = EveResultExecutor(
                response, quests, mockk(relaxed = true), mockk(relaxed = true),
                mockk(relaxed = true), mockk(relaxed = true), emptyMap(),
                marks, emptyMap(), emptyMap(),
                mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
                mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
                mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
                realSteps,
            )
            coEvery { quests.addMissionStep(characterId, missionId, 1) } returns 0
            assertTrue(executor.executeResultClass(
                session, EveResult(0, 1, 0, 2, missionId, 1, 1, 0)
            ))
            coVerify(exactly = 1) { quests.addMissionStep(characterId, missionId, 1) }
            coVerify(exactly = 1) { quests.setFlag(characterId, bitId, 1) }
            verify(exactly = 1) {
                response.sendSetMissionFlags(
                    session, match { it == listOf(MissionFlagRecord(bitId, 1)) }
                )
            }
        }
    }

    @Test
    fun genuineQuestStepDoesNotPrematurelyGrantItsMarkBit() = runTest {
        val marks = MarkDatLoader.load(file("File.dat", "Data-ios", "IOS_Mark_C.dat")).first
        val realSteps = MarkStepDatLoader.loadMissionIds(
            file("File.dat", "Data-ios", "IOS_MarkStep_C.dat")
        )
        assertTrue(11120 in realSteps)
        val response = mockk<ResponseSender>(relaxed = true)
        val quests = mockk<QuestSystem>(relaxed = true)
        val session = mockk<GameSession>(relaxed = true) {
            every { roleId } returns 41
            every { characterName } returns "OriginalDvtMarker"
        }
        val executor = EveResultExecutor(
            response, quests, mockk(relaxed = true), mockk(relaxed = true),
            mockk(relaxed = true), mockk(relaxed = true), emptyMap(),
            marks, emptyMap(), emptyMap(),
            mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
            mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
            mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
            realSteps,
        )
        coEvery { quests.addMissionStep(41L, 11120, 1) } returns 0
        assertTrue(executor.executeResultClass(
            session, EveResult(0, 1, 0, 2, 11120, 1, 1, 0)
        ))
        coVerify(exactly = 0) { quests.setFlag(any(), any(), any()) }
        verify(exactly = 0) { response.sendSetMissionFlags(any(), any()) }
    }
}
