package tw.tsm.data.loaders

import tw.tsm.data.DatReader
import tw.tsm.domain.data.AchievementDef

/**
 * 成就載入器 — 對應 `DataManager.OnLoadAchievementData`
 *
 * 來源：`AchievementData_C.dat`
 * 格式：count(Int32) + count 筆 AchievementData.New(reader)
 * Key = 成就 ID（id != 0 才存入）
 *
 * 欄位順序嚴格對照 AchievementData.lua 第 16-38 行。
 * 每筆 37 bytes 固定長度（所有欄位都是 ReadByte/ReadUInt16/ReadUInt32）。
 */
object AchievementDatLoader {

    /**
     * 載入成就資料
     *
     * @param data 原始位元組陣列
     * @return 成就定義 Map（key = 成就 ID）
     */
    fun load(data: ByteArray): Map<Int, AchievementDef> {
        val reader = DatReader(data)
        val count = reader.readInt32()
        val result = LinkedHashMap<Int, AchievementDef>(count)

        for (i in 0 until count) {
            val id = reader.readUInt16()                  // 成就 ID
            val _name = reader.readUInt32()               // 名稱（字串表 ID，跳過）
            val mainTag = reader.readByte()               // 主分類
            val subTag = reader.readByte()                // 次分類
            val sortId = reader.readByte()                // 排序
            val showKind = reader.readByte()              // 顯示類別
            val _content = reader.readUInt32()            // 達成內容（字串表 ID，跳過）
            val score = reader.readByte()                 // 成就積分
            val conditionKind = reader.readByte()         // 條件類別
            val conditionKindValue = reader.readUInt32()  // 條件次參數
            val conditionOpr = reader.readByte()          // 運算子
            val conditionValue = reader.readUInt32()      // 達成數值
            val itemId = reader.readUInt16()              // 獎勵物品 ID
            val itemCount = reader.readByte()             // 獎勵物品數量
            val completeFlag = reader.readUInt16()        // 完成永標
            val getFlag = reader.readUInt16()             // 領獎永標
            val channel = reader.readByte()               // 廣播頻道
            val channelContent = reader.readUInt32()      // 廣播內容（字串表 ID）

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
