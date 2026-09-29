package tw.tsm.data.loaders

import tw.tsm.data.DatReader
import tw.tsm.domain.data.BlissBagDef
import tw.tsm.domain.data.BlissBagItem

/**
 * Trình nạp túi phúc — tương ứng `DataManager.OnLoadBlissBagData`
 *
 * Nguồn: `BlissBag_C.dat`
 * Định dạng: count(Int32) + count bản ghi BlissBagData.New(reader)
 *
 * Định dạng mỗi bản ghi:
 * - id: UInt16       ← ID vật phẩm túi phúc (key, cùng id gộp thành một túi phúc)
 * - itemId: UInt16   ← ID vật phẩm thưởng
 * - count: UInt32    ← số lượng thưởng
 * - pr: UInt32       ← trọng số xác suất
 * - kind: Byte       ← phân nhóm loại (khi mở túi rút mỗi kind một cái)
 *
 * Key = ID vật phẩm túi phúc → BlissBagDef (chứa tất cả mục thưởng của id đó)
 * kindCount = giá trị kind lớn nhất trong cùng id
 */
object BlissBagDatLoader {

    /**
     * Nạp dữ liệu túi phúc
     *
     * @return ánh xạ ID vật phẩm túi phúc → BlissBagDef
     */
    fun load(data: ByteArray): Map<Int, BlissBagDef> {
        val reader = DatReader(data)
        val count = reader.readInt32()

        // Tạm lưu: nhóm theo id
        val grouped = LinkedHashMap<Int, MutableList<BlissBagItem>>()

        for (i in 0 until count) {
            val id = reader.readUInt16()       // ID vật phẩm túi phúc
            val itemId = reader.readUInt16()   // ID vật phẩm thưởng
            val itemCount = reader.readUInt32().toInt() // Số lượng thưởng
            val pr = reader.readUInt32().toInt()       // Trọng số xác suất
            val kind = reader.readByte()       // Phân nhóm loại

            val item = BlissBagItem(
                itemId = itemId,
                count = itemCount,
                pr = pr,
                kind = kind
            )
            grouped.getOrPut(id) { mutableListOf() }.add(item)
        }

        // Chuyển thành BlissBagDef
        val result = LinkedHashMap<Int, BlissBagDef>(grouped.size)
        for ((bagItemId, items) in grouped) {
            val kindCount = items.maxOfOrNull { it.kind } ?: 1
            result[bagItemId] = BlissBagDef(
                bagItemId = bagItemId,
                kindCount = kindCount,
                items = items
            )
        }

        return result
    }
}
