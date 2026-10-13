package tw.tsm.tests

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.data.loaders.EveDataLoader
import tw.tsm.gamelogic.systems.EveConditionEvaluator
import tw.tsm.gamelogic.systems.PlayerEventState

/** Tests against the exact Eve.emg actively used by DVT staging / DEV. */
class DvtEveClass14TimeWindowRegressionTest {
    private val scenes by lazy {
        EveDataLoader.load(Files.readAllBytes(Path.of("..", "gamedata", "Eve.emg")))
    }

    @Test fun timeValueIsPreservedForAllRealClass14Conditions() {
        val conditions = scenes.values.flatMap { scene ->
            scene.npcEvents.values.flatMap { event ->
                event.conditions.filter { it.conditionClass == 14 }
            }
        }
        assertEquals(279, conditions.size, "Updated authoritative Eve data changed: re-audit shapes")
        assertTrue(conditions.all { it.conditionTimeValue.isFinite() && it.conditionTimeValue > 0.0 })
        val param0 = conditions.filter {
            it.conditionParameter == 0 && it.conditionParameterStyle == 0 &&
                it.conditionValue == 0 && it.conditionSubItem == 0
        }
        assertEquals(274, param0.size)
        assertEquals(mapOf(1 to 72, 2 to 178, 3 to 13, 4 to 11),
            param0.groupingBy { it.conditionOps }.eachCount())
        val unknownParam2 = conditions - param0.toSet()
        assertEquals(5, unknownParam2.size)
        assertTrue(unknownParam2.all { it.conditionParameter == 2 && it.conditionOps == 0 })
    }

    @Test fun event10852UsesOpenDateRangeInsteadOfActivityEnabledFlag() {
        val event = scenes.getValue(10852).npcEvents.getValue(8)
        val start = event.conditions.single { it.conditionNo == 2 }
        val end = event.conditions.single { it.conditionNo == 3 }
        assertEquals(14, start.conditionClass)
        assertEquals(14, end.conditionClass)
        assertEquals(2, start.conditionOps) // greater than start
        assertEquals(1, end.conditionOps)   // less than end
        assertTrue(start.conditionTimeValue < end.conditionTimeValue)
        val midway = (start.conditionTimeValue + end.conditionTimeValue) / 2.0
        assertTrue(EveConditionEvaluator.evaluate(start, PlayerEventState(eveNowOaDate = midway)))
        assertTrue(EveConditionEvaluator.evaluate(end, PlayerEventState(eveNowOaDate = midway)))
        assertFalse(EveConditionEvaluator.evaluate(start, PlayerEventState(eveNowOaDate = start.conditionTimeValue)))
        assertFalse(EveConditionEvaluator.evaluate(end, PlayerEventState(eveNowOaDate = end.conditionTimeValue)))
        assertFalse(EveConditionEvaluator.evaluate(end, PlayerEventState(eveNowOaDate = end.conditionTimeValue + 1.0)))
    }

    @Test fun event11411SupportsInclusiveDateBoundsAndParam2RemainsClosed() {
        val event = scenes.getValue(11411).npcEvents.getValue(4)
        val lower = event.conditions.single { it.conditionNo == 2 }
        val upper = event.conditions.single { it.conditionNo == 3 }
        assertEquals(4, lower.conditionOps)
        assertEquals(3, upper.conditionOps)
        assertTrue(EveConditionEvaluator.evaluate(lower,
            PlayerEventState(eveNowOaDate = lower.conditionTimeValue)))
        assertTrue(EveConditionEvaluator.evaluate(upper,
            PlayerEventState(eveNowOaDate = upper.conditionTimeValue)))
        assertFalse(EveConditionEvaluator.evaluate(lower,
            PlayerEventState(eveNowOaDate = lower.conditionTimeValue - 1.0)))
        assertFalse(EveConditionEvaluator.evaluate(upper,
            PlayerEventState(eveNowOaDate = upper.conditionTimeValue + 1.0)))

        val unsupported = scenes.getValue(12001).npcEvents.getValue(64)
            .conditions.filter { it.conditionClass == 14 && it.conditionParameter == 2 }
        assertEquals(5, unsupported.size)
        for (cond in unsupported) {
            assertFalse(EveConditionEvaluator.evaluate(
                cond, PlayerEventState(eveNowOaDate = cond.conditionTimeValue)))
        }
    }
}
