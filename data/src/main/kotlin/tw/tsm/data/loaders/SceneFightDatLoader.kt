package tw.tsm.data.loaders

import tw.tsm.data.DatReader
import tw.tsm.domain.data.SceneFightDef

/** Loader đúng DVT SceneFightData.New(reader). */
object SceneFightDatLoader {
    fun load(data: ByteArray): List<SceneFightDef> {
        val reader = DatReader(data)
        val count = reader.readInt32()
        val result = ArrayList<SceneFightDef>(count)
        repeat(count) {
            result += SceneFightDef(
                group = reader.readByte(),
                groupName = reader.readUInt32(),
                sceneId = reader.readUInt16(),
                x = reader.readUInt16(),
                y = reader.readUInt16(),
                npcLow = reader.readUInt16(),
                npcHigh = reader.readUInt16(),
                element = reader.readByte(),
                infoStringId = reader.readUInt32(),
                mark = reader.readUInt16(),
                open = reader.readByte(),
                areaId = reader.readByte(),
                info = reader.readByte(),
            )
        }
        require(!reader.canRead()) { "IOS_SceneFight_C.dat còn trailing bytes" }
        return result
    }
}
