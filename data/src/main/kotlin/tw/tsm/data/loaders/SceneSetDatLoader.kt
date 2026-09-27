package tw.tsm.data.loaders

import tw.tsm.data.DatReader
import tw.tsm.domain.data.SceneSetDef

/**
 * 場景設定載入器 — 對應 `DataManager.OnLoadSceneSetData`
 *
 * 來源：`SceneSet_C.dat`
 * 格式：count(Int32) + count 筆 SceneData.ReadSceneSetData(reader)
 * Key = 場景 ID（id != 0 才存入）
 */
object SceneSetDatLoader {

    /**
     * 載入場景設定資料
     */
    fun load(data: ByteArray): Map<Int, SceneSetDef> {
        val reader = DatReader(data)
        val count = reader.readInt32()
        val result = LinkedHashMap<Int, SceneSetDef>(count)

        for (i in 0 until count) {
            val nameId = reader.readUInt32()
            val id = reader.readUInt16()
            val def = SceneSetDef(
                nameId = nameId,
                kind = reader.readByte(),
                maxPlayer = reader.readUInt16(),
                limit1 = reader.readByte(),
                limit2 = reader.readByte(),
                effect = reader.readByte(),
                minLv = reader.readByte(),
                maxLv = reader.readByte(),
                viewSetting = reader.readByte(),
                subId = reader.readByte(),
                limit3 = reader.readByte()
            )
            if (id != 0) {
                result[id] = def
            }
        }

        return result
    }
}
