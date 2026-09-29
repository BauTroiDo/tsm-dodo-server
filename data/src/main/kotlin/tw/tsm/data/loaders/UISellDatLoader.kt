package tw.tsm.data.loaders

import tw.tsm.data.DatReader
import tw.tsm.domain.data.UISellDef
import tw.tsm.domain.data.UISellFormulaDef

/**
 * Loader tiêu phí UI — tương ứng dữ liệu UISell của `DataManager` (DM: 439)
 *
 * Hai file nguồn:
 * - `UISell_C.dat` (client `Data/UISellData.lua` đọc):
 *   count(Int32) + mỗi bản ghi id(U16) + rolecountId(U16) + moneyString(U32) + itemString(U32) + customString(U32)
 * - `UISell_Formula_C.dat` (client không đọc, bảng phí của server):
 *   count(Int32) + mỗi bản ghi id(U16) + kind(U8) + value(U32) + params(11×U16)
 */
object UISellDatLoader {

    /**
     * Đọc UISell_C.dat (định nghĩa chuỗi 介消)
     *
     * @return Map<sellId, UISellDef> (chỉ lưu khi id != 0, theo lệ Lua)
     */
    fun loadSellDefs(data: ByteArray): Map<Int, UISellDef> {
        val reader = DatReader(data)
        val count = reader.readInt32()
        val result = LinkedHashMap<Int, UISellDef>(count)

        for (i in 0 until count) {
            val id = reader.readUInt16()
            val rolecountId = reader.readUInt16()
            val moneyStringId = reader.readUInt32()
            val itemStringId = reader.readUInt32()
            val customStringId = reader.readUInt32()

            if (id != 0) {
                result[id] = UISellDef(
                    rolecountId = rolecountId,
                    moneyStringId = moneyStringId,
                    itemStringId = itemStringId,
                    customStringId = customStringId
                )
            }
        }

        return result
    }

    /**
     * Đọc UISell_Formula_C.dat (công thức phí 介消)
     *
     * @return Map<sellId, UISellFormulaDef>
     */
    fun loadFormulaDefs(data: ByteArray): Map<Int, UISellFormulaDef> {
        val reader = DatReader(data)
        val count = reader.readInt32()
        val result = LinkedHashMap<Int, UISellFormulaDef>(count)

        for (i in 0 until count) {
            val id = reader.readUInt16()
            val kind = reader.readByte()
            val value = reader.readUInt32()
            val params = List(11) { reader.readUInt16() }

            if (id != 0) {
                result[id] = UISellFormulaDef(
                    kind = kind,
                    value = value,
                    params = params
                )
            }
        }

        return result
    }
}
