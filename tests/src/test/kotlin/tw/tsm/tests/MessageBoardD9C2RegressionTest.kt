package tw.tsm.tests

import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tw.tsm.app.handlers.MessageBoardHandler
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.domain.interfaces.repositories.MessageBoardRecord
import tw.tsm.domain.interfaces.repositories.MessageBoardRepository
import tw.tsm.gamelogic.systems.RoleCountSystem
import tw.tsm.network.SessionManager

class MessageBoardD9C2RegressionTest {
    private fun session(packets: MutableList<ByteArray>): GameSession = mockk(relaxed = true) {
        every { state } returns SessionState.IN_GAME
        every { roleId } returns 31
        every { playerId } returns 900L
        every { characterName } returns "Tester"
        every { sex } returns 1
        every { hair } returns 2
        every { color1 } returns 3
        every { color2 } returns 4
        every { send(any()) } answers { packets += firstArg<ByteArray>(); Unit }
    }

    @Test
    fun `valid C36-001 consumes one daily message-board count`() = runTest {
        val packets = mutableListOf<ByteArray>()
        val s = session(packets)
        val counts = mockk<RoleCountSystem>()
        val response = mockk<ResponseSender>(relaxed = true)
        every { counts.getValue(31L, 13) } returns 2
        every { counts.increment(31L, 13, 1) } returns 3
        every { counts.getMaxValue(31L, 13) } returns 3
        val repo = mockk<MessageBoardRepository>()
        val sessions = mockk<SessionManager>()
        val record = MessageBoardRecord(1, 31L, 900L, "Tester", 1, 2, 3, 4, 1_700_000_000_000L, "hello")
        coEvery { repo.create(31L, 900L, "Tester", 1, 2, 3, 4, "hello", any(), any()) } returns record
        every { sessions.allSessions() } returns emptyList()

        MessageBoardHandler(counts, response, repo, sessions).handle(s, 1, "hello".toByteArray())

        coVerify(exactly = 1) { repo.create(31L, 900L, "Tester", 1, 2, 3, 4, "hello", any(), any()) }
        verify(exactly = 1) { counts.increment(31L, 13, 1) }
        verify(exactly = 1) { response.sendRoleCountUpdate(s, mapOf(13 to (3 to 3))) }
        assertEquals(0, packets.size)
    }

    @Test
    fun `message longer than 60 characters is rejected without consuming quota`() = runTest {
        val packets = mutableListOf<ByteArray>()
        val s = session(packets)
        val counts = mockk<RoleCountSystem>(relaxed = true)
        val response = mockk<ResponseSender>(relaxed = true)
        val repo = mockk<MessageBoardRepository>(relaxed = true)
        val sessions = mockk<SessionManager>(relaxed = true)

        MessageBoardHandler(counts, response, repo, sessions).handle(s, 1, "x".repeat(61).toByteArray())

        verify(exactly = 0) { counts.increment(any(), any(), any()) }
        verify(exactly = 0) { response.sendRoleCountUpdate(any(), any()) }
        assertEquals(listOf(36, 7, 0, 1, 7), packets.single().map { it.toInt() and 0xFF })
    }

    @Test
    fun `fourth daily message is rejected`() = runTest {
        val packets = mutableListOf<ByteArray>()
        val s = session(packets)
        val counts = mockk<RoleCountSystem>()
        val response = mockk<ResponseSender>(relaxed = true)
        val repo = mockk<MessageBoardRepository>(relaxed = true)
        val sessions = mockk<SessionManager>(relaxed = true)
        every { counts.getValue(31L, 13) } returns 3

        MessageBoardHandler(counts, response, repo, sessions).handle(s, 1, "hello".toByteArray())

        verify(exactly = 0) { counts.increment(any(), any(), any()) }
        verify(exactly = 0) { response.sendRoleCountUpdate(any(), any()) }
        assertEquals(listOf(36, 7, 0, 1, 7), packets.single().map { it.toInt() and 0xFF })
    }
}
