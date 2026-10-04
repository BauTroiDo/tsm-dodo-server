package tw.tsm.data.loaders

import tw.tsm.data.DatReader
import tw.tsm.domain.data.DungeonCriteriaDef

/** Exact DVT DungeonCriteriaData.New(reader); each row is 13 bytes. */
object DungeonCriteriaDatLoader {
    fun load(data: ByteArray): Map<Int, DungeonCriteriaDef> {
        val reader = DatReader(data)
        val count = reader.readInt32()
        val result = LinkedHashMap<Int, DungeonCriteriaDef>(count)

        repeat(count) {
            val id = reader.readUInt16()
            result[id] = DungeonCriteriaDef(
                id = id,
                playerOrNpc = reader.readByte(),
                kind = reader.readByte(),
                compare = reader.readByte(),
                value = reader.readUInt32(),
                value2 = reader.readUInt32(),
            )
        }

        require(!reader.canRead()) { "IOS_DungeonCriteria_C.dat còn trailing bytes" }
        return result
    }
}
