package tw.tsm.data.loaders

import tw.tsm.data.DatReader
import tw.tsm.domain.data.WarpDef

/**
 * Loader cổng dịch chuyển — tương ứng `DataManager.OnLoadWarpData`
 *
 * Nguồn: `Warp_C.dat`
 * Định dạng: count(Int32) + count bản ghi WarpData.New(reader)
 * Key = 0-based index（warpDatas[i - 1] = data）
 */
object WarpDatLoader {

    /**
     * Đọc dữ liệu cổng dịch chuyển
     */
    fun load(data: ByteArray): Map<Int, WarpDef> {
        val reader = DatReader(data)
        val count = reader.readInt32()
        val result = LinkedHashMap<Int, WarpDef>(count)

        for (i in 0 until count) {
            val warp = WarpDef(
                name = reader.readUInt32(),
                scene = reader.readUInt16(),
                mark = reader.readUInt16(),
                x = reader.readInt32(),
                y = reader.readInt32()
            )
            result[i] = warp
        }

        return result
    }
}
