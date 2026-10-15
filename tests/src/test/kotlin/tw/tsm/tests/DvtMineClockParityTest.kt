package tw.tsm.tests

import io.mockk.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.app.handlers.MiningHandler
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.repositories.MiningRecord
import tw.tsm.domain.util.OleDate
import tw.tsm.gamelogic.systems.MiningClock
import tw.tsm.gamelogic.systems.MiningSystem
import tw.tsm.gamelogic.systems.MiningView
import tw.tsm.gamelogic.systems.PartySystem
import tw.tsm.gamelogic.systems.SpecialSceneTeleportService
import tw.tsm.gamelogic.systems.WorldManager

/** DVT compares S170-001 OADate to the LOCAL CGTimer server wall clock. */
class DvtMineClockParityTest {
    private val vietnam = ZoneId.of("Asia/Ho_Chi_Minh")
    private val persisted = Instant.parse("2026-10-15T01:52:04.876Z").toEpochMilli()

    @Test fun utcPersistedMillisEncodeToVietnamLocalWallClock() {
        val oa = MiningClock.toDvtWallClockOaDate(persisted, vietnam)
        val expected = OleDate.fromLocalDateTime(
            LocalDateTime.of(2026, 10, 15, 8, 52, 4, 876_000_000)
        )
        assertEquals(expected, oa, 0.00000001)
        assertTrue(oa - OleDate.fromMillis(persisted) > 6.99 / 24.0)
    }

    @Test fun clientTimerDoesNotImmediatelyConsumeThirtyFuelByTimezoneShift() {
        val setTime = MiningClock.toDvtWallClockOaDate(persisted, vietnam)
        val actualServerTime = OleDate.fromLocalDateTime(
            LocalDateTime.of(2026, 10, 15, 9, 5, 16)
        )
        val sixMinuteTicks = kotlin.math.floor((actualServerTime - setTime) * 86400.0 / 360.0).toInt()
        assertTrue(sixMinuteTicks in 0..3, "DVT must consume minutes, not 7 hours")
        assertTrue(30 - sixMinuteTicks > 0, "30 fuel must not render as 0")
    }

    @Test fun mineWireTimestampUsesSameLocalClockAsLoginServerTime() = runTest {
        val mine = mockk<MiningSystem>()
        val packets = mutableListOf<ByteArray>()
        val session = mockk<GameSession>(relaxed = true) {
            every { state } returns SessionState.IN_GAME
            every { roleId } returns 88
            every { send(any()) } answers { packets += firstArg<ByteArray>() }
        }
        val expected = MiningView(MiningRecord(machineFull = listOf(19,30,0,0),
            updatedAtMillis = persisted), MiningSystem.MAX_MINE)
        coEvery { mine.getView(88L) } returns expected
        MiningHandler(mine, mockk<SpecialSceneTeleportService>(relaxed = true),
            mockk<PartySystem>(relaxed = true), mockk<WorldManager>(relaxed = true))
            .handle(session, 1, byteArrayOf(1))
        val packet = packets.single()
        assertEquals(38, packet.size)
        val oa = ByteBuffer.wrap(packet, 13, 8).order(ByteOrder.LITTLE_ENDIAN).double
        assertEquals(expected.setTimeOaDate, oa, 0.00000001)
        val localNow = OleDate.fromLocalDateTime(
            Instant.ofEpochMilli(persisted).atZone(ZoneId.systemDefault()).toLocalDateTime()
        )
        assertEquals(localNow, oa, 0.00000001)
    }
}
