package tw.tsm.data.loaders

import tw.tsm.data.DatReader
import tw.tsm.domain.data.DungeonCriteriaDef

/** Loader matching DVT DungeonCriteriaData.New(reader); each record is 13 bytes. */
object DungeonCriteriaDatLoader {
    fun load(data: ByteArray): Map<Int, DungeonCriteriaDef> {
        val reader = DatReader(data)
        val count = reader.readInt32()
        require(count >= 0) { "IOS_DungeonCriteria_C.dat invalid record count: $count" }

        val result = LinkedHashMap<Int, DungeonCriteriaDef>(count)
        repeat(count) {
            val def = DungeonCriteriaDef(
                id = reader.readUInt16(),
                playerOrNpc = reader.readByte(),
                kind = reader.readByte(),
                compare = reader.readByte(),
                value = reader.readUInt32(),
                value2 = reader.readUInt32(),
            )
            result[def.id] = def
        }

        require(!reader.canRead()) { "IOS_DungeonCriteria_C.dat truncated or has trailing bytes" }
        return result
    }
}
