package tw.tsm.tests

import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tw.tsm.app.handlers.MessageBoardHandler
import tw.tsm.app.response.NpcEventResponseSender
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.domain.interfaces.SessionSender
import tw.tsm.domain.interfaces.repositories.MessageBoardRecord
import tw.tsm.domain.interfaces.repositories.MessageBoardRepository
import tw.tsm.gamelogic.systems.RoleCountSystem
import tw.tsm.network.GameSessionImpl
import tw.tsm.network.SessionManager
import tw.tsm.protocol.PacketReader

class MessageBoardD9C3RegressionTest {
    private fun record(message: String = "hello") = MessageBoardRecord(
        id = 7L, characterId = 31L, playerId = 900L, name = "Tester",
        sex = 1, headStyle = 2, color1 = 0x11223344, color2 = 0x55667788,
        createdAt = 1_700_000_000_000L, message = message
    )

    @Test
    fun `S36-004 snapshot uses byte-length message`() {
        val packets = mutableListOf<ByteArray>()
        val session = object : SessionSender {
            override fun send(data: ByteArray) { packets += data }
            override fun close() = Unit
        }
        val r = record()

        NpcEventResponseSender().sendMessageBoardSnapshot(session, listOf(r))

        val p = PacketReader(packets.single())
        assertEquals(36, p.readUByte())
        assertEquals(4, p.readUByte())
        assertEquals(0, p.readUByte())
        assertEquals(r.playerId, p.readULongLE())
        assertEquals(r.name, p.readBig5WithByteLen())
        assertEquals(r.sex, p.readUByte())
        assertEquals(r.headStyle, p.readUByte())
        assertEquals(r.color1, p.readIntLE())
        assertEquals(r.color2, p.readIntLE())
        assertEquals(r.oaDate, p.readDoubleLE())
        assertEquals(r.message, p.readBig5WithByteLen())
        assertEquals(0, p.remaining)
    }

    @Test
    fun `S36-005 uses raw message to packet end`() {
        val packets = mutableListOf<ByteArray>()
        val session = object : SessionSender {
            override fun send(data: ByteArray) { packets += data }
            override fun close() = Unit
        }
        val r = record()

        NpcEventResponseSender().sendMessageBoardAdded(session, r)

        val p = PacketReader(packets.single())
        assertEquals(36, p.readUByte())
        assertEquals(5, p.readUByte())
        assertEquals(0, p.readUByte())
        assertEquals(r.playerId, p.readULongLE())
        assertEquals(r.name, p.readBig5WithByteLen())
        assertEquals(r.sex, p.readUByte())
        assertEquals(r.headStyle, p.readUByte())
        assertEquals(r.color1, p.readIntLE())
        assertEquals(r.color2, p.readIntLE())
        assertEquals(r.oaDate, p.readDoubleLE())
        assertEquals(r.message, p.readBig5(p.remaining))
        assertEquals(0, p.remaining)
    }

    @Test
    fun `persist success broadcasts after quota update`() = runTest {
        val sender = mockk<GameSession>(relaxed = true) {
            every { state } returns SessionState.IN_GAME
            every { roleId } returns 31
            every { playerId } returns 900L
            every { characterName } returns "Tester"
            every { sex } returns 1
            every { hair } returns 2
            every { color1 } returns 0x11223344
            every { color2 } returns 0x55667788
        }
        val target = mockk<GameSessionImpl>(relaxed = true) {
            every { state } returns SessionState.IN_GAME
        }
        val counts = mockk<RoleCountSystem>()
        val response = mockk<ResponseSender>(relaxed = true)
        val repo = mockk<MessageBoardRepository>()
        val sessions = mockk<SessionManager>()
        val r = record()

        every { counts.getValue(31L, 13) } returns 0
        every { counts.increment(31L, 13, 1) } returns 1
        every { counts.getMaxValue(31L, 13) } returns 3
        coEvery { repo.create(31L, 900L, "Tester", 1, 2, 0x11223344, 0x55667788, "hello", any(), any()) } returns r
        every { sessions.allSessions() } returns listOf(target)

        MessageBoardHandler(counts, response, repo, sessions).handle(sender, 1, "hello".toByteArray())

        coVerify(exactly = 1) { repo.create(31L, 900L, "Tester", 1, 2, 0x11223344, 0x55667788, "hello", any(), any()) }
        verify(exactly = 1) { counts.increment(31L, 13, 1) }
        verify(exactly = 1) { response.sendMessageBoardAdded(target, r) }
    }

    @Test
    fun `persist failure does not consume quota`() = runTest {
        val packets = mutableListOf<ByteArray>()
        val sender = mockk<GameSession>(relaxed = true) {
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
        val counts = mockk<RoleCountSystem>()
        val response = mockk<ResponseSender>(relaxed = true)
        val repo = mockk<MessageBoardRepository>()
        val sessions = mockk<SessionManager>(relaxed = true)
        every { counts.getValue(31L, 13) } returns 0
        coEvery { repo.create(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } throws IllegalStateException("db down")

        MessageBoardHandler(counts, response, repo, sessions).handle(sender, 1, "hello".toByteArray())

        verify(exactly = 0) { counts.increment(any(), any(), any()) }
        verify(exactly = 0) { response.sendMessageBoardAdded(any(), any()) }
        assertEquals(listOf(36, 7, 0, 1, 7), packets.single().map { it.toInt() and 0xFF })
    }
}
