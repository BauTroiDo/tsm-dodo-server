package tw.tsm.domain.data

/**
 * 成就定義 — 對應 `AchievementData.lua`
 *
 * 來源：`AchievementData_C.dat`
 * 每筆 37 bytes 固定長度。
 *
 * name / content / channelContent 為字串表 ID，純 UI 用途，伺服器不需要存。
 *
 * @param id             成就 ID
 * @param mainTag        主分類
 * @param subTag         次分類
 * @param sortId         排序
 * @param showKind       顯示類別
 * @param score          成就積分
 * @param conditionKind  條件類別（對應 ECondition 枚舉）
 * @param conditionKindValue 條件次參數（如 RoleCount 索引、轉生次數等）
 * @param conditionOpr   運算子（1=等於, 2=大於, 3=大於等於, 4=小於, 5=小於等於, 6=不等於）
 * @param conditionValue 達成數值
 * @param itemId         獎勵物品 ID（0 = 無獎勵）
 * @param itemCount      獎勵物品數量
 * @param completeFlag   完成永標 ID（BitFlag）
 * @param getFlag        領獎永標 ID（BitFlag）
 * @param channel        廣播頻道（對應 EChannel）
 * @param channelContent 廣播內容（字串表 ID）
 */
data class AchievementDef(
    val id: Int,
    val mainTag: Int,
    val subTag: Int,
    val sortId: Int,
    val showKind: Int,
    val score: Int,
    val conditionKind: Int,
    val conditionKindValue: Long,
    val conditionOpr: Int,
    val conditionValue: Long,
    val itemId: Int,
    val itemCount: Int,
    val completeFlag: Int,
    val getFlag: Int,
    val channel: Int,
    val channelContent: Long,
)
