package tw.tsm.tests

import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.gamelogic.systems.*

class EveStaleNextRegressionTest {
    @Test
    fun lateNextWithoutActiveEveDoesNotCompleteOrSendEnd() = runTest {
        val sender = mockk<ResponseSender>(relaxed = true)
        val machine = mockk<EveEventStateMachine>(relaxed = true)
        val session = mockk<GameSession>(relaxed = true) {
            every { roleId } returns 31
        }
        every { machine.getSession(31L) } returns null
        val flow = EveFlowController(
            sender, machine, mockk(relaxed = true), mockk(relaxed = true),
            mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
            mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
            emptyMap(), mockk(relaxed = true),
        )
        flow.advanceEvent(session)
        verify(exactly = 1) { machine.getSession(31L) }
        verify(exactly = 0) { machine.advance(any()) }
        verify(exactly = 0) { machine.complete(any()) }
        verify(exactly = 0) { sender.sendEventEnd(any()) }
    }
}
