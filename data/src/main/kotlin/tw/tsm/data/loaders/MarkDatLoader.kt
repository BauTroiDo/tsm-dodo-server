package tw.tsm.data.loaders

import tw.tsm.data.DatReader
import tw.tsm.domain.data.MarkDef

/**
 * 任務標記載入器 — 對應 `DataManager.OnLoadMarkData`
 *
 * 來源：`Mark_C.dat`
 * 格式：count(Int32) + count 筆 MarkData.ReadInfo(reader)
 *
 * 每筆欄位順序（對照 MarkData.lua ReadInfo）：
 * 1. name:        UnicodeString（2B LE 長度 + UTF-16LE bytes）
 * 2. kind:        Byte
 * 3. id:          UInt16（key，id != 0 才存入）
 * 4. bitId:       UInt16（完成標記位元索引）
 * 5. gainWay:     Byte
 * 6. description: UnicodeString
 *
 * 同時建立 bitId → missionId 反向映射（bitId != 0 才建立），
 * 對照 Lua `bitMarkDatas[data.bitId] = id`。
 */
object MarkDatLoader {

    /**
     * 載入任務標記資料
     *
     * @return Pair(markDefs, bitToMissionId)
     *   - markDefs: 任務 ID → [MarkDef] 映射
     *   - bitToMissionId: bitId → missionId 反向映射
     */
    fun load(data: ByteArray): Pair<Map<Int, MarkDef>, Map<Int, Int>> {
        val reader = DatReader(data)
        val count = reader.readInt32()
        val markDefs = LinkedHashMap<Int, MarkDef>(count)
        val bitToMissionId = LinkedHashMap<Int, Int>()

        for (i in 0 until count) {
            val name = reader.readUnicodeString()        // [1] 任務名稱
            val kind = reader.readByte()                  // [2] 類別
            val id = reader.readUInt16()                  // [3] 編號（key）
            val bitId = reader.readUInt16()               // [4] 已完成任務編號
            val gainWay = reader.readByte()               // [5] 獲取方式
            val description = reader.readUnicodeString()  // [6] 說明

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
