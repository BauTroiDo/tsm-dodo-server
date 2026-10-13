package tw.tsm.tests

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import tw.tsm.data.loaders.EveDataLoader
import tw.tsm.domain.data.EveResult
import tw.tsm.gamelogic.systems.*

/** DVT original event recovery after loss, with NO EveCount/quest or reward. */
class DvtBattleDefeatConductRecoveryTest {
    private val scenes by lazy {
        EveDataLoader.load(Files.readAllBytes(Path.of("..", "gamedata", "Eve.emg")))
    }
    private val scene by lazy { scenes.getValue(12861) }
    private val eve by lazy { scene.npcEvents.getValue(6) }
    private val condition by lazy { eve.conditions.single { it.conditionNo == 16 } }
    private val results: List<EveResult> by lazy { condition.results }

    @Test
    fun originalDvtLossHasExactlyTalkAndFiveNpcHidesWithoutQuestOrRewards() {
        assertEquals(8, condition.conditionClass)
        assertEquals(28, condition.conditionParameter)
        assertEquals(2, condition.conditionParameterStyle)
        assertEquals(6, results.size)
        assertEquals(listOf(41353, 0, 0, 0, 0, 0),
            results.map { it.resultMeanNo })
        assertEquals(listOf(9, 9, 10, 11, 12, 13),
            results.map { it.parameter })
        assertTrue(DvtBattleDefeatRecoveryPolicy.isCosmeticDefeat(
            12861, 6, 28, false, results
        ))
        assertTrue(results.none { it.resultClass in listOf(1, 2, 5, 8) })
    }

    @Test
    fun actualEveResolverSelectsDefeatBranchFromFight28Result2() {
        val state = PlayerEventState(
            battleResult = 2, fightDataKey = 28,
        )
        val resolved = EveConditionResolver(scenes).resolveEvent(
            eve, state, 12861,
            chainFilter = { chain -> chain.any { it.conditionClass == 8 } }
        )
        assertNotNull(resolved)
        assertEquals(16, resolved!!.matchedConditionNo)
        assertEquals(results, resolved.results)
        assertTrue(DvtBattleDefeatRecoveryPolicy.isCosmeticDefeat(
            12861, 6, 28, false, resolved.results
        ))
    }

    @Test
    fun winOtherMapWrongFightAndUnexpectedRewardCannotAutoClose() {
        for ((map, event, fight, won) in listOf(
            listOf(12861, 6, 28, 1), listOf(12861, 6, 29, 0),
            listOf(12861, 7, 28, 0), listOf(11101, 6, 28, 0),
        )) {
            assertFalse(DvtBattleDefeatRecoveryPolicy.isCosmeticDefeat(
                map, event, fight, won != 0, results
            ))
        }
        assertFalse(DvtBattleDefeatRecoveryPolicy.isCosmeticDefeat(
            12861, 6, 28, false, results + results.last()
        ))
        assertFalse(DvtBattleDefeatRecoveryPolicy.isCosmeticDefeat(
            12861, 6, 28, false,
            results.toMutableList().also {
                it[1] = it[1].copy(resultClass = 2)
            }
        ))
        assertFalse(DvtBattleDefeatRecoveryPolicy.isCosmeticDefeat(
            12861, 6, 28, false,
            results.toMutableList().also {
                it[3] = it[3].copy(parameter = 99)
            }
        ))
        assertFalse(DvtBattleDefeatRecoveryPolicy.isCosmeticDefeat(
            12861, 6, 28, false,
            results.toMutableList().also {
                it[0] = it[0].copy(resultMeanNo = 0)
            }
        ))
    }

    @Test
    fun defeatStopsNavigationAndProvidesGraceOnlyForOriginalQuest() {
        assertEquals(45, DvtBattleDefeatRecoveryPolicy.DEATH_STOP_NAV_END_KIND)
        assertTrue(DvtBattleDefeatRecoveryPolicy.needsPostDefeatMineGrace(
            12861, 6, 28, false
        ))
        for ((map, eve, fight, win) in listOf(
            listOf(12861, 6, 28, 1),
            listOf(12861, 6, 24, 0),
            listOf(12861, 7, 28, 0),
            listOf(11012, 6, 28, 0),
        )) {
            assertFalse(DvtBattleDefeatRecoveryPolicy.needsPostDefeatMineGrace(
                map, eve, fight, win != 0
            ))
        }
    }

    @Test
    fun originalDefeatTalkThenExactlyFiveTimeHidesAreSequencedWithTokenFence() {
        val stateMachine = EveEventStateMachine()
        val charId = 31L
        val source = EventSession(
            mapId = 12861, eveNo = 6, npcClickId = 5,
            triggerKind = 8, results = emptyList(),
            phase = EventPhase.AwaitingBattle, fightDataKey = 28,
        )
        stateMachine.activate(charId, source)
        stateMachine.replaceResultsAndResume(charId, results, matchedConditionNo = 16)
        assertEquals(source.runToken, stateMachine.getSession(charId)?.runToken)
        assertEquals(16, stateMachine.getSession(charId)?.matchedConditionNo)
        val talk = stateMachine.advance(charId)
        assertEquals(1, talk?.resultType)
        assertEquals(3, talk?.resultClass)
        assertEquals(41353, talk?.resultMeanNo)
        val hides = (0 until 5).map { stateMachine.advance(charId) }
        assertEquals(listOf(9, 10, 11, 12, 13), hides.map { it?.parameter })
        assertTrue(hides.all {
            it?.resultType == 0 && it.resultClass == 3 &&
            it.parameterStyle == 3 && it.resultValue == 2000
        })
        assertNull(stateMachine.advance(charId))

        // Finishing a loss must clear the Eve, not persist its completion.
        assertTrue(stateMachine.clearIfCurrentRun(charId, source.runToken))
        assertNull(stateMachine.getSession(charId))
        assertEquals(120_000L, DvtBattleDefeatRecoveryPolicy.TALK_WATCHDOG_MS)

        // An old watchdog must never close a replacement Eve with a new token.
        val replacement = source.copy(runToken = "new-dvt-attempt")
        stateMachine.activate(charId, replacement)
        assertFalse(stateMachine.clearIfCurrentRun(charId, source.runToken))
        assertEquals(replacement.runToken, stateMachine.getSession(charId)?.runToken)
    }

    @Test
    fun lossAndEscapeAreNotConfusedWithVictoryRewards() {
        val victory = eve.conditions.single { it.conditionNo == 18 }.results
        assertFalse(DvtBattleDefeatRecoveryPolicy.isCosmeticDefeat(
            12861, 6, 28, true, victory
        ))
        assertFalse(DvtBattleDefeatRecoveryPolicy.isCosmeticDefeat(
            12861, 6, 28, false, victory
        ))
        val escape = eve.conditions.single { it.conditionNo == 17 }.results
        assertTrue(DvtBattleDefeatRecoveryPolicy.isCosmeticDefeat(
            12861, 6, 28, false, escape
        ))
    }
}
