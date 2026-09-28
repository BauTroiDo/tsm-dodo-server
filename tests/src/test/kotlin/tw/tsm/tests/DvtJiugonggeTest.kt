package tw.tsm.tests

import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.data.loaders.JiugonggeDatLoader
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.domain.valueobjects.ThingData
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
        val daily = missions.values
            .filter { it.boardNumber == 1L }
            .sortedBy { it.id }
        assertEquals((2..10).toList(), daily.map { it.id })
        assertEquals(
            listOf(
                listOf(14L, 160L, 3L, 1L),    // first mount
                listOf(14L, 151L, 3L, 2L),    // catch warrior x2
                listOf(17L, 1499L, 3L, 1L),   // deploy warrior
                listOf(14L, 190L, 3L, 10L),   // spend skill points
                listOf(14L, 191L, 3L, 20L),   // spend attribute points
                listOf(14L, 163L, 3L, 50L),   // fight
                listOf(18L, 0L, 3L, 5L),      // max friend count
                listOf(17L, 1498L, 3L, 1L),   // first party
                listOf(17L, 1497L, 3L, 1L),   // join organization
            ),
            daily.map {
                listOf(
                    it.conditionKind.toLong(),
                    it.conditionKindValue,
                    it.conditionOperator.toLong(),
                    it.conditionValue,
                )
            }
        )
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

        val dayBefore = LocalDateTime.now().toLocalDate()
        system.sendDailyInit(session)
        val dayAfter = LocalDateTime.now().toLocalDate()
        assertEquals(3, packets.size)

        ByteBuffer.wrap(packets[0]).order(ByteOrder.LITTLE_ENDIAN).apply {
            assertEquals(77, get().toInt() and 255)
            assertEquals(1, get().toInt() and 255)
            assertEquals(0, get().toInt() and 255)
            assertEquals(12, short.toInt() and 0xFFFF)
            val start = double
            val close = double
            val validStarts = setOf(dayBefore, dayAfter).map {
                OleDate.fromLocalDateTime(it.atTime(0, 0))
            }
            val validCloses = setOf(dayBefore, dayAfter).map {
                OleDate.fromLocalDateTime(it.atTime(23, 59))
            }
            assertTrue(validStarts.any { kotlin.math.abs(it - start) < 0.0000001 })
            assertTrue(validCloses.any { kotlin.math.abs(it - close) < 0.0000001 })
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

    @Test fun `first completed row claims board 1 item reward and acknowledgement`() = runTest {
        val infos = JiugonggeDatLoader.loadInfo(
            Files.readAllBytes(existing("File.dat", "Data-ios", "IOS_JiugonggeInfo_C.dat"))
        )
        val missions = JiugonggeDatLoader.loadMissions(
            Files.readAllBytes(existing("File.dat", "Data-ios", "IOS_JiugonggeMission_C.dat"))
        )

        val counts = mockk<RoleCountSystem>()
        every { counts.getValue(42, any()) } answers {
            when (secondArg<Int>()) {
                160 -> 1
                151 -> 2
                else -> 0
            }
        }
        every { counts.getBagCapacity(42) } returns 50

        val flags = mockk<BitFlagSystem>()
        coEvery { flags.getSetFlagIds(42) } returns setOf(1499)
        coJustRun { flags.setFlag(42, 1541) }

        val inventory = mockk<InventorySystem>()
        every { inventory.getBag(42) } returns emptyMap()
        val rewardItems = listOf(
            1 to ThingData(itemId = 31207, quantity = 1),
            2 to ThingData(itemId = 31207, quantity = 1),
        )
        coEvery { inventory.addItemsToBag(42, 31207, 2) } returns rewardItems

        val sender = mockk<ResponseSender>(relaxed = true)
        val packets = mutableListOf<ByteArray>()
        val session = mockk<GameSession>(relaxed = true) {
            every { roleId } returns 42
            every { send(capture(packets)) } just Runs
        }

        val system = JiugonggeSystem(
            infos = infos,
            missions = missions,
            activityTimes = emptyMap(),
            roleCountSystem = counts,
            bitFlagSystem = flags,
            inventorySystem = inventory,
            responseSender = sender,
            itemDefs = emptyMap(),
        )

        assertTrue(system.claimReward(session, gridId = 1, index = 1, missionId = 2))
        coVerify(exactly = 1) { inventory.addItemsToBag(42, 31207, 2) }
        coVerify(exactly = 1) { flags.setFlag(42, 1541) }
        verify(exactly = 1) {
            sender.sendBitFlagChanges(session, listOf(1541 to true))
        }

        assertEquals(1, packets.size)
        ByteBuffer.wrap(packets.single()).order(ByteOrder.LITTLE_ENDIAN).apply {
            assertEquals(91, get().toInt() and 0xFF)
            assertEquals(3, get().toInt() and 0xFF)
            assertEquals(0, get().toInt() and 0xFF)
            assertEquals(1, get().toInt() and 0xFF)
            assertEquals(1, short.toInt() and 0xFFFF)
            assertEquals(1, get().toInt() and 0xFF)
            assertFalse(hasRemaining())
        }
    }
}
