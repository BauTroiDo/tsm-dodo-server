package tw.tsm.tests

import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.domain.data.EveResult
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.domain.interfaces.repositories.QuestRepository
import tw.tsm.gamelogic.systems.*

class EveCompletionPersistenceFailureRegressionTest {
    private fun activeStateChangingEve() = EventSession(
        mapId = 12001, eveNo = 1, npcClickId = 1,
        results = listOf(EveResult(0, 1, 0, 2, 11804, 1, 1, 0)),
        currentIndex = 1, matchedConditionNo = 12,
    )

    @Test
    fun failedWriteDoesNotPoisonEveCountOrCompletedConditionAndDoesNotReplayReward() = runTest {
        val sender = mockk<ResponseSender>(relaxed = true)
        val machine = EveEventStateMachine()
        machine.activate(31L, activeStateChangingEve())
        val repository = mockk<QuestRepository>(relaxed = true)
        val autoChain = mockk<EveAutoChainEngine>(relaxed = true)
        val session = mockk<GameSession>(relaxed = true) {
            every { roleId } returns 31
            every { characterName } returns "TestQuest"
        }
        coEvery { repository.insertCompletedEvent(31L, 12001, 1) } throws IllegalStateException("database unavailable")
        val flow = EveFlowController(
            sender, machine, mockk(relaxed = true), mockk(relaxed = true),
            mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
            autoChain, repository, mockk(relaxed = true), emptyMap(), mockk(relaxed = true)
        )
        val completion = mockk<suspend (GameSession) -> Unit>()
        flow.onEventCompleted = completion

        flow.advanceEvent(session)
        // A late duplicate C020-006 must not repeat the persistence attempt.
        flow.advanceEvent(session)

        coVerify(exactly = 1) { repository.insertCompletedEvent(31L, 12001, 1) }
        assertNull(machine.getSession(31L))
        assertTrue(machine.getCompletedEveCounts(31L, 12001).isEmpty())
        assertFalse(machine.isConditionCompleted(31L, 12001, 1, 12))
        coVerify(exactly = 0) { completion(any()) }
        coVerify(exactly = 0) { autoChain.tryAutoChain(any(), any(), any()) }
        verify(exactly = 1) { sender.sendEventEnd(session) }
        verify(exactly = 1) { sender.sendSystemMessage(session, match { it.contains("Lỗi lưu") }) }
    }

    @Test
    fun successfulWriteIsVisibleToCacheAndCompletedConditionsOnlyAfterPersistence() = runTest {
        val sender = mockk<ResponseSender>(relaxed = true)
        val machine = EveEventStateMachine()
        machine.activate(31L, activeStateChangingEve())
        val repository = mockk<QuestRepository>(relaxed = true)
        val session = mockk<GameSession>(relaxed = true) {
            every { roleId } returns 31
            every { characterName } returns "TestQuest"
        }
        coEvery { repository.insertCompletedEvent(31L, 12001, 1) } coAnswers {
            assertTrue(machine.getCompletedEveCounts(31L, 12001).isEmpty())
            assertFalse(machine.isConditionCompleted(31L, 12001, 1, 12))
        }
        val flow = EveFlowController(
            sender, machine, mockk(relaxed = true), mockk(relaxed = true),
            mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
            mockk(relaxed = true), repository, mockk(relaxed = true),
            emptyMap(), mockk(relaxed = true)
        )

        flow.advanceEvent(session)
        flow.advanceEvent(session)

        assertEquals(1, machine.getCompletedEveCounts(31L, 12001)[1])
        assertTrue(machine.isConditionCompleted(31L, 12001, 1, 12))
        coVerify(exactly = 1) { repository.insertCompletedEvent(31L, 12001, 1) }
    }
}
