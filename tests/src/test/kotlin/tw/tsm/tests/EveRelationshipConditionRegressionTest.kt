package tw.tsm.tests

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tw.tsm.domain.data.EveCondition
import tw.tsm.gamelogic.systems.EveConditionEvaluator
import tw.tsm.gamelogic.systems.PlayerEventState

class EveRelationshipConditionRegressionTest {
    private fun cond(
        param: Int,
        pStyle: Int,
        ops: Int,
        value: Int = 0,
        sub: Int = 0,
    ) = EveCondition(
        conditionNo = 1,
        conditionClass = 7,
        conditionParameter = param,
        conditionParameterStyle = pStyle,
        conditionOps = ops,
        conditionValue = value,
        conditionSubItem = sub,
        toResult = 0,
        andNum = 0,
        results = emptyList(),
    )

    @Test
    fun offlineSingleStateDoesNotUnlockMarriagePrivileges() {
        val state = PlayerEventState()

        // pStyle2/status None: branch "chưa kết hôn / tình yêu chưa đủ" vẫn hoạt động.
        assertTrue(EveConditionEvaluator.evaluate(cond(15, 2, 0, 0), state))
        assertTrue(EveConditionEvaluator.evaluate(cond(15, 2, 1, 1), state))

        // pStyle1 = spouse requirement: offline single không được qua.
        assertFalse(EveConditionEvaluator.evaluate(cond(15, 1, 0, 0), state))
    }

    @Test
    fun spouseDurationUsesEveNumericComparator() {
        val fiveMonths = PlayerEventState(affectionStatus = 2, marriageMonths = 5)
        val sixMonths = PlayerEventState(affectionStatus = 2, marriageMonths = 6)

        assertTrue(EveConditionEvaluator.evaluate(cond(15, 1, 0, 0), fiveMonths))
        assertTrue(EveConditionEvaluator.evaluate(cond(15, 3, 1, 6), fiveMonths))
        assertFalse(EveConditionEvaluator.evaluate(cond(15, 3, 2, 5), fiveMonths))

        assertFalse(EveConditionEvaluator.evaluate(cond(15, 3, 1, 6), sixMonths))
        assertTrue(EveConditionEvaluator.evaluate(cond(15, 3, 2, 5), sixMonths))
    }

    @Test
    fun childConditionDefaultsToNoChild() {
        val none = PlayerEventState()
        val hasSlot1 = PlayerEventState(childSlots = setOf(1))

        assertTrue(EveConditionEvaluator.evaluate(cond(18, 2, 0), none))
        assertFalse(EveConditionEvaluator.evaluate(cond(18, 1, 0, sub = 1), none))

        assertFalse(EveConditionEvaluator.evaluate(cond(18, 2, 0), hasSlot1))
        assertTrue(EveConditionEvaluator.evaluate(cond(18, 1, 0, sub = 1), hasSlot1))
    }
}
