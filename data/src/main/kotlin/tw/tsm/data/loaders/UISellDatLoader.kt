package tw.tsm.data.loaders

import tw.tsm.data.DatReader
import tw.tsm.domain.data.UISellDef
import tw.tsm.domain.data.UISellFormulaDef

/**
 * UI 介面消費載入器 — 對應 `DataManager` 的 UISell 資料（DM: 439）
 *
 * 兩個來源檔：
 * - `UISell_C.dat`（客戶端 `Data/UISellData.lua` 讀取）：
 *   count(Int32) + 每筆 id(U16) + rolecountId(U16) + moneyString(U32) + itemString(U32) + customString(U32)
 * - `UISell_Formula_C.dat`（客戶端不讀，伺服器費用表）：
 *   count(Int32) + 每筆 id(U16) + kind(U8) + value(U32) + params(11×U16)
 */
object UISellDatLoader {

    /**
     * 載入 UISell_C.dat（介消字串定義）
     *
     * @return Map<sellId, UISellDef>（id != 0 才存入，對照 Lua 慣例）
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
     * 載入 UISell_Formula_C.dat（介消費用公式）
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
