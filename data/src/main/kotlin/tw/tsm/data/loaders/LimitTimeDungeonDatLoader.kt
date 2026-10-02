package tw.tsm.data.loaders

import tw.tsm.data.DatReader
import tw.tsm.domain.data.LimitTimeDungeonDef

/** Loader đúng DVT LimitTimeDungeonData.New(reader). */
object LimitTimeDungeonDatLoader {
    fun load(data: ByteArray): Map<Int, LimitTimeDungeonDef> {
        val reader = DatReader(data)
        val count = reader.readInt32()
        val result = LinkedHashMap<Int, LimitTimeDungeonDef>(count)
        repeat(count) {
            val def = LimitTimeDungeonDef(
                index = reader.readByte(),
                messageId = reader.readUInt32(),
                sceneId = reader.readUInt16(),
                limitIndex = reader.readUInt16(),
                limitTimeMinutes = reader.readUInt16(),
                limitLevel = reader.readUInt16(),
                missionId = reader.readUInt16(),
            )
            result[def.index] = def
        }
        require(!reader.canRead()) { "IOS_LimitTimeDungeon_C.dat còn trailing bytes" }
        return result
    }
}
