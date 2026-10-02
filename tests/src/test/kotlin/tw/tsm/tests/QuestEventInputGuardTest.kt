package tw.tsm.tests

import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.domain.interfaces.*
import tw.tsm.gamelogic.systems.*

class QuestEventInputGuardTest {
    private fun event(phase: EventPhase) = EventSession(12001, 1, 1,
        results=emptyList(), phase=phase, lastSurfaceId=7)

    @Test fun choiceIsClaimedOnceAndRetainsSurfaceContext() {
        val state = EveEventStateMachine(); state.activate(31, event(EventPhase.AwaitingChoice))
        val first = state.claimChoice(31, 30)
        assertEquals(30, first?.lastChoiceCode); assertEquals(7, first?.lastSurfaceId)
        assertEquals(EventPhase.Executing, first?.phase)
        assertTrue(first?.choiceResolutionInProgress == true)
        assertNull(state.claimChoice(31, 40))
        assertEquals(30, state.getSession(31)?.lastChoiceCode)
    }

    @Test fun nextPacketDuringChoiceResolutionDoesNotCompleteEvent() = runTest {
        val state = EveEventStateMachine()
        state.activate(31, event(EventPhase.AwaitingChoice))
        assertNotNull(state.claimChoice(31, 32))
        assertTrue(state.getSession(31)?.choiceResolutionInProgress == true)

        val response = mockk<ResponseSender>(relaxed = true)
        val session = mockk<GameSession>(relaxed = true) { every { roleId } returns 31 }
        val flow = EveFlowController(
            response, state, mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
            mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
            mockk(relaxed = true), emptyMap(), mockk(relaxed = true)
        )

        flow.advanceEvent(session)

        assertNotNull(state.getSession(31))
        assertTrue(state.getSession(31)?.choiceResolutionInProgress == true)
        verify(exactly = 0) { response.sendEventEnd(any()) }

        state.finishChoiceResolution(31)
        assertFalse(state.getSession(31)?.choiceResolutionInProgress == true)
    }

    @Test fun simultaneousChoicePacketsHaveOnlyOneWinner() = runTest {
        val state = EveEventStateMachine(); state.activate(31, event(EventPhase.AwaitingChoice))
        val claims = coroutineScope { (1..20).map { async(Dispatchers.Default) { state.claimChoice(31, it) } }.awaitAll() }
        assertEquals(1, claims.count { it != null })
        assertEquals(claims.first { it != null }?.lastChoiceCode, state.getSession(31)?.lastChoiceCode)
    }

    @Test fun flowUsesClaimedChoiceContextEvenIfBuilderSnapshotIsStale() = runTest {
        val stateMachine = EveEventStateMachine()
        stateMachine.activate(31, event(EventPhase.AwaitingChoice).copy(lastSurfaceId = 2, eveNo = 1))

        val response = mockk<ResponseSender>(relaxed = true)
        val builder = mockk<EveStateBuilder>()
        coEvery { builder.buildPlayerState(31, 12001, any()) } returns PlayerEventState(
            lastSurfaceId = -1,
            lastChoiceCode = -1,
        )

        val capturedState = slot<PlayerEventState>()
        val resolver = mockk<EveConditionResolver>()
        every {
            resolver.resolveEvent(any(), capture(capturedState), 12001, any())
        } returns null

        val scene = mockk<tw.tsm.domain.data.SceneEveData>()
        every { scene.npcEvents } returns mapOf(
            1 to tw.tsm.domain.data.NpcEventData(1, emptyList(), emptyList())
        )

        val session = mockk<GameSession>(relaxed = true) {
            every { roleId } returns 31
            every { characterName } returns "choice-race"
        }
        val flow = EveFlowController(
            response, stateMachine, builder, resolver, mockk(relaxed = true),
            mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
            mockk(relaxed = true), mapOf(12001 to scene), mockk(relaxed = true)
        )

        flow.chooseEvent(session, 32)

        assertEquals(2, capturedState.captured.lastSurfaceId)
        assertEquals(32, capturedState.captured.lastChoiceCode)
    }
    @Test fun battleCallbackIsClaimedOnceWithoutOverwritingWinningResult() {
        val state = EveEventStateMachine(); state.activate(31, event(EventPhase.AwaitingBattle).copy(fightDataKey = 174))
        val claimed = state.claimBattleResult(31, 1)
        assertEquals(1, claimed?.battleResult)
        assertEquals(174, claimed?.fightDataKey)
        assertNull(state.claimBattleResult(31, 2))
        assertEquals(1, state.getSession(31)?.battleResult)
        assertEquals(174, state.getSession(31)?.fightDataKey)
    }

    @Test fun enteringBattleStoresFightDataKeyForPostBattleClass8Routing() {
        val state = EveEventStateMachine(); state.activate(31, event(EventPhase.Executing))
        state.enterAwaitingBattle(31, 174)
        assertEquals(EventPhase.AwaitingBattle, state.getSession(31)?.phase)
        assertEquals(174, state.getSession(31)?.fightDataKey)
    }
    @Test fun choiceAndBattleInputsCannotClaimEachOthersPhase() {
        val state = EveEventStateMachine(); state.activate(31, event(EventPhase.AwaitingBattle))
        assertNull(state.claimChoice(31, 30))
        state.activate(31, event(EventPhase.AwaitingChoice))
        assertNull(state.claimBattleResult(31, 1))
    }
    @Test fun nextPacketDuringDoorWarpDoesNotCompleteEvent() = runTest {
        val state = EveEventStateMachine(); state.activate(31, event(EventPhase.AwaitingWarp))
        val response = mockk<ResponseSender>(relaxed=true)
        val builder = mockk<EveStateBuilder>(relaxed=true)
        val executor = mockk<EveResultExecutor>(relaxed=true)
        val session = mockk<GameSession>(relaxed=true) { every { roleId } returns 31 }
        val flow = EveFlowController(response, state, builder, mockk(relaxed=true), executor,
            mockk(relaxed=true), mockk(relaxed=true), mockk(relaxed=true), mockk(relaxed=true),
            mockk(relaxed=true), emptyMap(), mockk(relaxed=true))

        flow.advanceEvent(session)

        assertEquals(EventPhase.AwaitingWarp, state.getSession(31)?.phase)
        verify(exactly=0) { response.sendEventEnd(any()) }
        coVerify(exactly=0) { builder.buildPlayerState(any(), any(), any()) }
        coVerify(exactly=0) { executor.executeResultClass(any(), any()) }
    }

    @Test fun backgroundEventCannotOverwriteActiveQuestSession() {
        val state = EveEventStateMachine()
        val quest = event(EventPhase.Executing).copy(eveNo = 6)
        val ambient = event(EventPhase.Executing).copy(eveNo = 7, triggerKind = 7)
        state.activate(31, quest)

        assertFalse(state.tryActivateIfIdle(31, ambient))
        assertEquals(6, state.getSession(31)?.eveNo)
        assertEquals(0, state.getSession(31)?.triggerKind)
    }

    @Test fun staleInputsDoNotResolveRewardsOrEndCurrentDialogue() = runTest {
        val state = EveEventStateMachine(); state.activate(31, event(EventPhase.Executing))
        val response = mockk<ResponseSender>(relaxed=true)
        val builder = mockk<EveStateBuilder>(relaxed=true)
        val executor = mockk<EveResultExecutor>(relaxed=true)
        val session = mockk<GameSession>(relaxed=true) { every { roleId } returns 31 }
        val flow = EveFlowController(response, state, builder, mockk(relaxed=true), executor,
            mockk(relaxed=true), mockk(relaxed=true), mockk(relaxed=true), mockk(relaxed=true),
            mockk(relaxed=true), emptyMap(), mockk(relaxed=true))
        flow.chooseEvent(session, 30)
        flow.resumeAfterBattle(session, 13, true)
        coVerify(exactly=0) { builder.buildPlayerState(any(), any(), any()) }
        coVerify(exactly=0) { executor.executeResultClass(any(), any()) }
        verify(exactly=0) { response.sendEventEnd(any()) }
        assertEquals(EventPhase.Executing, state.getSession(31)?.phase)
    }
}
