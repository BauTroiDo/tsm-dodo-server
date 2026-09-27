package tw.tsm.domain.constants

/**
 * 派遣系統結果碼
 *
 * 來源：protocal.lua protocolTable[86]（第 13366-13446 行）
 */
object DispatchStartResult {
    /** 成功 */
    const val SUCCESS = 1
    /** 派遣種類錯誤 */
    const val INVALID_KIND = 2
    /** 序列已滿（超過 MaxSliderCount=4） */
    const val SLOT_FULL = 3
    /** 派遣種類重複 */
    const val KIND_DUPLICATE = 4
    /** 武將索引錯誤 */
    const val INVALID_NPC_INDEX = 5
    /** 武將忙碌中 */
    const val NPC_BUSY = 6
    /** 派遣次數已滿 */
    const val COUNT_FULL = 7
}

object DispatchCompleteResult {
    /** 成功 */
    const val SUCCESS = 1
    /** 派遣種類錯誤 */
    const val INVALID_KIND = 2
    /** 武將索引錯誤 */
    const val INVALID_NPC_INDEX = 3
    /** 尚未完成 */
    const val NOT_FINISHED = 4
    /** 背包空間不足 */
    const val BAG_FULL = 5
    /** 物品關閉 */
    const val ITEM_DISABLED = 6
}
