package tw.tsm.tests

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tw.tsm.domain.data.EveCondition
import tw.tsm.gamelogic.systems.EveConditionEvaluator
import tw.tsm.gamelogic.systems.PlayerEventState

class EvePartyConditionRegressionTest {
    private fun cond(
        param: Int,
        pStyle: Int = 0,
        ops: Int,
        value: Int = 0,
        sub: Int = 0,
    ) = EveCondition(
        conditionNo = 1,
        conditionClass = 12,
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
    fun param3DistinguishesSoloFromParty() {
        val solo = PlayerEventState(teamSize = 1)
        val party = PlayerEventState(teamSize = 2)

        assertTrue(EveConditionEvaluator.evaluate(cond(param = 3, ops = 1), solo))
        assertFalse(EveConditionEvaluator.evaluate(cond(param = 3, ops = 0), solo))
        assertFalse(EveConditionEvaluator.evaluate(cond(param = 3, ops = 1), party))
        assertTrue(EveConditionEvaluator.evaluate(cond(param = 3, ops = 0), party))
    }

    @Test
    fun param4RequiresValidatedRomancePair() {
        val valid = PlayerEventState(teamSize = 2, romancePairValid = true)
        val invalid = PlayerEventState(teamSize = 2, romancePairValid = false)

        assertTrue(EveConditionEvaluator.evaluate(cond(param = 4, ops = 5), valid))
        assertFalse(EveConditionEvaluator.evaluate(cond(param = 4, ops = 5), invalid))
    }
}
