package tw.tsm.data.loaders

import tw.tsm.data.DatReader
import tw.tsm.domain.data.MountDef

/**
 * Loader định nghĩa vật cưỡi — tương ứng `DataManager.OnLoadMountsData`
 *
 * Nguồn: `Mounts_C.dat`
 * Định dạng: count(Int32) + count bản ghi MountsData.New(reader)
 *
 * Thứ tự trường của MountsData.New (MountsData.lua L4-15):
 *   npcId(UInt16) + flagId(UInt16) + source(UInt32) + scale(Byte) * 0.01
 */
object MountDatLoader {

    /**
     * Đọc dữ liệu vật cưỡi
     */
    fun load(data: ByteArray): Map<Int, MountDef> {
        val reader = DatReader(data)
        val count = reader.readInt32()
        val result = LinkedHashMap<Int, MountDef>(count)

        for (i in 0 until count) {
            val npcId = reader.readUInt16()
            val flagId = reader.readUInt16()
            val source = reader.readUInt32()
            val scale = reader.readByte() * 0.01

            if (npcId != 0) {
                result[npcId] = MountDef(npcId, flagId, source, scale)
            }
        }

        return result
    }
}
