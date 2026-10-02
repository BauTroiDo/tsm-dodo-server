package tw.tsm.data.loaders

import tw.tsm.data.DatReader
import tw.tsm.domain.data.FurnaceSlotDef

/** Loader đúng DVT FurnaceSlotData.New(reader). */
object FurnaceSlotDatLoader {
    fun load(data: ByteArray): List<FurnaceSlotDef> {
        val reader = DatReader(data)
        val count = reader.readInt32()
        val result = ArrayList<FurnaceSlotDef>(count)
        repeat(count) {
            result += FurnaceSlotDef(
                id = reader.readUInt16(),
                kind = reader.readByte(),
                awardKind = reader.readByte(),
                itemId = reader.readUInt16(),
                timedOpen = reader.readByte(),
                openYear = reader.readUInt16(),
                openMonth = reader.readByte(),
                openDay = reader.readByte(),
                closeYear = reader.readUInt16(),
                closeMonth = reader.readByte(),
                closeDay = reader.readByte(),
            )
        }
        require(!reader.canRead()) { "IOS_FurnaceSlot_C.dat còn trailing bytes" }
        return result
    }
}
