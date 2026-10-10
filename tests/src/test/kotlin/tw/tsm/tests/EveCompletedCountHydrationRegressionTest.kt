package tw.tsm.tests

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tw.tsm.domain.data.EveCondition
import tw.tsm.gamelogic.systems.EveConditionEvaluator
import tw.tsm.gamelogic.systems.EveEventStateMachine
import tw.tsm.gamelogic.systems.EventSession
import tw.tsm.gamelogic.systems.PlayerEventState

class EveCompletedCountHydrationRegressionTest {
    private fun complete(engine: EveEventStateMachine, charId: Long, mapId: Int, eveNo: Int) {
        engine.activate(charId, EventSession(mapId, eveNo, 1, results = emptyList()))
        engine.complete(charId)
    }

    private fun countAtLeastThree() = EveCondition(
        conditionNo = 1, conditionClass = 12, conditionParameter = 0,
        conditionParameterStyle = 1, conditionOps = 5, conditionValue = 3,
        conditionSubItem = 0, toResult = 0, andNum = 1, results = emptyList()
    )

    @Test fun persistedCountAheadOfRamRestoresClass12Branch() {
        val engine = EveEventStateMachine()
        complete(engine, 41L, 10821, 3)
        val condition = countAtLeastThree()
        assertFalse(EveConditionEvaluator.evaluate(
            condition, PlayerEventState(currentEveNo = 3, completedEveCounts = engine.getCompletedEveCounts(41L, 10821))
        ))
        engine.prePopulateCompleted(41L, 10821, mapOf(3 to 4))
        assertEquals(4, engine.getCompletedEveCounts(41L, 10821)[3])
        assertTrue(EveConditionEvaluator.evaluate(
            condition, PlayerEventState(currentEveNo = 3, completedEveCounts = engine.getCompletedEveCounts(41L, 10821))
        ))
    }

    @Test fun olderDatabaseSnapshotNeverRollsBackLiveCount() {
        val engine = EveEventStateMachine()
        engine.prePopulateCompleted(41L, 10821, mapOf(3 to 4))
        complete(engine, 41L, 10821, 3)
        assertEquals(5, engine.getCompletedEveCounts(41L, 10821)[3])
        engine.prePopulateCompleted(41L, 10821, mapOf(3 to 1))
        assertEquals(5, engine.getCompletedEveCounts(41L, 10821)[3])
    }

    @Test fun repeatedHydrationIsIdempotentAndIsolatedByCharacterAndMap() {
        val engine = EveEventStateMachine()
        repeat(3) { engine.prePopulateCompleted(41L, 10821, mapOf(3 to 2, 5 to 6)) }
        engine.prePopulateCompleted(41L, 10853, mapOf(3 to 7))
        engine.prePopulateCompleted(42L, 10821, mapOf(3 to 9))
        assertEquals(mapOf(3 to 2, 5 to 6), engine.getCompletedEveCounts(41L, 10821))
        assertEquals(mapOf(3 to 7), engine.getCompletedEveCounts(41L, 10853))
        assertEquals(mapOf(3 to 9), engine.getCompletedEveCounts(42L, 10821))
    }

    @Test fun explicitSceneResetCanLoadLowerNewBaseline() {
        val engine = EveEventStateMachine()
        engine.prePopulateCompleted(41L, 10821, mapOf(3 to 6))
        engine.clearSceneEvents(41L, 10821)
        engine.prePopulateCompleted(41L, 10821, mapOf(3 to 1))
        assertEquals(1, engine.getCompletedEveCounts(41L, 10821)[3])
    }

    @Test fun invalidCountOrFallbackEventCannotCreatePhantomCompletion() {
        val engine = EveEventStateMachine()
        engine.prePopulateCompleted(41L, 10821, mapOf(0 to 8, 3 to 0, 4 to -2, 5 to 2))
        assertEquals(mapOf(5 to 2), engine.getCompletedEveCounts(41L, 10821))
    }
}
