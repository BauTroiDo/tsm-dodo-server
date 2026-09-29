package tw.tsm.data.loaders

import tw.tsm.data.DatReader
import tw.tsm.domain.data.MountAttributeGrow
import tw.tsm.domain.data.MountGrowDef

/**
 * Loader định nghĩa trưởng thành vật cưỡi — tương ứng `DataManager.OnLoadMountsGrowData`
 *
 * Nguồn: `MountsGrow_C.dat`
 * Định dạng: count(Int32) + count bản ghi MountsGrowData.New(reader)
 *
 * Thứ tự trường của MountsGrowData.New (MountsData.lua L21-42):
 *   level(Byte) + speed(Byte)
 *   + upItemId(UInt16) + upItemCount(Byte) + upMoney(UInt32)
 *   + 5 × { addValue(UInt16) + upItemId(UInt16) + upItemCount(UInt16) }
 */
object MountGrowDatLoader {

    /**
     * Đọc dữ liệu trưởng thành vật cưỡi
     */
    fun load(data: ByteArray): Map<Int, MountGrowDef> {
        val reader = DatReader(data)
        val count = reader.readInt32()
        val result = LinkedHashMap<Int, MountGrowDef>(count)

        for (i in 0 until count) {
            val level = reader.readByte()
            val speed = reader.readByte()
            val upItemId = reader.readUInt16()
            val upItemCount = reader.readByte()
            val upMoney = reader.readUInt32()

            val attributes = (1..5).map {
                MountAttributeGrow(
                    addValue = reader.readUInt16(),
                    upItemId = reader.readUInt16(),
                    upItemCount = reader.readUInt16()
                )
            }

            result[level] = MountGrowDef(speed, upItemId, upItemCount, upMoney, attributes)
        }

        return result
    }
}
