package tw.tsm.tests

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.data.loaders.EveDataLoader
import tw.tsm.data.loaders.MarkStepDatLoader
import tw.tsm.domain.data.EveResult
import tw.tsm.domain.interfaces.repositories.MissionRecord
import tw.tsm.domain.interfaces.repositories.QuestRepository
import tw.tsm.gamelogic.systems.DvtNoviceStepGapPolicy
import tw.tsm.gamelogic.systems.EveMissionPreflightPolicy
import tw.tsm.gamelogic.systems.QuestSystem

/**
 * DVT mission12280 has two disjoint valid tutorial branches: 1..5 and 11..15.
 * Never create an unrouteable step6..10, even if a repeated Eve packet races
 * the preflight. Existing legacy step6 rows require independent investigation.
 */
class DvtNoviceStepGapRegressionTest {
    private fun add(mission: Int, value: Int): EveResult =
        EveResult(0, 1, 0, 2, mission, 1, value, 0)

    private fun source(path: String): Path =
        listOf(Path.of(path), Path.of("..", path)).first { Files.isRegularFile(it) }

    @Test fun sourceDvtHasNoIntermediateMarkStepAndProvidesBothRealRoutes() {
        val marks = MarkStepDatLoader.loadSteps(Files.readAllBytes(
            source("File.dat/Data-ios/IOS_MarkStep_C.dat")))
        assertEquals((1..5).toSet() + (11..15).toSet(), marks.getValue(12280).keys)

        val eve = EveDataLoader.load(Files.readAllBytes(source("gamedata/Eve.emg")))
        val complete = eve.getValue(10851).npcEvents.getValue(4)
            .conditions.single { it.conditionNo == 7 }
        assertTrue(complete.results.any {
            it.resultType == 0 && it.resultClass == 2 &&
                it.parameter == 12280 && it.parameterStyle == 3
        }, "Original DVT completes first starter branch directly at step5")

        val startSecond = eve.getValue(10821).npcEvents.getValue(1)
            .conditions.single { it.conditionNo == 1 }
        assertTrue(startSecond.results.any {
            it.resultType == 0 && it.resultClass == 2 &&
                it.parameter == 12280 && it.parameterStyle == 1 &&
                it.resultValue == 11
        }, "Alternate DVT branch begins at step11 when mission is absent")
    }

    @Test fun fullChainIsRejectedBeforeAnyEffectsOnUndefinedNoviceStep() {
        for (current in 5..9) {
            val rejected = EveMissionPreflightPolicy.validate(
                listOf(
                    EveResult(0, 1, 0, 1, 30002, 1, 1, 0),
                    add(12280, 1),
                    add(12281, 1),
                ),
                mapOf(12280 to current),
            )
            assertEquals(
                EveMissionPreflightPolicy.Rejection(
                    12280, EveMissionPreflightPolicy.Failure.UNDEFINED_DVT_TUTORIAL_STEP,
                ),
                rejected,
                "Stage ${current + 1} must never be created by any stale Eve choice",
            )
        }
    }

    @Test fun bothRealDvtRoutesAndOtherMissionsRemainPermitted() {
        assertFalse(DvtNoviceStepGapPolicy.isUnsupportedAddDestination(12280, 5))
        assertTrue(DvtNoviceStepGapPolicy.isUnsupportedAddDestination(12280, 6))
        assertTrue(DvtNoviceStepGapPolicy.isUnsupportedAddDestination(12280, 10))
        assertFalse(DvtNoviceStepGapPolicy.isUnsupportedAddDestination(12280, 11))
        assertFalse(DvtNoviceStepGapPolicy.isUnsupportedAddDestination(10120, 6))
        assertNull(EveMissionPreflightPolicy.validate(listOf(add(12280, 1)), mapOf(12280 to 4)))
        assertNull(EveMissionPreflightPolicy.validate(listOf(add(12280, 11)), emptyMap()))
        assertNull(EveMissionPreflightPolicy.validate(listOf(add(12280, 1)), mapOf(12280 to 11)))
        assertNull(EveMissionPreflightPolicy.validate(listOf(add(12280, 1)), mapOf(12280 to 14)))
        assertNull(EveMissionPreflightPolicy.validate(listOf(add(10122, 1)), mapOf(10122 to 5)))
    }

    @Test fun atomicAddRefusesStaleReplayEvenIfPreflightSawStep4() = runBlocking {
        val repository = mockk<QuestRepository>(relaxed = true)
        coEvery { repository.getMissions(21L) } returns listOf(MissionRecord(12280, 5))
        val quest = QuestSystem(repository, emptyMap())
        assertNull(quest.addMissionStep(21L, 12280, 1))
        coVerify(exactly = 0) { repository.upsertMission(any(), any(), any()) }
        coVerify(exactly = 1) { repository.getMissions(21L) }
    }

    @Test fun legitimateStepAndAlternateStep11CanBePersisted() = runBlocking {
        val repository = mockk<QuestRepository>(relaxed = true)
        coEvery { repository.getMissions(22L) } returns emptyList()
        coEvery { repository.getMissions(23L) } returns listOf(MissionRecord(12280, 4))
        val quest = QuestSystem(repository, emptyMap())
        assertEquals(0, quest.addMissionStep(22L, 12280, 11))
        assertEquals(4, quest.addMissionStep(23L, 12280, 1))
        coVerify(exactly = 1) { repository.upsertMission(22L, 12280, 11) }
        coVerify(exactly = 1) { repository.upsertMission(23L, 12280, 5) }
    }
}
