package tw.tsm.data.loaders

import tw.tsm.data.DatReader
import tw.tsm.domain.data.SceneSetDef

/**
 * Loader cài đặt cảnh — tương ứng `DataManager.OnLoadSceneSetData`
 *
 * nguồn: `SceneSet_C.dat`
 * Định dạng: count(Int32) + count bản ghi SceneData.ReadSceneSetData(reader)
 * Key = ID cảnh (chỉ lưu khi id != 0)
 */
object SceneSetDatLoader {

    /**
     * Đọc dữ liệu cài đặt cảnh
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
