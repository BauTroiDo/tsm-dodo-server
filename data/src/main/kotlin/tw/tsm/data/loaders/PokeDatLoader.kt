package tw.tsm.data.loaders

import tw.tsm.data.DatReader
import tw.tsm.domain.data.PokeCandidate

object PokeDatLoader {
    fun loadCandidates(data: ByteArray): List<PokeCandidate> {
        val r = DatReader(data)
        val count = r.readInt32()
        require(count in 1..255)
        require(data.size == 4 + count * 9)
        val rows = List(count) {
            PokeCandidate(r.readUInt16(), r.readByte(), r.readUInt16(), r.readUInt32().toInt())
        }
        require(rows.map { it.id }.toSet().size == rows.size)
        require(rows.all { it.id in 1..255 && it.kind in 1..4 && it.itemId > 0 && it.count > 0 })
        return rows
    }

    fun loadRates(data: ByteArray): Map<Int, Int> {
        val r = DatReader(data)
        val count = r.readInt32()
        require(data.size == 4 + count * 2)
        val result = linkedMapOf<Int, Int>()
        repeat(count) {
            val kind = r.readByte()
            val weight = r.readByte()
            require(kind in 1..4 && weight > 0 && kind !in result)
            result[kind] = weight
        }
        require(result.keys == setOf(1, 2, 3, 4))
        return result
    }
}
