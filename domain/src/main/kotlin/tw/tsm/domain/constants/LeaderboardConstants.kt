package tw.tsm.domain.constants

import tw.tsm.domain.data.LeaderboardDef

/**
 * 排行榜類型常數
 *
 * 根據 LeaderbordInfo_C.dat 解碼結果，26 個排行榜分 7 種類型。
 */
object LeaderboardConstants {

    /** 五行等級排行（按元素篩選）— IDs 1-5, 32-36 */
    val ELEMENT_LEVEL_IDS = setOf(1, 2, 3, 4, 5, 32, 33, 34, 35, 36)

    /** 轉生排行 */
    const val REBORN_ID = 6

    /** 總等級排行 */
    const val TOTAL_LEVEL_ID = 7

    /** 成就等級排行（與總等級同排序邏輯） */
    const val ACHIEVEMENT_ID = 20

    /** 金錢排行 */
    const val GOLD_ID = 10

    /** 拍賣金排行 */
    const val AUCTION_GOLD_ID = 11

    /** 武將等級排行 */
    const val PET_LEVEL_ID = 16

    /** 尚未實作的排行榜（需未來系統支援） */
    val NOT_IMPLEMENTED_IDS = setOf(8, 9, 17, 19, 27, 29, 30, 31, 40, 44)

    /** subTag → 五行元素映射（金木水火土 = 1-5） */
    private val SUBTAG_TO_ELEMENT = mapOf(
        20557L to 1, // 金
        20558L to 2, // 木
        20559L to 3, // 水
        20560L to 4, // 火
        20570L to 5  // 土
    )

    /**
     * 從排行榜定義解析五行元素
     *
     * @return 元素 ID（1-5），無法識別時回傳 0
     */
    fun resolveElement(def: LeaderboardDef): Int = SUBTAG_TO_ELEMENT[def.subTagText] ?: 0
}
