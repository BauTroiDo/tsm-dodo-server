package tw.tsm.data.loaders

import tw.tsm.data.DatReader
import tw.tsm.domain.data.AchievementDef

/**
 * Trình nạp thành tựu — tương ứng `DataManager.OnLoadAchievementData`
 *
 * Nguồn: `AchievementData_C.dat`
 * Định dạng: count(Int32) + count bản ghi AchievementData.New(reader)
 * Key = ID thành tựu (chỉ lưu khi id != 0)
 *
 * Thứ tự trường đối chiếu nghiêm ngặt AchievementData.lua dòng 16-38.
 * Mỗi bản ghi độ dài cố định 37 bytes (tất cả trường đều là ReadByte/ReadUInt16/ReadUInt32).
 */
object AchievementDatLoader {

    /**
     * Nạp dữ liệu thành tựu
     *
     * @param data mảng byte thô
     * @return Map định nghĩa thành tựu (key = ID thành tựu)
     */
    fun load(data: ByteArray): Map<Int, AchievementDef> {
        val reader = DatReader(data)
        val count = reader.readInt32()
        val result = LinkedHashMap<Int, AchievementDef>(count)

        for (i in 0 until count) {
            val id = reader.readUInt16()                  // ID thành tựu
            val _name = reader.readUInt32()               // Tên (ID bảng chuỗi, bỏ qua)
            val mainTag = reader.readByte()               // Phân loại chính
            val subTag = reader.readByte()                // Phân loại phụ
            val sortId = reader.readByte()                // Sắp xếp
            val showKind = reader.readByte()              // Loại hiển thị
            val _content = reader.readUInt32()            // Nội dung đạt được (ID bảng chuỗi, bỏ qua)
            val score = reader.readByte()                 // Điểm tích luỹ thành tựu
            val conditionKind = reader.readByte()         // Loại điều kiện
            val conditionKindValue = reader.readUInt32()  // Tham số phụ của điều kiện
            val conditionOpr = reader.readByte()          // Toán tử
            val conditionValue = reader.readUInt32()      // Giá trị số đạt được
            val itemId = reader.readUInt16()              // ID vật phẩm thưởng
            val itemCount = reader.readByte()             // Số lượng vật phẩm thưởng
            val completeFlag = reader.readUInt16()        // Cờ vĩnh viễn hoàn thành
            val getFlag = reader.readUInt16()             // Cờ vĩnh viễn nhận thưởng
            val channel = reader.readByte()               // Kênh phát rộng
            val channelContent = reader.readUInt32()      // Nội dung phát rộng (ID bảng chuỗi)

            if (id != 0) {
                result[id] = AchievementDef(
                    id = id,
                    mainTag = mainTag,
                    subTag = subTag,
                    sortId = sortId,
                    showKind = showKind,
                    score = score,
                    conditionKind = conditionKind,
                    conditionKindValue = conditionKindValue,
                    conditionOpr = conditionOpr,
                    conditionValue = conditionValue,
                    itemId = itemId,
                    itemCount = itemCount,
                    completeFlag = completeFlag,
                    getFlag = getFlag,
                    channel = channel,
                    channelContent = channelContent,
                )
            }
        }

        return result
    }
}
