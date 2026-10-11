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

    @Test fun authentic399To400AdulthoodTransitionAndS069Updates() {
        val foster = ChildDataSnapshot.decode(childBytes())
        val before = foster.toBytes()
        assertEquals(399, foster.childItemCount)
        assertEquals(1, foster.kind)
        assertArrayEquals(before, foster.withItemCount(399).toBytes())
        assertThrows(IllegalArgumentException::class.java) { foster.withItemCount(398) }

        val adult = foster.withItemCount(400)
        assertEquals(3, adult.kind)
        assertEquals(400, adult.childItemCount)
        assertEquals(399, foster.childItemCount)
        assertEquals(1, foster.kind) // immutable original
        assertEquals(3, adult.toBytes()[0].toInt() and 0xff)
        assertEquals(0x90, adult.toBytes()[72].toInt() and 0xff)
        assertEquals(0x01, adult.toBytes()[73].toInt() and 0xff)
        assertArrayEquals(before.copyOfRange(1, 72), adult.toBytes().copyOfRange(1, 72))
        assertArrayEquals(before.copyOfRange(74, 93), adult.toBytes().copyOfRange(74, 93))
        assertArrayEquals(byteArrayOf(69, 8, 0, 0x90.toByte(), 1),
            ChildPackets.itemUseTotal(adult))
        assertArrayEquals(byteArrayOf(69, 11, 0, 3),
            ChildPackets.lifecycleKind(adult))

        val max = adult.withItemCount(65535)
        assertEquals(65535, max.childItemCount)
        assertEquals(3, max.kind)
        assertArrayEquals(byteArrayOf(69, 8, 0, 0xff.toByte(), 0xff.toByte()),
            ChildPackets.itemUseTotal(max))
        assertThrows(IllegalArgumentException::class.java) { adult.withItemCount(65536) }

        val absentBytes = childBytes()
        absentBytes[0] = 0
        val absent = ChildDataSnapshot.decode(absentBytes)
        assertThrows(IllegalArgumentException::class.java) { absent.withItemCount(400) }
    }

    @Test fun originalDvtS069CareUpdatesPreserve93BytesAndZeroFullnessCausesDeath() {
        val foster = ChildDataSnapshot.decode(childBytes())
        val original = foster.toBytes()
        assertArrayEquals(byteArrayOf(69, 3, 0, 48, 0), ChildPackets.stodge(foster))
        assertArrayEquals(byteArrayOf(69, 4, 0, 47, 0), ChildPackets.satiation(foster))
        assertArrayEquals(original, foster.withCare(stodge = 48, satiation = 47).toBytes())

        val dead = foster.withCare(stodge = 0)
        assertEquals(2, dead.kind)
        assertEquals(0, dead.stodge)
        assertEquals(47, dead.satiation)
        assertArrayEquals(byteArrayOf(69, 3, 0, 0, 0), ChildPackets.stodge(dead))
        assertArrayEquals(byteArrayOf(69, 11, 0, 2), ChildPackets.lifecycleKind(dead))
        assertArrayEquals(original.copyOfRange(1, 42), dead.toBytes().copyOfRange(1, 42))
        assertArrayEquals(original.copyOfRange(44, 93), dead.toBytes().copyOfRange(44, 93))
        assertEquals(2, dead.withCare(stodge = 48).kind,
            "Fullness alone cannot revive a dead child")

        val onlySatisfaction = foster.withCare(satiation = 0)
        assertEquals(1, onlySatisfaction.kind,
            "DVT S069-004 does not kill a child when satisfaction reaches zero")
        assertEquals(48, onlySatisfaction.stodge)
        assertEquals(0, onlySatisfaction.satiation)
        assertArrayEquals(original.copyOfRange(0, 52),
            onlySatisfaction.toBytes().copyOfRange(0, 52))
        assertArrayEquals(original.copyOfRange(54, 93),
            onlySatisfaction.toBytes().copyOfRange(54, 93))

        val changed = foster.withCare(stodge = 300, satiation = 500)
        assertEquals(300, changed.stodge)
        assertEquals(500, changed.satiation)
        assertArrayEquals(byteArrayOf(69, 3, 0, 44, 1), ChildPackets.stodge(changed))
        assertArrayEquals(byteArrayOf(69, 4, 0, -12, 1), ChildPackets.satiation(changed))
        assertArrayEquals(original.copyOfRange(54, 93),
            changed.toBytes().copyOfRange(54, 93))
        assertEquals(48, foster.stodge)
        assertEquals(47, foster.satiation)

        assertThrows(IllegalArgumentException::class.java) { foster.withCare() }
        assertThrows(IllegalArgumentException::class.java) { foster.withCare(stodge = -1) }
        assertThrows(IllegalArgumentException::class.java) { foster.withCare(satiation = 65536) }
        val none = childBytes().also { it[0] = 0 }
        assertThrows(IllegalArgumentException::class.java) {
            ChildDataSnapshot.decode(none).withCare(stodge = 0)
        }
    }

    @Test fun invalidPayloadNeverCreatesOrUnlocksAChild() {
        assertThrows(IllegalArgumentException::class.java) { ChildDataSnapshot.decode(ByteArray(92)) }
        assertThrows(IllegalArgumentException::class.java) { ChildDataSnapshot.decode(ByteArray(94)) }
        val unknown = childBytes()
        unknown[0] = 4
        assertThrows(IllegalArgumentException::class.java) { ChildDataSnapshot.decode(unknown) }
        val malformed = childBytes()
        ByteBuffer.wrap(malformed).order(ByteOrder.LITTLE_ENDIAN).putDouble(34, Double.NaN)
        assertThrows(IllegalArgumentException::class.java) { ChildDataSnapshot.decode(malformed) }
    }
}
