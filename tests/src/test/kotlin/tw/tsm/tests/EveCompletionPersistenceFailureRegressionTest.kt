package tw.tsm.tests

import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.domain.interfaces.repositories.QuestRepository
import tw.tsm.gamelogic.systems.*

class EveCompletionPersistenceFailureRegressionTest {
    @Test
    fun persistenceFailureDoesNotInvokeCompletionHookOrAutoChain() = runTest {
        val sender = mockk<ResponseSender>(relaxed = true)
        val machine = mockk<EveEventStateMachine>(relaxed = true)
        val repository = mockk<QuestRepository>(relaxed = true)
        val autoChain = mockk<EveAutoChainEngine>(relaxed = true)
        val session = mockk<GameSession>(relaxed = true) {
            every { roleId } returns 31
            every { characterName } returns "TestQuest"
        }
        every { machine.getSession(31L) } returns EventSession(12001, 1, 1, results = emptyList())
        every { machine.advance(31L) } returns null
        every { machine.complete(31L) } returns EventSession(12001, 1, 1, results = emptyList())
        coEvery { repository.insertCompletedEvent(31L, 12001, 1) } throws IllegalStateException("database unavailable")

        val flow = EveFlowController(
            sender, machine, mockk(relaxed = true), mockk(relaxed = true),
            mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
            autoChain, repository, mockk(relaxed = true), emptyMap(), mockk(relaxed = true)
        )
        val completion = mockk<suspend (GameSession) -> Unit>()
        flow.onEventCompleted = completion

        flow.advanceEvent(session)

        coVerify(exactly = 1) { repository.insertCompletedEvent(31L, 12001, 1) }
        coVerify(exactly = 0) { completion(any()) }
        coVerify(exactly = 0) { autoChain.tryAutoChain(any(), any(), any()) }
        verify(exactly = 1) { sender.sendEventEnd(session) }
        verify(exactly = 1) { sender.sendSystemMessage(session, match { it.contains("Lỗi lưu") }) }
    }
}
