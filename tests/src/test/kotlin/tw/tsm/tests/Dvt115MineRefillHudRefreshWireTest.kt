package tw.tsm.tests

import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.app.handlers.MiningHandler
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.repositories.MiningRecord
import tw.tsm.gamelogic.systems.MiningRefillResult
import tw.tsm.gamelogic.systems.MiningSystem
import tw.tsm.gamelogic.systems.MiningView
import tw.tsm.gamelogic.systems.PartySystem
import tw.tsm.gamelogic.systems.SpecialSceneTeleportService
import tw.tsm.gamelogic.systems.WorldManager
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * DVT original UI/UIMine.lua:
 * - C170-010: bagSlot U16 + itemId U16 + count U16 + reversed workerIndex U8.
 * - S170-010 success modifies machineData but NOT oldMachineData.
 * - S170-001 immediately afterwards must replace oldMachineData (SetData)
 *   to keep the 360-second UI timer from restoring stale fullness.
 * Wire only. All persistence tested separately in DvtMiningFuelAtomicSqlTest.
 */
class Dvt115MineRefillHudRefreshWireTest {
    private val system = mockk<MiningSystem>()
    private val teleport = mockk<SpecialSceneTeleportService>(relaxed = true)
    private val party = mockk<PartySystem>(relaxed = true)
    private val world = mockk<WorldManager>(relaxed = true)
    private val handler = MiningHandler(system, teleport, party, world)
    private val messages = mutableListOf<ByteArray>()
    private val session = mockk<GameSession>(relaxed = true) {
        every { state } returns SessionState.IN_GAME
        every { roleId } returns 44
        every { send(any()) } answers {
            messages.add(firstArg<ByteArray>())
            Unit
        }
    }

    @Test fun successfulRefillMustSendInventoryThenAckThenFreshTimerBaseline() = runBlocking {
        coEvery { system.refill(44L, 7, 55431, 2, 3) } returns
            MiningRefillResult(true, 3, 20)
        coEvery { system.getView(44L) } returns MiningView(
            MiningRecord(mineType = 1, machineFull = listOf(20,0,0,0),
                updatedAtMillis=1_700_000_000_000L), MiningSystem.MAX_MINE)
        // Lua: UI worker1 -> wireIndex3
        handler.handle(session, 10, byteArrayOf(7,0, -121, -40, 2,0,3))
        assertEquals(3, messages.size)
        val bag = messages[0].map { it.toInt() and 255 }
        val ack = messages[1].map { it.toInt() and 255 }
        val info = messages[2].map { it.toInt() and 255 }
        assertEquals(listOf(23,9,0),bag.take(3))
        assertEquals(7, ByteBuffer.wrap(messages[0],3,2)
            .order(ByteOrder.LITTLE_ENDIAN).short.toInt())
        assertEquals(2, ByteBuffer.wrap(messages[0],5,4)
            .order(ByteOrder.LITTLE_ENDIAN).int)
        assertEquals(listOf(170,10,0,1,3,20,0),ack)
        assertEquals(38,info.size)
        assertEquals(listOf(170,1,0,1,1),info.take(5))
        // Protocol S170-001 serializes workers 4,3,2,1; worker1 at end.
        val workerOne = ByteBuffer.wrap(messages[2],33,4).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(20,workerOne.short.toInt() and 65535)
        assertEquals(MiningSystem.MACHINE_SKIN_IDS[0],workerOne.short.toInt() and 65535)
        coVerify(exactly = 1) { system.refill(44L,7,55431,2,3) }
        coVerify(exactly = 1) { system.getView(44L) }
    }

    @Test fun refillFailureMustNotClaimBagDebitOrResetTimer() = runBlocking {
        coEvery { system.refill(44L,7,55431,2,3) } returns
            MiningRefillResult(false,3,0)
        handler.handle(session,10,byteArrayOf(7,0,-121,-40,2,0,3))
        assertEquals(1,messages.size)
        assertEquals(listOf(170,10,0,0,3,0,0),
            messages.single().map { it.toInt() and 255 })
        coVerify(exactly = 0) { system.getView(any()) }
    }

    @Test fun malformedFuelPacketDoesNotTouchPersistenceOrSendSuccess() = runBlocking {
        handler.handle(session,10,byteArrayOf())
        handler.handle(session,10,byteArrayOf(7,0,-121,-40,2,0))
        handler.handle(session,10,byteArrayOf(7,0,-121,-40,2,0,3,4))
        assertTrue(messages.isEmpty())
        coVerify(exactly=0) { system.refill(any(),any(),any(),any(),any()) }
    }
}
