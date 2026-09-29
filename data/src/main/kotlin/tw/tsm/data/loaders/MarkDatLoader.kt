package tw.tsm.data.loaders

import tw.tsm.data.DatReader
import tw.tsm.domain.data.MarkDef

/**
 * Loader cờ đánh dấu nhiệm vụ — tương ứng `DataManager.OnLoadMarkData`
 *
 * Nguồn: `Mark_C.dat`
 * Định dạng: count(Int32) + count bản ghi MarkData.ReadInfo(reader)
 *
 * Thứ tự trường mỗi bản ghi (đối chiếu ReadInfo của MarkData.lua):
 * 1. name:        UnicodeString (độ dài 2B LE + UTF-16LE bytes)
 * 2. kind:        Byte
 * 3. id:          UInt16 (key, chỉ lưu khi id != 0)
 * 4. bitId:       UInt16 (chỉ số bit của cờ hoàn thành)
 * 5. gainWay:     Byte
 * 6. description: UnicodeString
 *
 * Đồng thời tạo ánh xạ ngược bitId → missionId (chỉ tạo khi bitId != 0),
 * Đối chiếu Lua `bitMarkDatas[data.bitId] = id`.
 */
object MarkDatLoader {

    /**
     * Đọc dữ liệu cờ đánh dấu nhiệm vụ
     *
     * @return Pair(markDefs, bitToMissionId)
     *   - markDefs: ánh xạ ID nhiệm vụ → [MarkDef]
     *   - bitToMissionId: ánh xạ ngược bitId → missionId
     */
    fun load(data: ByteArray): Pair<Map<Int, MarkDef>, Map<Int, Int>> {
        val reader = DatReader(data)
        val count = reader.readInt32()
        val markDefs = LinkedHashMap<Int, MarkDef>(count)
        val bitToMissionId = LinkedHashMap<Int, Int>()

        for (i in 0 until count) {
            val name = reader.readUnicodeString()        // [1] Tên nhiệm vụ
            val kind = reader.readByte()                  // [2] Loại
            val id = reader.readUInt16()                  // [3] Số hiệu (key)
            val bitId = reader.readUInt16()               // [4] Số hiệu nhiệm vụ đã hoàn thành
            val gainWay = reader.readByte()               // [5] Cách nhận
            val description = reader.readUnicodeString()  // [6] Mô tả

            if (id != 0) {
                markDefs[id] = MarkDef(
                    name = name,
                    kind = kind,
                    bitId = bitId,
                    gainWay = gainWay,
                    description = description
                )
                if (bitId != 0) {
                    bitToMissionId[bitId] = id
                }
            }
        }

        return Pair(markDefs, bitToMissionId)
    }
}
