package tw.tsm.tests

import io.mockk.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tw.tsm.app.response.InventoryResponseSender
import tw.tsm.domain.interfaces.SessionSender
import tw.tsm.domain.interfaces.repositories.FlyPointRecord
import tw.tsm.protocol.PacketReader

class DvtTeleportMemoryTest {

    @Test
    fun `S023-118 initializes every default memory slot including empty ones`() {
        val packets = mutableListOf<ByteArray>()
        val session = mockk<SessionSender> {
            every { send(capture(packets)) } just Runs
        }

        InventoryResponseSender().sendFlyPointData(
            session = session,
            flyPointCount = 0,
            points = mapOf(
                2 to FlyPointRecord(index = 2, sceneId = 12003, x = 70, y = 580),
            ),
        )

        val reader = PacketReader(packets.single())
        assertEquals(23, reader.readUByte())
        assertEquals(118, reader.readUByte())
        assertEquals(0, reader.readUByte())
        assertEquals(0, reader.readUByte()) // no extended slots purchased
        assertEquals(5, reader.readUByte()) // five default slots must all exist client-side

        for (index in 1..5) {
            assertEquals(index, reader.readUByte())
            if (index == 2) {
                assertEquals(12003, reader.readUShortLE())
                assertEquals(70, reader.readUShortLE())
                assertEquals(580, reader.readUShortLE())
            } else {
                assertEquals(0, reader.readUShortLE())
                assertEquals(0, reader.readUShortLE())
                assertEquals(0, reader.readUShortLE())
            }
        }
        assertEquals(0, reader.remaining)
    }

    @Test
    fun `S023-118 includes only purchased extension slots and clamps extension count`() {
        val packets = mutableListOf<ByteArray>()
        val session = mockk<SessionSender> {
            every { send(capture(packets)) } just Runs
        }

        InventoryResponseSender().sendFlyPointData(
            session = session,
            flyPointCount = 99,
            points = mapOf(
                10 to FlyPointRecord(index = 10, sceneId = 12421, x = 33, y = 44),
                11 to FlyPointRecord(index = 11, sceneId = 9999, x = 1, y = 1),
            ),
        )

        val reader = PacketReader(packets.single())
        assertEquals(23, reader.readUByte())
        assertEquals(118, reader.readUByte())
        assertEquals(0, reader.readUByte())
        assertEquals(5, reader.readUByte()) // Lua MaxFlyScrollExtend
        assertEquals(10, reader.readUByte())

        repeat(9) {
            val index = it + 1
            assertEquals(index, reader.readUByte())
            assertEquals(0, reader.readUShortLE())
            assertEquals(0, reader.readUShortLE())
            assertEquals(0, reader.readUShortLE())
        }

        assertEquals(10, reader.readUByte())
        assertEquals(12421, reader.readUShortLE())
        assertEquals(33, reader.readUShortLE())
        assertEquals(44, reader.readUShortLE())
        assertEquals(0, reader.remaining)
    }
}
