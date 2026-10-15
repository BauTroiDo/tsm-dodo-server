package tw.tsm.tests

import io.mockk.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.app.handlers.MiningHandler
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.repositories.MiningRecord
import tw.tsm.domain.interfaces.repositories.MiningRepository
import tw.tsm.gamelogic.systems.InventorySystem
import tw.tsm.gamelogic.systems.MiningSystem
import tw.tsm.gamelogic.systems.MiningView
import tw.tsm.gamelogic.systems.PartySystem
import tw.tsm.gamelogic.systems.SpecialSceneTeleportService
import tw.tsm.gamelogic.systems.WorldManager

/** Client-source parity ONLY: UIMine.lua and Common/protocal.lua in _native_dump_dvt115.
 * These tests do not prove or invent server-side TS currency reward quantities.
 */
class Dvt115MiningSourceParityTest {
    private val character = 73L
    private var now = 9_000_000L
    private val repo = mockk<MiningRepository>(relaxed = true)
    private val inventory = mockk<InventorySystem>(relaxed = true)

    private fun start(record: MiningRecord): MiningSystem {
        coEvery { repo.load(character) } returns record
        coEvery { repo.loadRemainingOre(any()) } returns null
        coEvery { repo.save(any(), any()) } just Runs
        return MiningSystem(repo, inventory, emptyMap(), nowMillis = { now })
    }

    @Test fun clientOriginalConstantsRemainExact() {
        assertEquals(600_000_000L, MiningSystem.MAX_MINE)
        assertEquals(65_535, MiningSystem.FULL_MAX)
        assertEquals(360_000L, MiningSystem.TICK_MS)
        assertEquals(49996, MiningSystem.MINE_SCENE_ID)
        assertEquals(listOf(55431, 55432, 55433, 55434), MiningSystem.FUEL_ITEM_IDS)
        assertEquals(listOf(22120, 22121, 22122, 22123), MiningSystem.MACHINE_SKIN_IDS)
    }

    @Test fun fuelDoesNotTickBefore360SecondBoundary() = runBlocking {
        val start = 9_000_000L
        val system = start(MiningRecord(machineFull = listOf(2, 1, 0, 0), updatedAtMillis = start))
        now = start + 359_999L
        val before = system.getView(character)
        assertEquals(listOf(2, 1, 0, 0), before.record.machineFull)
        assertEquals(start, before.record.updatedAtMillis)
        now = start + 360_000L
        val after = system.getView(character)
        assertEquals(listOf(1, 1, 0, 0), after.record.machineFull)
        assertEquals(start + 360_000L, after.record.updatedAtMillis)
    }

    @Test fun consumeOneWorkerAfterAnotherInOriginalLuaIndexOrder() = runBlocking {
        val start = 9_000_000L
        now = start + MiningSystem.TICK_MS * 4
        val system = start(MiningRecord(machineFull = listOf(1, 2, 3, 4), updatedAtMillis = start))
        val view = system.getView(character)
        assertEquals(listOf(0, 0, 2, 4), view.record.machineFull)
        assertEquals(start + MiningSystem.TICK_MS * 4, view.record.updatedAtMillis)
        assertTrue(view.isMining)
    }

    @Test fun dayLimitStopsHungerConsumptionAndMiningState() = runBlocking {
        val start = 9_000_000L
        now = start + MiningSystem.TICK_MS * 99
        val system = start(MiningRecord(
            machineFull = listOf(10, 11, 12, 13), updatedAtMillis = start, dayLimit = true
        ))
        val view = system.getView(character)
        assertEquals(listOf(10, 11, 12, 13), view.record.machineFull)
        assertFalse(view.isMining)
    }

    @Test fun exhaustionDoesNotSpendMoreFoodThanExists() = runBlocking {
        val start = 9_000_000L
        now = start + MiningSystem.TICK_MS * 9
        val system = start(MiningRecord(machineFull = listOf(1, 0, 0, 0), updatedAtMillis = start))
        val view = system.getView(character)
        assertEquals(listOf(0, 0, 0, 0), view.record.machineFull)
        assertFalse(view.isMining)
        assertEquals(now, view.record.updatedAtMillis)
    }

    @Test fun s170001SerializesWorkersInReverseForOriginalLuaReader() = runBlocking {
        val system = mockk<MiningSystem>()
        coEvery { system.getView(character) } returns MiningView(
            MiningRecord(machineFull = listOf(10, 20, 30, 40),
                updatedAtMillis = 1_700_000_000_000L, dayLimit = true),
            MiningSystem.MAX_MINE
        )
        val handler = MiningHandler(
            system,
            mockk<SpecialSceneTeleportService>(relaxed = true),
            mockk<PartySystem>(relaxed = true),
            mockk<WorldManager>(relaxed = true)
        )
        val packets = mutableListOf<ByteArray>()
        val session = mockk<GameSession>(relaxed = true) {
            every { state } returns SessionState.IN_GAME
            every { roleId } returns character.toInt()
            every { send(any()) } answers { packets.add(firstArg<ByteArray>()); Unit }
        }
        handler.handle(session, 1, byteArrayOf(1))
        val raw = packets.single()
        assertEquals(38, raw.size)
        assertEquals(listOf(170, 1, 0, 1, 1), raw.take(5).map { it.toInt() and 255 })
        assertEquals(600_000_000L, ByteBuffer.wrap(raw, 5, 8)
            .order(ByteOrder.LITTLE_ENDIAN).long)
        for (wireIndex in 0..3) {
            val originalIndex = 3 - wireIndex
            val value = ByteBuffer.wrap(raw, 21 + 4 * wireIndex, 4)
                .order(ByteOrder.LITTLE_ENDIAN)
            assertEquals(listOf(10, 20, 30, 40)[originalIndex], value.short.toInt() and 65535)
            assertEquals(MiningSystem.MACHINE_SKIN_IDS[originalIndex], value.short.toInt() and 65535)
        }
        assertEquals(1, raw[37].toInt() and 255)
    }
}
