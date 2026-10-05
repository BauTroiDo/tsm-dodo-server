package tw.tsm.tests

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tw.tsm.data.loaders.JiugonggeDatLoader
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.gamelogic.systems.ActivityScheduleSystem
import tw.tsm.protocol.PacketReader
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime

class DvtJudgmentTowerActivityTest {
    private fun fixture(name: String): ByteArray {
        val direct = Path.of("File.dat", "Data-ios", name)
        val path = if (Files.exists(direct)) direct else Path.of("..", "File.dat", "Data-ios", name)
        return Files.readAllBytes(path)
    }

    @Test
    fun judgmentTowerSchedule() {
        val defs = JiugonggeDatLoader.loadActivityTimes(fixture("IOS_ActivityTime_C.dat"))
        val def = defs.getValue(ActivityScheduleSystem.JUDGMENT_TOWER_ACTIVITY_ID)
        assertEquals(64, def.openDayMask)
        assertEquals(8, def.openHour)
        assertEquals(0, def.openMinute)
        assertEquals(23, def.closeHour)
        assertEquals(0, def.closeMinute)

        val system = ActivityScheduleSystem(defs)
        assertEquals(ActivityScheduleSystem.STATE_STARTED, system.calculateState(def, LocalDateTime.of(2026, 10, 11, 9, 0)))
        assertEquals(ActivityScheduleSystem.STATE_NONE, system.calculateState(def, LocalDateTime.of(2026, 10, 5, 9, 0)))
        assertEquals(ActivityScheduleSystem.STATE_NONE, system.calculateState(def, LocalDateTime.of(2026, 10, 11, 7, 59)))
        assertEquals(ActivityScheduleSystem.STATE_ENDED, system.calculateState(def, LocalDateTime.of(2026, 10, 11, 23, 1)))
    }

    @Test
    fun s77RuntimePackets() {
        val defs = JiugonggeDatLoader.loadActivityTimes(fixture("IOS_ActivityTime_C.dat"))
        val system = ActivityScheduleSystem(defs)
        val packets = mutableListOf<ByteArray>()
        val session = mockk<GameSession>()
        every { session.gmLevel } returns 0
        every { session.send(capture(packets)) } just runs

        system.sendActivityState(session, ActivityScheduleSystem.JUDGMENT_TOWER_ACTIVITY_ID, LocalDateTime.of(2026, 10, 11, 9, 0))
        assertEquals(2, packets.size)

        PacketReader(packets[0]).let { r ->
            assertEquals(77, r.readUByte())
            assertEquals(1, r.readUByte())
            assertEquals(0, r.readUByte())
            assertEquals(1, r.readUShortLE())
            r.readDoubleLE()
            r.readDoubleLE()
            assertEquals(0, r.remaining)
        }
        PacketReader(packets[1]).let { r ->
            assertEquals(77, r.readUByte())
            assertEquals(2, r.readUByte())
            assertEquals(0, r.readUByte())
            assertEquals(1, r.readUShortLE())
            assertEquals(1, r.readUByte())
            assertEquals(0, r.remaining)
        }
    }

    @Test
    fun allActivitySchedulesAreSentAndGmSeesThemStarted() {
        val defs = JiugonggeDatLoader.loadActivityTimes(fixture("IOS_ActivityTime_C.dat"))
        val system = ActivityScheduleSystem(defs)
        val packets = mutableListOf<ByteArray>()
        val gm = mockk<GameSession>()
        every { gm.gmLevel } returns 99
        every { gm.send(capture(packets)) } just runs

        system.sendAllActivityStates(gm, LocalDateTime.of(2026, 10, 5, 21, 0))

        val enabled = defs.values.count { it.switchKind >= 1 }
        assertEquals(enabled * 2, packets.size)
        val statePackets = packets.filter { p -> p.size >= 6 && (p[0].toInt() and 0xFF) == 77 && (p[1].toInt() and 0xFF) == 2 }
        assertEquals(enabled, statePackets.size)
        statePackets.forEach { p ->
            PacketReader(p).let { r ->
                assertEquals(77, r.readUByte())
                assertEquals(2, r.readUByte())
                assertEquals(0, r.readUByte())
                r.readUShortLE()
                assertEquals(ActivityScheduleSystem.STATE_STARTED, r.readUByte())
            }
        }
    }

    @Test
    fun gmBypassMakesClosedActivityStartedWithoutChangingNormalRules() {
        val defs = JiugonggeDatLoader.loadActivityTimes(fixture("IOS_ActivityTime_C.dat"))
        val system = ActivityScheduleSystem(defs)
        val monday = LocalDateTime.of(2026, 10, 5, 21, 0)

        val normalPackets = mutableListOf<ByteArray>()
        val normal = mockk<GameSession>()
        every { normal.gmLevel } returns 0
        every { normal.send(capture(normalPackets)) } just runs
        system.sendActivityState(normal, ActivityScheduleSystem.JUDGMENT_TOWER_ACTIVITY_ID, monday)
        PacketReader(normalPackets[1]).let { r ->
            r.readUByte(); r.readUByte(); r.readUByte(); r.readUShortLE()
            assertEquals(ActivityScheduleSystem.STATE_NONE, r.readUByte())
        }

        val gmPackets = mutableListOf<ByteArray>()
        val gm = mockk<GameSession>()
        every { gm.gmLevel } returns 1
        every { gm.send(capture(gmPackets)) } just runs
        system.sendActivityState(gm, ActivityScheduleSystem.JUDGMENT_TOWER_ACTIVITY_ID, monday)
        PacketReader(gmPackets[1]).let { r ->
            r.readUByte(); r.readUByte(); r.readUByte(); r.readUShortLE()
            assertEquals(ActivityScheduleSystem.STATE_STARTED, r.readUByte())
        }
    }
}
