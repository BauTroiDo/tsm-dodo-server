package tw.tsm.tests

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.app.response.BattleResponseSender
import tw.tsm.app.response.NpcEventResponseSender
import tw.tsm.domain.constants.EAttribute
import tw.tsm.domain.interfaces.SessionSender
import tw.tsm.domain.interfaces.repositories.MissionFlagRecord
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Client in-session function icon showLv/requireLv and showFlag/requireFlag
 * refresh happens only after the original DVT S008-001 and S024-005 packets.
 * These tests safeguard exact wire parity; never forge a marker or lower its
 * UiOpen_C requirement.
 */
class DvtLiveIconUnlockWireRegressionTest {
    private class Capture : SessionSender {
        val packets = mutableListOf<ByteArray>()
        override fun send(data: ByteArray) { packets += data }
        override fun close() {}
    }

    @Test
    fun absoluteLevelThresholdCrossingUsesSourceS008001PacketLayout() {
        val capture = Capture()
        val sender = BattleResponseSender()
        sender.sendSetAttribute(capture, EAttribute.LV, 10, true, 0)
        sender.sendSetAttribute(capture, EAttribute.LV, 15, true, 0)
        sender.sendSetAttribute(capture, EAttribute.LV, 60, true, 0)

        assertEquals(3, capture.packets.size)
        for ((i, level) in listOf(10, 15, 60).withIndex()) {
            val packet = capture.packets[i]
            assertEquals(13, packet.size)
            val buffer = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN)
            assertEquals(8, buffer.get().toInt() and 255)
            assertEquals(1, buffer.get().toInt() and 255)
            assertEquals(0, buffer.get().toInt() and 255)
            assertEquals(EAttribute.LV, buffer.get().toInt() and 255)
            assertEquals(1, buffer.get().toInt() and 255)
            assertEquals(level, buffer.int)
            assertEquals(0, buffer.int)
            assertFalse(buffer.hasRemaining())
        }
    }

    @Test
    fun incrementalMissionFlagPacketHasOriginalS024005Encoding() {
        val capture = Capture()
        val sender = NpcEventResponseSender()
        val flags = mapOf(936 to 1, 1375 to 1, 1376 to 1, 1434 to 1)
        sender.sendSetMissionFlags(
            capture,
            flags.entries.map { MissionFlagRecord(it.key, it.value) }
        )
        val p = capture.packets.single()
        assertEquals(3 + 4 + flags.size * 3, p.size)
        val rd = ByteBuffer.wrap(p).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(24, rd.get().toInt() and 255)
        assertEquals(5, rd.get().toInt() and 255)
        assertEquals(0, rd.get().toInt() and 255)
        assertEquals(4, rd.int)
        for ((bit, enabled) in flags) {
            assertEquals(bit, rd.short.toInt() and 0xffff)
            assertEquals(enabled, rd.get().toInt() and 255)
        }
        assertFalse(rd.hasRemaining())
    }

    @Test
    fun flagRemovalAndRedeliveryAreSentAsBooleanNotNewLevel() {
        val capture = Capture()
        val sender = NpcEventResponseSender()
        sender.sendSetMissionFlags(capture, listOf(MissionFlagRecord(936, 0)))
        sender.sendSetMissionFlags(capture, listOf(MissionFlagRecord(936, 1)))
        assertEquals(2, capture.packets.size)
        for ((i, value) in listOf(0, 1).withIndex()) {
            val rd = ByteBuffer.wrap(capture.packets[i]).order(ByteOrder.LITTLE_ENDIAN)
            assertEquals(24, rd.get().toInt() and 255)
            assertEquals(5, rd.get().toInt() and 255)
            assertEquals(0, rd.get().toInt() and 255)
            assertEquals(1, rd.int)
            assertEquals(936, rd.short.toInt() and 0xffff)
            assertEquals(value, rd.get().toInt() and 255)
            assertFalse(rd.hasRemaining())
        }
    }
}
