package tw.tsm.tests

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tw.tsm.domain.valueobjects.MythAttribute
import tw.tsm.domain.valueobjects.ThingData
import tw.tsm.protocol.PacketReader
import tw.tsm.protocol.PacketWriter
import tw.tsm.protocol.ThingDataWire
import tw.tsm.protocol.readThingData
import tw.tsm.protocol.writeThingData

class MythThingDataD7RegressionTest {
    @Test
    fun mythThingDataRoundTripsExactDvt56ByteLayout() {
        val original = ThingData(
            itemId = 50001,
            quantity = 1,
            damage = 2,
            element = 3,
            elementValue = 4,
            proofKind = 5,
            growLevel = 6,
            growExp = 700,
            specialKind = 1,
            stoneAttr = 8,
            stoneLevel = 9,
            enhanceLevel = 10,
            deleteTime = 123.5,
            damagedItemId = 50000,
            isLocked = true,
            reinforced = 2,
            affix1 = 3,
            affix2 = 4,
            affix3 = 5,
            styleLevel = 6,
            mythLevel = 7,
            myth = listOf(
                MythAttribute(101, 11, 1),
                MythAttribute(102, 22, 2),
                MythAttribute(105, 33, 3),
                MythAttribute(106, 44, 4)
            )
        )

        val writer = PacketWriter().writeThingData(original)
        val bytes = writer.toByteArray()
        assertEquals(ThingDataWire.SIZE, bytes.size)

        val reader = PacketReader(bytes)
        val decoded = reader.readThingData()
        assertEquals(original, decoded)
        assertEquals(0, reader.remaining)
    }
}
