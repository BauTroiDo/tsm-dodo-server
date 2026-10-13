package tw.tsm.tests

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.data.loaders.EveDataLoader
import tw.tsm.domain.data.EveCondition
import tw.tsm.gamelogic.systems.EveChainResolver
import tw.tsm.gamelogic.systems.EveConditionEvaluator
import tw.tsm.gamelogic.systems.PlayerEventState

/**
 * Live DEV log 2026-10-13: CaoYenDao has mission 10074 step 1 and a single
 * item 30002. Kotlin previously tested Eve item ops4/val1 as 1 > 1 (false),
 * so NPC 11002 / scene 11012 / Eve 8 fell back to generic talk 12100.
 * All records below are sourced from the exact DVT gamedata/Eve.emg.
 */
class DvtKongRongGiftTurnInRegressionTest {
    private val scenes by lazy {
        EveDataLoader.load(Files.readAllBytes(Path.of("..", "gamedata", "Eve.emg")))
    }

    private val event by lazy { scenes.getValue(11012).npcEvents.getValue(8) }
    private val step by lazy { event.conditions.single { it.conditionNo == 16 } }
    private val giftRequirement by lazy { event.conditions.single { it.conditionNo == 17 } }

    @Test
    fun giverAwardsExactlyOneGiftAndReceiverChecksInclusiveOne() {
        val giver = scenes.getValue(11101).npcEvents.getValue(2)
        val gifts = giver.conditions.flatMap { it.results }.filter {
            it.resultClass == 1 && it.parameter == 30002 &&
                it.resultValue == 1 && it.parameterStyle == 1
        }
        assertTrue(gifts.isNotEmpty(), "DVT giver must award one copy of item 30002")

        val khongDung = scenes.getValue(11012).npcs.getValue(1)
        assertEquals(11002, khongDung.npcId)
        assertTrue(8 in khongDung.events)
        assertEquals(2, step.conditionClass)
        assertEquals(10074, step.conditionParameter)
        assertEquals(1, step.conditionParameterStyle)
        assertEquals(1, step.conditionValue)
        assertEquals(1, giftRequirement.conditionClass)
        assertEquals(30002, giftRequirement.conditionParameter)
        assertEquals(1, giftRequirement.conditionParameterStyle)
        assertEquals(4, giftRequirement.conditionOps)
        assertEquals(1, giftRequirement.conditionValue)

        val consumedGift = step.results.single {
            it.resultClass == 1 && it.parameter == 30002
        }
        assertEquals(-1, consumedGift.resultValue)
        val nextStep = step.results.single {
            it.resultClass == 2 && it.parameter == 10074
        }
        assertEquals(1, nextStep.parameterStyle)
        assertEquals(1, nextStep.resultValue)
    }

    @Test
    fun exactOneGiftMatchesOriginalStepAndSelectsNineResultDeliveryNotFallbackTalk() {
        val state = PlayerEventState(
            missionSteps = mapOf(10074 to 1),
            bagItems = mapOf(30002 to 1),
        )
        assertTrue(EveConditionEvaluator.evaluate(step, state))
        assertTrue(EveConditionEvaluator.evaluate(giftRequirement, state))

        val matched = EveChainResolver.resolve(
            event.conditions,
            evaluator = { cond -> EveConditionEvaluator.evaluate(cond, state) },
        )
        assertNotNull(matched, "one gifted item must unlock Khong Dung delivery")
        assertEquals(16, matched!!.matchedConditionNo)
        assertEquals(9, matched.results.size)
        assertEquals(1, matched.results.count {
            it.resultClass == 2 && it.parameter == 10074 && it.resultValue == 1
        })
    }

    @Test
    fun missingGiftCannotAdvanceMissionOrConsumeItems() {
        val state = PlayerEventState(
            missionSteps = mapOf(10074 to 1),
            bagItems = emptyMap(),
        )
        assertFalse(EveConditionEvaluator.evaluate(giftRequirement, state))
        val matched = EveChainResolver.resolve(event.conditions, evaluator = {
            EveConditionEvaluator.evaluate(it, state)
        })
        assertNull(matched, "missing item must not cause quest step mutation")
    }

    @Test
    fun unrelatedInventoryThresholdAndStrictGreaterOperatorsStayDistinct() {
        val ops4 = giftRequirement.copy(conditionParameter = 901, conditionValue = 5)
        val strictGreater = ops4.copy(conditionOps = 2)
        for (qty in 0..7) {
            val state = PlayerEventState(bagItems = mapOf(901 to qty))
            assertEquals(qty >= 5, EveConditionEvaluator.evaluate(ops4, state))
            assertEquals(qty > 5, EveConditionEvaluator.evaluate(strictGreater, state))
        }
        val absent = giftRequirement.copy(conditionParameter = 999)
        assertFalse(EveConditionEvaluator.evaluate(absent, PlayerEventState()))
    }

    @Test
    fun unrelatedMissionAndDungeonNumericComparisonsAreNotRelaxed() {
        assertFalse(EveConditionEvaluator.compareStep(1, 4, 1))
        assertTrue(EveConditionEvaluator.compareStep(2, 4, 1))
        val mission = EveCondition(
            conditionNo = 1, conditionClass = 2, conditionParameter = 10074,
            conditionParameterStyle = 1, conditionOps = 5, conditionValue = 1,
            conditionSubItem = 0, toResult = 0, andNum = 1, results = emptyList(),
        )
        assertTrue(EveConditionEvaluator.evaluate(mission,
            PlayerEventState(missionSteps = mapOf(10074 to 1))))
        assertFalse(EveConditionEvaluator.evaluate(mission,
            PlayerEventState(missionSteps = mapOf(10074 to 2))))
    }
}
