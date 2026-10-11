package tw.tsm.tests

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.domain.constants.EWar
import tw.tsm.domain.data.EveResult
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.domain.interfaces.repositories.EveEffectClaimRepository
import tw.tsm.gamelogic.systems.*

class EveBattleRewardFenceRegressionTest {
    @Test fun onlyPayingEventWinsMustReserveBeforeBattlePayout() {
        assertTrue(EveBattleRewardFencePolicy.needsReservation(EWar.EVENT, true, false))
        assertFalse(EveBattleRewardFencePolicy.needsReservation(EWar.EVENT, false, false))
        assertFalse(EveBattleRewardFencePolicy.needsReservation(EWar.EVENT, true, true))
        assertFalse(EveBattleRewardFencePolicy.needsReservation(EWar.PK_PLAYERS, true, false))
    }

    private fun newFlow(machine: EveEventStateMachine, claims: EveEffectClaimRepository) =
        EveFlowController(
            mockk<ResponseSender>(relaxed = true), machine,
            mockk(relaxed = true), mockk(relaxed = true),
            mockk(relaxed = true), mockk(relaxed = true),
            mockk(relaxed = true), mockk(relaxed = true),
            mockk(relaxed = true), mockk(relaxed = true),
            emptyMap(), mockk(relaxed = true), claims
        )

    @Test fun battleCallbackReservesOncePerEveRunBeforeLoot() = runTest {
        val machine = EveEventStateMachine()
        val effect = EveResult(0, 1, 3, 0, 0, 0, 1, 123)
        val event = EventSession(12001, 5, 12, results = listOf(effect))
        machine.activate(31L, event)
        val claim = mockk<EveEffectClaimRepository>()
        coEvery { claim.reserve(31L, 12001, 5, event.runToken, any()) } returns true
        val game = mockk<GameSession>(relaxed = true) {
            io.mockk.every { roleId } returns 31
        }

        val flow = newFlow(machine, claim)
        assertTrue(flow.reserveBeforeEventBattleRewards(game))
        assertTrue(flow.reserveBeforeEventBattleRewards(game))
        coVerify(exactly = 1) { claim.reserve(31L, 12001, 5, event.runToken, any()) }
        assertTrue(machine.getSession(31L)?.effectClaimed == true)
    }

    @Test fun rejectedBattleReservationCannotIssueGenericPayout() = runTest {
        val machine = EveEventStateMachine()
        val event = EventSession(12001, 5, 12, results = emptyList())
        machine.activate(31L, event)
        val claim = mockk<EveEffectClaimRepository>()
        coEvery { claim.reserve(31L, 12001, 5, event.runToken, any()) } returns false
        val game = mockk<GameSession>(relaxed = true) {
            io.mockk.every { roleId } returns 31
        }

        assertFalse(newFlow(machine, claim).reserveBeforeEventBattleRewards(game))
        assertNull(machine.getSession(31L))
        coVerify(exactly = 1) { claim.reserve(31L, 12001, 5, event.runToken, any()) }
    }
}
