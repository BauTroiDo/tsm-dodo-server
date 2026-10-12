package tw.tsm.tests

import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.app.handlers.MiningHandler
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.repositories.MiningRecord
import tw.tsm.gamelogic.systems.MiningSystem
import tw.tsm.gamelogic.systems.MiningView
import tw.tsm.gamelogic.systems.PartySystem
import tw.tsm.gamelogic.systems.SpecialSceneTeleportService
import tw.tsm.gamelogic.systems.WorldManager

/**
 * Original DVT icon EFunction.Mine sends C170-001 type=0 while its dropdown
 * subsequently sends type=1. Only 0 on the info QUERY is a default-type alias,
 * never permission to enter a mine or refill.
 */
class DvtMineIconClickWireRegressionTest {
    private val system = mockk<MiningSystem>()
    private val teleport = mockk<SpecialSceneTeleportService>(relaxed = true)
    private val parties = mockk<PartySystem>(relaxed = true)
    private val world = mockk<WorldManager>(relaxed = true)
    private val handler = MiningHandler(system, teleport, parties, world)
    private val sent = mutableListOf<ByteArray>()
    private val session = mockk<GameSession>(relaxed = true) {
        every { state } returns SessionState.IN_GAME
        every { roleId } returns 88
        every { send(any()) } answers { sent += firstArg<ByteArray>() }
    }

    private fun setupView() {
        coEvery { system.getView(88L) } returns MiningView(
            MiningRecord(mineType = 1, machineFull = listOf(10, 20, 30, 40),
                updatedAtMillis = 1_700_000_000_000L),
            MiningSystem.MAX_MINE
        )
    }

    @Test
    fun originalIconClickZeroReturnsSuccessfulMineInfoAndOpensSourceUi() = runTest {
        setupView()
        handler.handle(session, 1, byteArrayOf(0))
        coVerify(exactly = 1) { system.getView(88L) }
        val packet = sent.single()
        assertEquals(38, packet.size)
        assertEquals(listOf(170, 1, 0, 1, 1),
            packet.take(5).map { it.toInt() and 255 })
        coVerify(exactly = 0) { teleport.enterSpecialScene(any(), any()) }
    }

    @Test
    fun legitimateDropdownTypeOneStillReturnsMineInfo() = runTest {
        setupView()
        handler.handle(session, 1, byteArrayOf(1))
        coVerify(exactly = 1) { system.getView(88L) }
        assertEquals(listOf(170, 1, 0, 1, 1),
            sent.single().take(5).map { it.toInt() and 255 })
    }

    @Test
    fun unsupportedQueryTypeStillGetsOriginalDisabledResponse() = runTest {
        handler.handle(session, 1, byteArrayOf(2))
        coVerify(exactly = 0) { system.getView(any()) }
        assertEquals(listOf(170, 1, 0, 255),
            sent.single().map { it.toInt() and 255 })
    }

    @Test
    fun zeroTypeCannotBypassMineEntrance() = runTest {
        handler.handle(session, 2, byteArrayOf(0))
        coVerify(exactly = 0) { teleport.enterSpecialScene(any(), any()) }
        assertTrue(sent.isEmpty())
    }

    @Test
    fun truncatedQueryDoesNotOpenMineOrMutateAnything() = runTest {
        handler.handle(session, 1, byteArrayOf())
        coVerify(exactly = 0) { system.getView(any()) }
        assertTrue(sent.isEmpty())
    }
}
