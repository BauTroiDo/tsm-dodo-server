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
import tw.tsm.gamelogic.systems.DvtQuestStepBoundaryPolicy
import tw.tsm.gamelogic.systems.EveMissionPreflightPolicy
import tw.tsm.gamelogic.systems.QuestSystem

/**
 * DVT mission 10120 and 10164: real NPC turn-in requires step2, but repeated
 * post-battle class8 continuations can incorrectly persist a step3.
 */
class DvtTerminalTurnInStepRegressionTest {
    private fun source(path: String): Path =
        listOf(Path.of(path), Path.of("..", path)).first { Files.isRegularFile(it) }

    private fun add(missionId: Int, delta: Int): EveResult =
        EveResult(0, 1, 0, 2, missionId, 1, delta, 0)

    @Test fun originalDvtOnlyHasTwoStagesAndGenuineNpcCompletesStageTwo() {
        val marks = MarkStepDatLoader.loadSteps(Files.readAllBytes(
            source("File.dat/Data-ios/IOS_MarkStep_C.dat")))
        val eve = EveDataLoader.load(Files.readAllBytes(source("gamedata/Eve.emg")))
        assertEquals(setOf(1, 2), marks.getValue(10120).keys)
        assertEquals(setOf(1, 2), marks.getValue(10164).keys)

        val officer = eve.getValue(11301).npcEvents.getValue(3)
            .conditions.single { it.conditionNo == 2 }
        assertEquals(2, officer.conditionValue)
        assertEquals(10120, officer.conditionParameter)
        assertTrue(officer.results.any {
            it.resultType == 0 && it.resultClass == 2 &&
                it.parameter == 10120 && it.parameterStyle == 3
        })

        val altar = eve.getValue(12012).npcEvents.getValue(21)
        val completion = altar.conditions.single { it.conditionNo == 8 }
        assertTrue(completion.results.any {
            it.resultType == 0 && it.resultClass == 2 &&
                it.parameter == 10164 && it.parameterStyle == 3
        })
        assertTrue(altar.conditions.any {
            it.conditionClass == 2 &&
                it.conditionParameter == 10164 &&
                it.conditionValue == 2
        }, "The original altar NPC requires stage2 for the turn-in")
    }

    @Test fun repeatedBattleCannotReachUndefinedStepThreeOrApplyEarlierItemReward() {
        for (missionId in listOf(10120, 10164)) {
            val rejected = EveMissionPreflightPolicy.validate(
                listOf(
                    EveResult(0, 1, 0, 1, 46285, 1, 1, 0),
                    add(missionId, 1),
                    add(10165, 1),
                ),
                mapOf(missionId to 2),
            )
            assertEquals(
                EveMissionPreflightPolicy.Rejection(
                    missionId,
                    EveMissionPreflightPolicy.Failure.UNDEFINED_DVT_QUEST_STEP,
                ),
                rejected,
                "Reject the entire chain before mutating inventory or other missions"
            )
        }
    }

    @Test fun realStagesAndUnrelatedQuestStillAcceptTheirOriginalActions() {
        for (missionId in listOf(10120, 10164)) {
            assertFalse(DvtQuestStepBoundaryPolicy.isUnsupportedAddDestination(missionId, 1))
            assertFalse(DvtQuestStepBoundaryPolicy.isUnsupportedAddDestination(missionId, 2))
            assertTrue(DvtQuestStepBoundaryPolicy.isUnsupportedAddDestination(missionId, 3))
            assertTrue(DvtQuestStepBoundaryPolicy.isUnsupportedAddDestination(missionId, 255))
            assertNull(EveMissionPreflightPolicy.validate(
                listOf(add(missionId, 1)), mapOf(missionId to 1)))
        }
        assertFalse(DvtQuestStepBoundaryPolicy.isUnsupportedAddDestination(10122, 3))
        assertNull(EveMissionPreflightPolicy.validate(
            listOf(add(10122, 1)), mapOf(10122 to 2)))
        assertNull(EveMissionPreflightPolicy.validate(listOf(add(12280, 11)), emptyMap()))
    }

    @Test fun mutationLockForbidsSecondAddEvenAfterEarlierPreflight() = runBlocking {
        val repo = mockk<QuestRepository>(relaxed = true)
        coEvery { repo.getMissions(77L) } returns listOf(
            MissionRecord(10120, 2), MissionRecord(10164, 2))
        val quest = QuestSystem(repo, emptyMap())
        assertNull(quest.addMissionStep(77L, 10120, 1))
        assertNull(quest.addMissionStep(77L, 10164, 1))
        coVerify(exactly = 0) { repo.upsertMission(any(), any(), any()) }
    }
}
