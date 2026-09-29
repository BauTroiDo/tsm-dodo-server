package tw.tsm.tests

import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import tw.tsm.app.handlers.ActivityModelHandler
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.enums.SessionState
import java.nio.ByteBuffer
import java.nio.ByteOrder

class DvtActivityModelWireTest {
    @Test
    fun `C124-4 empty refresh request returns empty S124-4 snapshot`() = runTest {
        val packets = mutableListOf<ByteArray>()
        val session = mockk<GameSession>(relaxed = true) {
            every { state } returns SessionState.IN_GAME
            every { send(capture(packets)) } just Runs
        }

        ActivityModelHandler().handle(session, 4, ByteArray(0))

        assertEquals(1, packets.size)
        ByteBuffer.wrap(packets.single()).order(ByteOrder.LITTLE_ENDIAN).apply {
            assertEquals(124, get().toInt() and 0xFF)
            assertEquals(4, get().toInt() and 0xFF)
            assertEquals(0, get().toInt() and 0xFF)
            assertEquals(0, int)
            assertFalse(hasRemaining())
        }
    }

    @Test
    fun `C124-4 rejects unexpected payload instead of inventing semantics`() = runTest {
        val packets = mutableListOf<ByteArray>()
        val session = mockk<GameSession>(relaxed = true) {
            every { state } returns SessionState.IN_GAME
            every { send(capture(packets)) } just Runs
        }

        ActivityModelHandler().handle(session, 4, byteArrayOf(1))

        assertEquals(0, packets.size)
    }
}
