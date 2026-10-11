package tw.tsm.tests

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.app.handlers.ChildPackets
import tw.tsm.domain.valueobjects.ChildDataSnapshot

/** DVT Data/HumanData.lua ChildData.New + Common/protocal.lua S069-007/009. */
class DvtChildDataWirePersistenceContractTest {
    private fun childBytes(): ByteArray {
        val b = ByteBuffer.allocate(93).order(ByteOrder.LITTLE_ENDIAN)
        b.put(1) // Foster, synthetic test only (never written to live DB)
        b.put(2) // female
        b.put(0) // turn
        b.put(3) // fire
        repeat(10) { index ->
            b.putShort((400 + index).toShort())
            b.put((index % 5 + 1).toByte())
        }
        b.putDouble(45307.25)
        b.putShort(48)
        b.putDouble(45307.75)
        b.putShort(47)
        repeat(6) { b.putShort((100 + it).toShort()) }
        repeat(6) { b.put((it + 10).toByte()) }
        b.putShort(399)
        b.put(2)
        repeat(18) { b.put((0xa0 + it).toByte()) }
        assertEquals(93, b.position())
        return b.array()
    }

    @Test fun exact93ByteDecodeAndOpaquePersistenceRoundTrip() {
        val original = childBytes()
        val snapshot = ChildDataSnapshot.decode(original)
        assertEquals(1, snapshot.kind)
        assertEquals(2, snapshot.sex)
        assertEquals(0, snapshot.turn)
        assertEquals(3, snapshot.element)
        assertEquals(10, snapshot.skills.size)
        assertEquals(400, snapshot.skills[0].id)
        assertEquals(1, snapshot.skills[0].level)
        assertEquals(409, snapshot.skills[9].id)
        assertEquals(45307.25, snapshot.lastFeedingTime)
        assertEquals(48, snapshot.stodge)
        assertEquals(45307.75, snapshot.lastSatiationTime)
        assertEquals(47, snapshot.satiation)
        assertEquals(listOf(100, 101, 102, 103, 104, 105), snapshot.attributes)
        assertEquals(listOf(10, 11, 12, 13, 14, 15), snapshot.attributeExperience)
        assertEquals(399, snapshot.childItemCount)
        assertEquals(2, snapshot.career)
        assertArrayEquals(original, snapshot.toBytes())
        original[0] = 4
        assertEquals(1, snapshot.kind)
        val leaked = snapshot.toBytes()
        leaked[1] = 0
        assertArrayEquals(childBytes(), snapshot.toBytes())
    }

    @Test fun originalS069SnapshotAndFollowSnapshotFraming() {
        val original = childBytes()
        val snapshot = ChildDataSnapshot.decode(original)
        val packet = ChildPackets.snapshot(snapshot)
        assertEquals(98, packet.size)
        assertArrayEquals(byteArrayOf(69, 7, 0, 93, 0), packet.copyOfRange(0, 5))
        assertArrayEquals(original, packet.copyOfRange(5, packet.size))
        val followPacket = ChildPackets.deployed(3, snapshot)
        assertEquals(99, followPacket.size)
        assertArrayEquals(byteArrayOf(69, 9, 0, 3, 93, 0), followPacket.copyOfRange(0, 6))
        assertArrayEquals(original, followPacket.copyOfRange(6, followPacket.size))
        assertThrows(IllegalArgumentException::class.java) { ChildPackets.deployed(0, snapshot) }
    }

    @Test fun invalidPayloadNeverCreatesOrUnlocksAChild() {
        assertThrows(IllegalArgumentException::class.java) { ChildDataSnapshot.decode(ByteArray(92)) }
        assertThrows(IllegalArgumentException::class.java) { ChildDataSnapshot.decode(ByteArray(94)) }
        val unknown = childBytes()
        unknown[0] = 5
        assertThrows(IllegalArgumentException::class.java) { ChildDataSnapshot.decode(unknown) }
        val malformed = childBytes()
        ByteBuffer.wrap(malformed).order(ByteOrder.LITTLE_ENDIAN).putDouble(34, Double.NaN)
        assertThrows(IllegalArgumentException::class.java) { ChildDataSnapshot.decode(malformed) }
    }
}
