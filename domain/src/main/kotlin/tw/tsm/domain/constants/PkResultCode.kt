package tw.tsm.domain.constants

/**
 * PK 要求結果碼（S:033-001）
 *
 * 來源：protocal.lua protocolTable[33][1]（第 7019-7031 行）
 */
object PkResultCode {
    /** 對方未開 PK 開關 */
    const val PK_OFF = 1
    /** 對方未開參戰開關 */
    const val JOIN_OFF = 2
    /** 對方戰鬥問答中 */
    const val IN_QUESTION = 3
    /** 特殊戰鬥不能參戰 */
    const val SPECIAL_BATTLE = 4
    /** 城門戰，不同盟不能參戰 */
    const val CITY_DIFF_ALLY = 5
    /** 城門戰，不同軍團不能參戰 */
    const val CITY_DIFF_GROUP = 6
    /** 該玩家目前在住宿中 */
    const val RESTING = 7
    /** 此場景不可參戰 */
    const val SCENE_NO_PK = 8
    /** 同盟不可 PK */
    const val SAME_ALLY = 9
    /** 對方無敵狀態中，無法攻擊 */
    const val INVINCIBLE = 10
}
