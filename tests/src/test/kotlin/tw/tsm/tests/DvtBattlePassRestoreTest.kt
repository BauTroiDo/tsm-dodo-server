package tw.tsm.tests

import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.data.loaders.JiugonggeDatLoader
import tw.tsm.data.loaders.PassAwardDatLoader
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.util.OleDate
import tw.tsm.gamelogic.systems.*
import tw.tsm.protocol.PacketReader
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime

class DvtBattlePassRestoreTest {
    private fun bytes(name: String): ByteArray {
        val root = generateSequence(Path.of("").toAbsolutePath()) { it.parent }
            .first { Files.exists(it.resolve("File.dat/Data-ios/IOS_ActivityTime_C.dat")) }
        return Files.readAllBytes(root.resolve("File.dat/Data-ios/$name"))
    }

    private val times by lazy { JiugonggeDatLoader.loadActivityTimes(bytes("IOS_ActivityTime_C.dat")) }
    private val awards by lazy { PassAwardDatLoader.load(bytes("IOS_PassAward_C.dat")) }
    private val reopened = LocalDateTime.of(2026, 10, 16, 12, 0)
    private val extendedEnd = LocalDateTime.of(2026, 11, 10, 23, 59)

    @Test fun source117ExpiredButOneDevCycleReopensWithoutInventingSeason118() {
        val original = times.getValue(117)
        assertEquals(LocalDateTime.of(2026, 9, 16, 0, 0), BattlePassSystem.start(original))
        assertEquals(LocalDateTime.of(2026, 10, 13, 23, 59),
            LocalDateTime.of(original.endYear, original.endMonth, original.endDay,
                original.endHour, original.endMinute))
        assertFalse(times.containsKey(118))
        assertEquals(setOf(117, 118), awards.map { it.seasonId }.toSet())
        assertEquals(extendedEnd, BattlePassSystem.end(original))

        val pass = BattlePassSystem(awards, times, emptyMap(),
            mockk(), mockk(), mockk())
        assertEquals(117, pass.currentSeason(reopened))
        assertEquals(117, pass.currentSeason(extendedEnd.minusMinutes(1)))
        assertNull(pass.currentSeason(extendedEnd))
        assertNull(pass.currentSeason(LocalDateTime.of(2026, 11, 11, 12, 0)))
    }

    @Test fun s077ScheduleAdvertisesRestoredEndAndOpenStateToUnmodifiedDvtLua() {
        val session = mockk<GameSession>(relaxed = true)
        val packets = mutableListOf<ByteArray>()
        every { session.send(capture(packets)) } just Runs
        val schedule = ActivityScheduleSystem(times, AdminBypassPolicy(), setOf(117, 118))
        schedule.sendActivityState(session, 117, reopened)
        assertEquals(2, packets.size)
        val dates = PacketReader(packets[0])
        assertEquals(77, dates.readUByte())
        assertEquals(1, dates.readUByte())
        assertEquals(0, dates.readUByte())
        assertEquals(117, dates.readUShortLE())
        assertEquals(OleDate.fromLocalDateTime(BattlePassSystem.start(times.getValue(117))),
            dates.readDoubleLE())
        assertEquals(OleDate.fromLocalDateTime(extendedEnd), dates.readDoubleLE())

        val state = PacketReader(packets[1])
        assertEquals(77, state.readUByte())
        assertEquals(2, state.readUByte())
        assertEquals(0, state.readUByte())
        assertEquals(117, state.readUShortLE())
        assertEquals(ActivityScheduleSystem.STATE_STARTED, state.readUByte())

        packets.clear()
        schedule.sendActivityState(session, 117, extendedEnd)
        val ended = PacketReader(packets.last())
        ended.readUByte(); ended.readUByte(); ended.readUByte(); ended.readUShortLE()
        assertEquals(ActivityScheduleSystem.STATE_ENDED, ended.readUByte())
    }
}
