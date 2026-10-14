package tw.tsm.data.loaders

import java.nio.ByteBuffer
import java.nio.ByteOrder
import tw.tsm.domain.data.HouseExpandConditionDef

/** Mirrors Lua HouseExpandConditionData.New with strict five-byte rows. */
object HouseExpandConditionDatLoader {
    fun load(bytes: ByteArray): Map<Int, HouseExpandConditionDef> {
        require(bytes.size >= 4)
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val count = bb.int
        require(count == 12 && bb.remaining() == count * 5) {
            "DVT home expansion expected 12 five-byte rows"
        }
        val data = linkedMapOf<Int, HouseExpandConditionDef>()
        repeat(count) {
            fun u8() = bb.get().toInt() and 0xff
            val row = HouseExpandConditionDef(u8(), u8(), u8(), u8(), u8())
            require(row.level in 1..12 && row.level !in data)
            require(row.building in 1..4 && row.floorLevel in 0..2)
            require(row.level == (row.building - 1) * 3 + row.floorLevel + 1)
            require(row.furnitureKindsRequired in 0..255 && row.goldCost >= 0)
            data[row.level] = row
        }
        require(data.keys == (1..12).toSet() && data.getValue(1).goldCost == 0)
        return data
    }
}
