package tw.tsm.domain.data

/**
 * 排行榜定義 — 對應 `LeaderboardData.lua`
 *
 * 來源：`LeaderbordInfo_C.dat`
 * 伺服器只需要 id 和 stateEnable，其餘屬性供客戶端 UI 使用。
 */
data class LeaderboardDef(
    /** 排行榜 ID */
    val id: Int,
    /** 主標籤文字 ID（客戶端用） */
    val mainTagText: Long,
    /** 子標籤文字 ID（客戶端用） */
    val subTagText: Long,
    /** 分數欄位名文字 ID（客戶端用） */
    val scoreText: Long,
    /** 玩家名欄位名文字 ID（客戶端用） */
    val nameText: Long,
    /** 獎勵 ID */
    val awardId: Int,
    /** 分數格式化範本 */
    val scoreFormat: String,
    /** 是否顯示升降狀態 */
    val stateEnable: Boolean,
    /** 前三名獎勵 flagId */
    val rankAwardFlagIds: List<Int>,
    /** 功能位元旗標 */
    val bitFunction: Int
)
