package tw.tsm.domain.constants

/**
 * 信件系統常數
 *
 * 來源：protocal.lua protocolTable[83], Social.lua EMailState
 */
object MailConstants {
    // ── 信件狀態 ──
    /** 未讀 */
    const val STATE_UNREAD = 0
    /** 已讀 */
    const val STATE_READ = 1
    /** 已領取附件 */
    const val STATE_TAKEN = 2

    // ── 附件種類 ──
    /** 物品 */
    const val ATTACH_KIND_ITEM = 1
    /** 武將 NPC */
    const val ATTACH_KIND_NPC = 2

    // ── S:083-005 錯誤碼 ──
    /** 背包空間不足 */
    const val ERROR_BAG_FULL = 4
    /** 附件已領取過 */
    const val ERROR_ALREADY_TAKEN = 5
    /** 信件已刪除 */
    const val ERROR_ALREADY_DELETED = 6
    /** 信件已過期 */
    const val ERROR_EXPIRED = 7
    /** 金錢容量不足 */
    const val ERROR_MONEY_FULL = 8
    /** 背包系統關閉（戰鬥中等） */
    const val ERROR_BAG_CLOSED = 9

    /** 預設信件過期天數 */
    const val DEFAULT_EXPIRE_DAYS = 30
    /** 最大附件數量 */
    const val MAX_ATTACHMENTS = 5
}
