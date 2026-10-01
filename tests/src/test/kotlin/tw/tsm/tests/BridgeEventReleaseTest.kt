package tw.tsm.tests

import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.domain.data.*
import tw.tsm.domain.interfaces.*
import tw.tsm.domain.interfaces.repositories.QuestRepository
import tw.tsm.gamelogic.systems.*

class BridgeEventReleaseTest {
    private val state = EveEventStateMachine()
    private val response = mockk<ResponseSender>(relaxed = true)
    private val quests = mockk<QuestRepository>(relaxed = true)
    private val session = mockk<GameSession>(relaxed = true) { every { roleId } returns 31; every { mapId } returns 63000 }
    private val data = mapOf(63000 to SceneEveData(emptyMap(), emptyMap(), mapOf(4 to NpcEventData(4, emptyList(), emptyList())), emptyMap()))
    private fun flow(): EveFlowController {
        val builder = mockk<EveStateBuilder>()
        coEvery { builder.buildPlayerState(31, 63000, session) } returns PlayerEventState()
        return EveFlowController(response, state, builder, EveConditionResolver(data), mockk(relaxed = true),
            mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), quests, mockk(relaxed = true), data, mockk(relaxed = true))
    }
    private fun activate() = state.activate(31, EventSession(63000, 4, 10, results = emptyList(), phase = EventPhase.AwaitingBattle))
    @Test fun losingWithoutFailureBranchUnlocksWithoutCompletingEvent() = runTest {
        activate(); flow().resumeAfterBattle(session, 13, false)
        assertNull(state.getSession(31))
        verify(exactly = 1) { response.sendEventEnd(session) }
        coVerify(exactly = 0) { quests.insertCompletedEvent(any(), any(), any()) }
        assertEquals(0, state.getCompletedEveCounts(31, 63000)[4] ?: 0)
    }
    @Test fun classLoadingFailureClearsAwaitedBattleAndAllowsRetry() = runTest {
        activate(); val f = flow()
        BattleEventContinuation.run({ throw NoClassDefFoundError("EveMissionResultOperation") }, { f.abortAfterBattle(session) })
        assertNull(state.getSession(31))
        verify(exactly = 1) { response.sendEventEnd(session) }
        coVerify(exactly = 0) { quests.insertCompletedEvent(any(), any(), any()) }
        activate()
        assertNotNull(state.claimBattleResult(31, 2))
    }
}
