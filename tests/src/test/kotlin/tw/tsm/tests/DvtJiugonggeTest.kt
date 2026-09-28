package tw.tsm.tests

import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.data.loaders.JiugonggeDatLoader
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.gamelogic.systems.BitFlagSystem
import tw.tsm.gamelogic.systems.InventorySystem
import tw.tsm.gamelogic.systems.JiugonggeSystem
import tw.tsm.gamelogic.systems.RoleCountSystem
import tw.tsm.domain.util.OleDate
import java.time.LocalDateTime
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path

class DvtJiugonggeTest {
    private fun existing(vararg parts: String): Path {
        val direct = Path.of(parts.first(), *parts.drop(1).toTypedArray())
        if (Files.exists(direct)) return direct
        val parent = Path.of("..", *parts)
        check(Files.exists(parent)) { "Missing fixture: " + parts.joinToString("/") }
        return parent
    }

    @Test fun `DVT daily static data maps board 1 to exactly nine missions`() {
        val infos = JiugonggeDatLoader.loadInfo(
            Files.readAllBytes(existing("File.dat", "Data-ios", "IOS_JiugonggeInfo_C.dat"))
        )
        val missions = JiugonggeDatLoader.loadMissions(
            Files.readAllBytes(existing("File.dat", "Data-ios", "IOS_JiugonggeMission_C.dat"))
        )
        val times = JiugonggeDatLoader.loadActivityTimes(
            Files.readAllBytes(existing("File.dat", "Data", "ActivityTime_C.dat"))
        )

        assertEquals(6, infos.size)
        assertEquals(55, missions.size)
        assertEquals(12, infos.getValue(1).activityId)
        assertEquals(1, infos.getValue(1).kind)
        assertEquals((2..10).toList(), missions.values
            .filter { it.boardNumber == 1L }
            .sortedBy { it.id }
            .map { it.id })
        assertEquals(127, times.getValue(12).openDayMask)
        assertEquals(0, times.getValue(12).openHour)
        assertEquals(23, times.getValue(12).closeHour)
        assertEquals(59, times.getValue(12).closeMinute)
    }

    @Test fun `login daily init emits S77 runtime time then valid S91 one-grid snapshot`() = runTest {
        val infos = JiugonggeDatLoader.loadInfo(
            Files.readAllBytes(existing("File.dat", "Data-ios", "IOS_JiugonggeInfo_C.dat"))
        )
        val missions = JiugonggeDatLoader.loadMissions(
            Files.readAllBytes(existing("File.dat", "Data-ios", "IOS_JiugonggeMission_C.dat"))
        )
        val times = JiugonggeDatLoader.loadActivityTimes(
            Files.readAllBytes(existing("File.dat", "Data", "ActivityTime_C.dat"))
        )

        val counts = mockk<RoleCountSystem>()
        every { counts.getValue(any(), any()) } returns 0
        val flags = mockk<BitFlagSystem>()
        coEvery { flags.getSetFlagIds(42) } returns emptySet()

        val packets = mutableListOf<ByteArray>()
        val session = mockk<GameSession>(relaxed = true) {
            every { roleId } returns 42
            every { level } returns 21
            every { turn } returns 0
            every { send(capture(packets)) } just Runs
        }

        val system = JiugonggeSystem(
            infos = infos,
            missions = missions,
            activityTimes = times,
            roleCountSystem = counts,
            bitFlagSystem = flags,
            inventorySystem = mockk<InventorySystem>(relaxed = true),
            responseSender = mockk<ResponseSender>(relaxed = true),
            itemDefs = emptyMap(),
        )

        system.sendDailyInit(session)
        assertEquals(3, packets.size)

        ByteBuffer.wrap(packets[0]).order(ByteOrder.LITTLE_ENDIAN).apply {
            assertEquals(77, get().toInt() and 255)
            assertEquals(1, get().toInt() and 255)
            assertEquals(0, get().toInt() and 255)
            assertEquals(12, short.toInt() and 0xFFFF)
            val start = double
            val close = double
            assertEquals(
                OleDate.fromLocalDateTime(LocalDateTime.of(2022, 11, 21, 0, 0)),
                start,
                0.0000001
            )
            assertEquals(
                OleDate.fromLocalDateTime(LocalDateTime.of(2028, 12, 31, 23, 59)),
                close,
                0.0000001
            )
            assertFalse(hasRemaining())
        }

        ByteBuffer.wrap(packets[1]).order(ByteOrder.LITTLE_ENDIAN).apply {
            assertEquals(77, get().toInt() and 255)
            assertEquals(2, get().toInt() and 255)
            assertEquals(0, get().toInt() and 255)
            assertEquals(12, short.toInt() and 0xFFFF)
            assertEquals(1, get().toInt() and 255)
            assertFalse(hasRemaining())
        }

        ByteBuffer.wrap(packets[2]).order(ByteOrder.LITTLE_ENDIAN).apply {
            assertEquals(91, get().toInt() and 255)
            assertEquals(1, get().toInt() and 255)
            assertEquals(0, get().toInt() and 255)
            assertEquals(1, get().toInt() and 255)
            assertEquals(1, short.toInt() and 0xFFFF)
            assertEquals(1, get().toInt() and 255)
            for (missionId in 2..10) {
                assertEquals(missionId, short.toInt() and 0xFFFF)
                int // current condition value
                assertEquals(0, get().toInt() and 255)
            }
            assertFalse(hasRemaining())
        }
    }
}
