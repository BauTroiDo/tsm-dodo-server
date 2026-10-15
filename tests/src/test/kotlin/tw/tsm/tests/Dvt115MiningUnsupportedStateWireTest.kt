package tw.tsm.tests

import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.app.handlers.MiningHandler
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.gamelogic.systems.MiningSystem
import tw.tsm.gamelogic.systems.PartySystem
import tw.tsm.gamelogic.systems.SpecialSceneTeleportService
import tw.tsm.gamelogic.systems.WorldManager

/**
 * DVT original protocal.lua S170-011 only recognizes 1=success/255=failure.
 * No C170-011 sender occurs in original UIMine/UITeleport/Function Lua.
 * Pending authoritative backend state semantics, reject instead of fake success.
 */
class Dvt115MiningUnsupportedStateWireTest {
    private val system = mockk<MiningSystem>(relaxed = true)
    private val teleport = mockk<SpecialSceneTeleportService>(relaxed = true)
    private val party = mockk<PartySystem>(relaxed = true)
    private val world = mockk<WorldManager>(relaxed = true)
    private val handler = MiningHandler(system, teleport, party, world)
    private val frames = mutableListOf<ByteArray>()
    private val session = mockk<GameSession>(relaxed = true) {
        every { state } returns SessionState.IN_GAME
        every { roleId } returns 88
        every { send(any()) } answers {
            frames.add(firstArg<ByteArray>())
            Unit
        }
    }

    @Test fun typeOneCannotReportSuccessForAnUnimplementedTransition() = runBlocking {
        handler.handle(session, 11, byteArrayOf(1))
        assertEquals(1, frames.size)
        assertEquals(listOf(170, 11, 0, 255), frames.single().map { it.toInt() and 255 })
        coVerify(exactly = 0) { system.getView(any()) }
        coVerify(exactly = 0) { system.refill(any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { teleport.enterSpecialScene(any(), any()) }
    }

    @Test fun nonMineTypeCannotReportSuccess() = runBlocking {
        handler.handle(session, 11, byteArrayOf(2))
        assertEquals(listOf(170, 11, 0, 255), frames.single().map { it.toInt() and 255 })
    }

    @Test fun truncatedAndOversizedPayloadsAreIgnored() = runBlocking {
        handler.handle(session, 11, byteArrayOf())
        handler.handle(session, 11, byteArrayOf(1, 1))
        assertTrue(frames.isEmpty())
        coVerify(exactly = 0) { system.getView(any()) }
    }
}
