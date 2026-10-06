package tw.tsm.data.loaders

import tw.tsm.data.DatReader
import tw.tsm.domain.data.PokeBonusRewardDef
import tw.tsm.domain.data.PokeRewardDef

object PokeDatLoader {
    /** IOS_PokeData_C.dat: count + id(U16)+kind(U8)+itemId(U16)+count(U32). */
    fun loadRewards(data: ByteArray): List<PokeRewardDef> {
        val r = DatReader(data)
        val count = r.readInt32()
        require(data.size.toLong() == 4L + count.toLong() * 9L)
        val rows = List(count) {
            PokeRewardDef(
                id = r.readUInt16(),
                kind = r.readByte(),
                itemId = r.readUInt16(),
                count = r.readUInt32().toInt(),
            )
        }
        require(rows.map { it.id }.toSet().size == rows.size)
        require(rows.all { it.id in 1..255 && it.kind in 1..4 && it.itemId > 0 && it.count > 0 })
        return rows
    }

    /** IOS_PokeRate_C.dat: count + kind(U8)+weight(U8). */
    fun loadRates(data: ByteArray): Map<Int, Int> {
        val r = DatReader(data)
        val count = r.readInt32()
        require(data.size.toLong() == 4L + count.toLong() * 2L)
        val result = linkedMapOf<Int, Int>()
        repeat(count) {
            val kind = r.readByte()
            val weight = r.readByte()
            require(kind in 1..4 && weight > 0)
            result[kind] = weight
        }
        return result
    }

    /** IOS_PokeBonusReward_C.dat: count + boardIndex(U8)+id(U8)+quantPlus(U8). */
    fun loadBonusRewards(data: ByteArray): List<PokeBonusRewardDef> {
        val r = DatReader(data)
        val count = r.readInt32()
        require(data.size.toLong() == 4L + count.toLong() * 3L)
        return List(count) {
            PokeBonusRewardDef(
                boardIndex = r.readByte(),
                id = r.readByte(),
                quantPlus = r.readByte(),
            )
        }
    }
}
