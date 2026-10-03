package tw.tsm.data.loaders

import tw.tsm.data.DatReader
import tw.tsm.domain.data.DungeonComboDef
import tw.tsm.domain.data.DungeonComboReward

/** DVT DataManager.OnLoadDungeonComboData preserves all duplicate table IDs in file order. */
object DungeonComboDatLoader {
    fun load(data: ByteArray): Map<Int, List<DungeonComboDef>> {
        require(data.size >= 4) { "Truncated DungeonCombo header" }
        val reader = DatReader(data)
        val count = reader.readInt32()
        require(count >= 0 && data.size.toLong() == 4L + count.toLong() * 31L) {
            "Invalid DungeonCombo record count/length"
        }
        val tables = linkedMapOf<Int, MutableList<DungeonComboDef>>()
        repeat(count) {
            val row = DungeonComboDef(
                reader.readUInt16(), reader.readByte(), reader.readUInt32(),
                List(4) { DungeonComboReward(reader.readUInt16(), reader.readUInt32()) },
            )
            tables.getOrPut(row.id) { mutableListOf() }.add(row)
        }
        return tables.mapValues { it.value.toList() }
    }
}
