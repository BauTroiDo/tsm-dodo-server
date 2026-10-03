package tw.tsm.data.loaders

import tw.tsm.data.DatReader
import tw.tsm.domain.data.DungeonDef
import tw.tsm.domain.data.DungeonNeedDef

/** Loader đúng DVT DungeonData.New(reader); mỗi record IOS_Dungeon_C.dat = 42 bytes. */
object DungeonDatLoader {
    fun load(data: ByteArray): Map<Int, DungeonDef> {
        val reader = DatReader(data)
        val count = reader.readInt32()
        val result = LinkedHashMap<Int, DungeonDef>(count)

        repeat(count) {
            val id = reader.readUInt16()
            val def = DungeonDef(
                id = id,
                nameId = reader.readUInt32(),
                kind = reader.readByte(),
                minLv = reader.readUInt16(),
                maxLv = reader.readUInt16(),
                maxPlayer = reader.readByte(),
                missionFlag = reader.readUInt16(),
                dailyFlag = reader.readUInt16(),
                dailyCount = reader.readUInt16(),
                skipFlag = reader.readUInt16(),
                timeMinutes = reader.readUInt16(),
                sceneId = reader.readUInt16(),
                costPoint = reader.readUInt16(),
                lootTable = reader.readUInt16(),
                extraLoot = reader.readUInt16(),
                infoTextId = reader.readUInt32(),
                needPar = List(2) {
                    DungeonNeedDef(
                        kind = reader.readByte(),
                        parameter = reader.readUInt16(),
                    )
                },
                allPassPrize = reader.readUInt16(),
            )
            result[id] = def
        }

        require(!reader.canRead()) { "IOS_Dungeon_C.dat còn trailing bytes" }
        return result
    }
}
