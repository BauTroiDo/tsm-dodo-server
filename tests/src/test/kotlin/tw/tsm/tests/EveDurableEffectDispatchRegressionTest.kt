package tw.tsm.tests

import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.domain.data.EveResult
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.domain.interfaces.repositories.EveEffectClaimRepository
import tw.tsm.domain.interfaces.repositories.QuestRepository
import tw.tsm.gamelogic.systems.*

class EveDurableEffectDispatchRegressionTest {
    private fun reward() = EveResult(0, 1, 0, 1, 29124, 1, 1, 0)
    private fun gameSession(): GameSession = mockk(relaxed = true) {
        every { roleId } returns 31
        every { mapId } returns 12001
        every { characterName } returns "ClaimTester"
    }
    private fun flow(
        machine: EveEventStateMachine,
        effectClaims: EveEffectClaimRepository,
        executor: EveResultExecutor,
        questRepo: QuestRepository,
        sender: ResponseSender,
    ): EveFlowController = EveFlowController(
        sender, machine, mockk(relaxed = true), mockk(relaxed = true),
        executor, mockk(relaxed = true), mockk(relaxed = true),
        mockk(relaxed = true), questRepo, mockk(relaxed = true),
        emptyMap(), mockk(relaxed = true), effectClaims
    )

    @Test
    fun itemRewardIsReservedBeforeExecutionAndSettledOnce() = runTest {
        val machine = EveEventStateMachine()
        val effect = reward()
        machine.activate(31L, EventSession(12001, 5, 12, results = listOf(effect), currentIndex = 1))
        val sender = mockk<ResponseSender>(relaxed = true)
        val executor = mockk<EveResultExecutor>(relaxed = true)
        val questRepo = mockk<QuestRepository>(relaxed = true)
        val claims = mockk<EveEffectClaimRepository>(relaxed = true)
        every { executor.canExecuteResults(any(), any()) } returns true
        coEvery { executor.executeResultClass(any(), effect) } returns true
        coEvery { claims.reserve(eq(31L), eq(12001), eq(5), any(), any()) } returns true
        coEvery { claims.settle(eq(31L), eq(12001), eq(5), any()) } coAnswers {
            assertTrue(machine.getCompletedEveCounts(31L, 12001).isEmpty())
            1
        }

        val f = flow(machine, claims, executor, questRepo, sender)
        val game = gameSession()
        f.startEvent(game, effect)
        f.advanceEvent(game) // Stale C020-006 must be harmless after completion.

        coVerifyOrder {
            claims.reserve(31L, 12001, 5, any(), any())
            executor.executeResultClass(game, effect)
            claims.settle(31L, 12001, 5, any())
        }
        coVerify(exactly = 0) { questRepo.insertCompletedEventAndGetCount(any(), any(), any()) }
        coVerify(exactly = 1) { claims.settle(31L, 12001, 5, any()) }
        assertEquals(1, machine.getCompletedEveCounts(31L, 12001)[5])
        verify(exactly = 1) { sender.sendEventEnd(game) }
    }

    @Test
    fun foreignPendingClaimRejectsRewardWithoutExecutingEffectOrIncrementingEveCount() = runTest {
        val machine = EveEventStateMachine()
        val effect = reward()
        machine.activate(31L, EventSession(12001, 5, 12, results = listOf(effect), currentIndex = 1))
        val sender = mockk<ResponseSender>(relaxed = true)
        val executor = mockk<EveResultExecutor>(relaxed = true)
        val claims = mockk<EveEffectClaimRepository>(relaxed = true)
        every { executor.canExecuteResults(any(), any()) } returns true
        coEvery { claims.reserve(eq(31L), eq(12001), eq(5), any(), any()) } returns false

        val game = gameSession()
        flow(machine, claims, executor, mockk(relaxed = true), sender).startEvent(game, effect)

        assertNull(machine.getSession(31L))
        assertTrue(machine.getCompletedEveCounts(31L, 12001).isEmpty())
        coVerify(exactly = 0) { executor.executeResultClass(any(), any()) }
        coVerify(exactly = 0) { claims.settle(any(), any(), any(), any()) }
        verify(exactly = 1) { sender.sendSystemMessage(game, match { it.contains("đang chờ") }) }
        verify(exactly = 1) { sender.sendEventEnd(game) }
    }

    @Test
    fun validTalkOnlyEveCompletesWithoutCreatingARewardClaim() = runTest {
        val machine = EveEventStateMachine()
        val effect = EveResult(0, 1, 1, 0, 10166, 0, 0, 0)
        machine.activate(31L, EventSession(12001, 6, 12, results = listOf(effect), currentIndex = 1))
        val sender = mockk<ResponseSender>(relaxed = true)
        val claims = mockk<EveEffectClaimRepository>(relaxed = true)
        val questRepo = mockk<QuestRepository>(relaxed = true)
        coEvery { questRepo.insertCompletedEventAndGetCount(31L, 12001, 6) } returns 1
        val game = gameSession()
        flow(machine, claims, mockk(relaxed = true), questRepo, sender).advanceEvent(game)
        coVerify(exactly = 0) { claims.reserve(any(), any(), any(), any(), any()) }
        coVerify(exactly = 1) { questRepo.insertCompletedEventAndGetCount(31L, 12001, 6) }
        assertEquals(1, machine.getCompletedEveCounts(31L, 12001)[6])
    }
}
