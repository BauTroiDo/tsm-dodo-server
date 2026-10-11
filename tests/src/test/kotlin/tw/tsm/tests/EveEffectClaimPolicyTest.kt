package tw.tsm.tests

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.domain.data.EveResult
import tw.tsm.gamelogic.systems.EveEffectClaimPolicy
import tw.tsm.gamelogic.systems.EveEventStateMachine
import tw.tsm.gamelogic.systems.EventSession

class EveEffectClaimPolicyTest {
    private fun effect(type: Int, cls: Int, style: Int = 1, value: Int = 1) =
        EveResult(0, 1, type, cls, 11804, style, value, 0)

    @Test fun rewardMutationsRequireReservation() {
        for (cls in listOf(1, 2, 4, 5, 8, 14)) {
            assertTrue(EveEffectClaimPolicy.requiresReservation(effect(0, cls)), "class=$cls")
        }
    }

    @Test fun talkChoiceWarpAndNonRewardEffectsAreNotReserved() {
        for (type in listOf(1, 2, 3, 4, 5, 6)) {
            assertFalse(EveEffectClaimPolicy.requiresReservation(effect(type, 2)), "type=$type")
        }
        assertFalse(EveEffectClaimPolicy.requiresReservation(effect(0, 0)))
        assertFalse(EveEffectClaimPolicy.requiresReservation(effect(0, 3)))
    }

    @Test fun eventAttemptTokenSurvivesCopiesButNewAttemptGetsNewToken() {
        val first = EventSession(12001, 5, 12, results = listOf(effect(0, 2)))
        assertEquals(first.runToken, first.copy(currentIndex = 1).runToken)
        assertFalse(first.effectClaimed)
        val machine = EveEventStateMachine()
        machine.activate(31L, first)
        assertTrue(machine.markEffectClaimed(31L, first.runToken))
        assertTrue(machine.getSession(31L)?.effectClaimed == true)
        val replacement = EventSession(12001, 5, 12, results = emptyList())
        assertNotEquals(first.runToken, replacement.runToken)
        machine.activate(31L, replacement)
        assertFalse(machine.clearIfCurrentRun(31L, first.runToken))
        assertEquals(replacement.runToken, machine.getSession(31L)?.runToken)
    }
}
