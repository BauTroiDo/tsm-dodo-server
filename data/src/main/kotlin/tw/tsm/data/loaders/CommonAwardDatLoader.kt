package tw.tsm.data.loaders

import tw.tsm.data.DatReader
import tw.tsm.domain.data.*

object CommonAwardDatLoader {
    fun load(data: ByteArray): Map<Int, List<CommonAwardEntry>> {
        val reader = DatReader(data)
        val count = reader.readInt32()
        require(count >= 0 && data.size.toLong() == 4L + count.toLong() * 44L) { "Invalid CommonAward payload" }
        return buildMap {
            repeat(count) {
                val id = reader.readUInt16()
                put(id, List(6) { CommonAwardEntry(reader.readByte(), reader.readUInt16(), reader.readUInt32()) })
            }
        }
    }
    fun loadLuckyBags(data: ByteArray): Map<Int, LuckyBagDef> {
        val reader = DatReader(data)
        val count = reader.readInt32()
        require(count >= 0 && data.size.toLong() == 4L + count.toLong() * 218L) { "Invalid LuckyBag payload" }
        return buildMap {
            repeat(count) {
                val id = reader.readUInt16()
                val name = reader.readUInt32()
                val icon = reader.readUInt16()
                val entries = List(30) { LuckyBagEntry(reader.readByte(), reader.readUInt16(), reader.readUInt32()) }
                put(id, LuckyBagDef(name, icon, entries))
            }
        }
    }
}
