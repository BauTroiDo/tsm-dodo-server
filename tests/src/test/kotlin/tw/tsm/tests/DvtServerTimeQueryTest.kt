package tw.tsm.tests

import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.app.handlers.LoginHandler
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.domain.interfaces.repositories.CharacterRepository
import tw.tsm.gamelogic.systems.AuthSystem
import tw.tsm.gamelogic.systems.LoginService
import tw.tsm.gamelogic.systems.WorldManager
import java.nio.ByteBuffer
import java.nio.ByteOrder

class DvtServerTimeQueryTest {
    @Test fun `C1-16 returns current server OLE time for time-sensitive UIs`() = runTest {
        val sent = slot<ByteArray>()
        val session = mockk<GameSession>(relaxed = true) {
            every { send(capture(sent)) } just Runs
        }
        val handler = LoginHandler(
            mockk<AuthSystem>(),
            mockk<LoginService>(relaxed = true),
            mockk<ResponseSender>(),
            mockk<CharacterRepository>(),
            mockk<WorldManager>(),
        )

        val before = LoginService.currentServerTime()
        handler.handle(session, 16, byteArrayOf())
        val after = LoginService.currentServerTime()

        assertTrue(sent.isCaptured)
        ByteBuffer.wrap(sent.captured).order(ByteOrder.LITTLE_ENDIAN).apply {
            assertEquals(1, get().toInt() and 255)
            assertEquals(16, get().toInt() and 255)
            assertEquals(0, get().toInt() and 255)
            val serverTime = double
            assertTrue(serverTime in before..after)
            assertFalse(hasRemaining())
        }
    }
}
