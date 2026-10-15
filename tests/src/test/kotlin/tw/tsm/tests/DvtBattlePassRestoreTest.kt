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
    private val restored = LocalDateTime.of(2026,10,16,12,0)
    private val cycle1 = LocalDateTime.of(2026,10,14,0,0)
    private val cycle2 = LocalDateTime.of(2026,11,11,0,0)
    private val cycle3 = LocalDateTime.of(2026,12,9,0,0)

    @Test fun sourceScheduleCyclesIndefinitelyWithoutNewSeasonOrCharacterChanges() {
        val original=times.getValue(117)
        assertEquals(LocalDateTime.of(2026,9,16,0,0), BattlePassSystem.start(original))
        assertEquals(LocalDateTime.of(2026,10,13,23,59),
            LocalDateTime.of(original.endYear,original.endMonth,original.endDay,
                original.endHour,original.endMinute))
        assertFalse(times.containsKey(118))
        assertEquals(setOf(117,118),awards.map{it.seasonId}.toSet())

        val pass=BattlePassSystem(awards,times,emptyMap(),mockk(),mockk(),mockk())
        val originalWindow=BattlePassSystem.window(original,LocalDateTime.of(2026,10,6,12,0))
        assertEquals(LocalDateTime.of(2026,9,16,0,0),originalWindow.start)
        assertEquals(cycle1,originalWindow.end)
        val w1=BattlePassSystem.window(original,restored)
        assertEquals(cycle1,w1.start)
        assertEquals(cycle2,w1.end)
        val w2=BattlePassSystem.window(original,cycle2)
        assertEquals(cycle2,w2.start)
        assertEquals(cycle3,w2.end)
        assertEquals(cycle3,BattlePassSystem.window(original,cycle3).start)
        assertEquals(117,pass.currentSeason(restored))
        assertEquals(117,pass.currentSeason(cycle2.minusNanos(1)))
        assertEquals(117,pass.currentSeason(cycle2))
        assertEquals(117,pass.currentSeason(cycle3))
        assertEquals(117,pass.currentSeason(LocalDateTime.of(2028,11,20,8,0)))
    }

    @Test fun runtimeScheduleS077PacketsMatchEveryCycleWithoutClientPatch() {
        val s=mockk<GameSession>(relaxed=true)
        val packets=mutableListOf<ByteArray>()
        every { s.send(capture(packets)) } just Runs
        val schedule=ActivityScheduleSystem(times,AdminBypassPolicy(),setOf(117,118))
        fun assertWindow(now: LocalDateTime, expectedStart: LocalDateTime, expectedEnd: LocalDateTime) {
            packets.clear()
            schedule.sendActivityState(s,117,now)
            assertEquals(2,packets.size)
            val dates=PacketReader(packets[0])
            assertEquals(77,dates.readUByte());assertEquals(1,dates.readUByte())
            dates.readUByte()
            assertEquals(117,dates.readUShortLE())
            assertEquals(OleDate.fromLocalDateTime(expectedStart),dates.readDoubleLE())
            assertEquals(OleDate.fromLocalDateTime(expectedEnd),dates.readDoubleLE())
            val state=PacketReader(packets[1])
            assertEquals(77,state.readUByte());assertEquals(2,state.readUByte())
            state.readUByte();assertEquals(117,state.readUShortLE())
            assertEquals(ActivityScheduleSystem.STATE_STARTED,state.readUByte())
        }
        assertWindow(restored,cycle1,cycle2)
        assertWindow(cycle2,cycle2,cycle3)
        assertWindow(cycle3,cycle3,cycle3.plusDays(28))
    }

    @Test fun liveHeartbeatRefreshesScheduleOnlyWhenCycleChanges() {
        val s=mockk<GameSession>(relaxed=true)
        val packets=mutableListOf<ByteArray>()
        every { s.send(capture(packets)) } just Runs
        val schedule=ActivityScheduleSystem(times,AdminBypassPolicy(),setOf(117,118))
        schedule.sendActivityState(s,117,cycle2.minusSeconds(5))
        assertEquals(2,packets.size)
        schedule.refreshPassWindowIfChanged(s,cycle2.minusSeconds(1))
        assertEquals(2,packets.size)
        schedule.refreshPassWindowIfChanged(s,cycle2)
        assertEquals(4,packets.size)
        val dates=PacketReader(packets[2])
        dates.readUByte();dates.readUByte();dates.readUByte()
        assertEquals(117,dates.readUShortLE())
        assertEquals(OleDate.fromLocalDateTime(cycle2),dates.readDoubleLE())
        assertEquals(OleDate.fromLocalDateTime(cycle3),dates.readDoubleLE())
        schedule.refreshPassWindowIfChanged(s,cycle2.plusSeconds(20))
        assertEquals(4,packets.size)
    }

    @Test fun explicitNextSeasonOrModifiedDatDisablesFallback() {
        val original=times.getValue(117)
        val stale=BattlePassSystem.window(original,restored,allowFallback=false)
        assertEquals(LocalDateTime.of(2026,10,13,23,59),stale.end)
        val custom=original.copy(endDay=14)
        assertEquals(LocalDateTime.of(2026,10,14,23,59),
            BattlePassSystem.window(custom,restored).end)
    }
}
