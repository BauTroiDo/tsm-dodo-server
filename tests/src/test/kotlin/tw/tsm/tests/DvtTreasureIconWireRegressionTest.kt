package tw.tsm.tests

import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.app.handlers.TreasureHandler
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.repositories.MissionFlagRecord
import tw.tsm.gamelogic.systems.QuestSystem

/**
 * DVT C123-001 UI open uses original S123-001 count=0 when holdings are
 * unsupported. The server cannot invent Treasure level, star or materials.
 * Original 11804 bit936 remains a hard gate on every Treasure RPC.
 */
class DvtTreasureIconWireRegressionTest {
    private val quest = mockk<QuestSystem>()
    private val handler = TreasureHandler(quest)
    private val sent = mutableListOf<ByteArray>()
    private val session = mockk<GameSession>(relaxed = true) {
        every { state } returns SessionState.IN_GAME
        every { roleId } returns 88
        every { send(any()) } answers { sent += firstArg<ByteArray>() }
    }
    private fun granted() {
        coEvery { quest.getFlags(88L) } returns listOf(MissionFlagRecord(936, 1))
    }
    private fun gated() {
        coEvery { quest.getFlags(88L) } returns listOf(MissionFlagRecord(936, 0))
    }

    @Test
    fun originalUnlockedIconC123001GetsExactlyEmptySevenByteS123001Snapshot() = runTest {
        granted()
        handler.handle(session, 1, byteArrayOf())
        assertEquals(listOf(123, 1, 0, 0, 0, 0, 0),
            sent.single().map { it.toInt() and 255 })
        coVerify(exactly = 1) { quest.getFlags(88L) }
    }

    @Test
    fun legitimateUncompletedMarkerCannotOpenUiOrPerformUpgrade() = runTest {
        gated()
        handler.handle(session, 1, byteArrayOf())
        handler.handle(session, 2, byteArrayOf(1, 0, 1, 0, 0, 0, 0, 0))
        handler.handle(session, 3, byteArrayOf(1, 0, 1, 0, 0, 0, 0, 0))
        assertTrue(sent.isEmpty())
    }

    @Test
    fun existingLevelUpgradeRequestExplicitlyRejectedWithoutGrant() = runTest {
        granted()
        handler.handle(session, 2, byteArrayOf(42, 0, 1, 0, 0, 0, 10, 0))
        assertEquals(listOf(123, 2, 0, 255),
            sent.single().map { it.toInt() and 255 })
    }

    @Test
    fun starUpgradeReturnsSafeFullErrorBodyAndSameRequestedId() = runTest {
        granted()
        handler.handle(session, 3, byteArrayOf(0x34, 0x12, 1, 0, 0, 0, 10, 0))
        assertEquals(listOf(123, 3, 0, 255, 0x34, 0x12, 0, 0),
            sent.single().map { it.toInt() and 255 })
    }

    @Test
    fun malformedPayloadNeverGetsSuccessOrMutatesResources() = runTest {
        granted()
        handler.handle(session, 1, byteArrayOf(1))
        handler.handle(session, 2, byteArrayOf(1, 2, 3))
        handler.handle(session, 3, byteArrayOf(1, 2, 3))
        assertTrue(sent.isEmpty())
    }

    @Test
    fun sessionMustBeInGameEvenWithCompletedMarker() = runTest {
        every { session.state } returns SessionState.LOGGED_IN
        handler.handle(session, 1, byteArrayOf())
        coVerify(exactly = 0) { quest.getFlags(any()) }
        assertTrue(sent.isEmpty())
    }
}
