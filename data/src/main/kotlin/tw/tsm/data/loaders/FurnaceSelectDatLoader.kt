package tw.tsm.data.loaders

import tw.tsm.data.DatReader
import tw.tsm.domain.data.FurnaceSelectDef

/** Loader đúng DVT FurnaceSelectData.New(reader). */
object FurnaceSelectDatLoader {
    fun load(data: ByteArray): List<FurnaceSelectDef> {
        val reader = DatReader(data)
        val count = reader.readInt32()
        val result = ArrayList<FurnaceSelectDef>(count)
        repeat(count) {
            result += FurnaceSelectDef(
                id = reader.readUInt16(),
                kind = reader.readByte(),
                sample = reader.readUInt16(),
                awardCounts = List(6) { reader.readByte() },
            )
        }
        require(!reader.canRead()) { "IOS_FurnaceSelect_C.dat còn trailing bytes" }
        return result
    }
}
