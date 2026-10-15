package tw.tsm.tests

import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.app.handlers.RequiemHandler
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession

class DvtRequiem111WireRegressionTest {
    private fun session(state: SessionState = SessionState.IN_GAME):
        Pair<GameSession, MutableList<ByteArray>> {
        val s = mockk<GameSession>(relaxed = true)
        val out = mutableListOf<ByteArray>()
        every { s.state } returns state
        every { s.send(capture(out)) } just Runs
        return s to out
    }

    @Test fun openDeliversPointsBeforeUiAckAndEmptyBossList() = runBlocking {
        val (s, out) = session()
        val handler = RequiemHandler()
        assertEquals(111, handler.mainKind)
        handler.handle(s, 0, byteArrayOf())
        assertEquals(3, out.size)
        assertArrayEquals(byteArrayOf(111, 3, 0, 0, 0), out[0])
        assertArrayEquals(byteArrayOf(111, 0, 0, 1), out[1])
        assertArrayEquals(byteArrayOf(111, 1, 0, 0), out[2])
    }

    @Test fun mutatingRequestsFailClosedWithoutFakeRewards() = runBlocking {
        val (s, out) = session()
        val h = RequiemHandler()
        h.handle(s, 2, byteArrayOf(1))
        h.handle(s, 4, byteArrayOf(1))
        h.handle(s, 5, byteArrayOf(1, 1))
        assertEquals(3, out.size)
        assertArrayEquals(byteArrayOf(111, 2, 0, 2), out[0])
        assertArrayEquals(byteArrayOf(111, 4, 0, 7), out[1])
        assertArrayEquals(byteArrayOf(111, 5, 0, 2), out[2])
    }

    @Test fun malformedRequestsCannotOpenOrAward() = runBlocking {
        val (s, out) = session()
        val h = RequiemHandler()
        h.handle(s, 0, byteArrayOf(1))
        h.handle(s, 2, byteArrayOf())
        h.handle(s, 4, byteArrayOf(1,2))
        h.handle(s, 5, byteArrayOf(1))
        assertTrue(out.isEmpty())
    }

    @Test fun loginScreenCannotOpenOrMutate() = runBlocking {
        val (s, out) = session(SessionState.CONNECTED)
        val h = RequiemHandler()
        h.handle(s, 0, byteArrayOf())
        h.handle(s, 2, byteArrayOf(1))
        h.handle(s, 4, byteArrayOf(1))
        h.handle(s, 5, byteArrayOf(1, 1))
        assertTrue(out.isEmpty())
    }
}
