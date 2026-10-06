package tw.tsm.data.loaders

import tw.tsm.data.DatReader
import tw.tsm.domain.data.PassAwardDef
import tw.tsm.domain.data.PassReward

object PassAwardDatLoader {
    fun load(data: ByteArray): List<PassAwardDef> {
        require(data.size >= 4)
        val r = DatReader(data)
        val count = r.readInt32()
        require(count >= 0 && data.size.toLong() == 4L + count.toLong() * 22)
        val rows = List(count) {
            val id = r.readByte()
            val level = r.readByte()
            val exp = r.readUInt16()
            PassAwardDef(id, level, exp, List(3) { PassReward(r.readUInt16(), r.readUInt32()) })
        }
        rows.groupBy { it.seasonId }.values.forEach { season ->
            require(season.first().seasonId > 0)
            require(season.map { it.level } == (1..season.size).toList())
            require(season.first().totalExp > 0 && season.zipWithNext().all { (a,b) -> a.totalExp < b.totalExp })
            require(season.all { row -> row.rewards.all { it.itemId == 0 || (it.itemId > 0 && it.quantity in 1..Int.MAX_VALUE.toLong()) } })
        }
        return rows
    }
}
