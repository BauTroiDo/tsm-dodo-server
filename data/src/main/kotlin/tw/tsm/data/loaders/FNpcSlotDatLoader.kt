package tw.tsm.data.loaders

import tw.tsm.data.DatReader
import tw.tsm.domain.data.FNpcSlotDef

/** DVT IOS_FNpcSlot_C.dat: count(I32) + [index(U16), exp(U32), icons(3xU8)]. */
object FNpcSlotDatLoader {
    fun load(data: ByteArray): Map<Int, FNpcSlotDef> {
        val reader = DatReader(data)
        val count = reader.readInt32()
        val result = LinkedHashMap<Int, FNpcSlotDef>(count)
        repeat(count) {
            val index = reader.readUInt16()
            val exp = reader.readUInt32().coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            val icons = List(3) { reader.readByte() }
            if (index > 0) result[index] = FNpcSlotDef(index, exp, icons)
        }
        return result
    }
}
