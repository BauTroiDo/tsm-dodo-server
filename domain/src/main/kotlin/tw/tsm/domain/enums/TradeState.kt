package tw.tsm.domain.enums

/**
 * 交易會話中單一玩家的狀態
 *
 * 狀態遷移：WaitingUpload → Uploaded → Confirmed
 * 任何狀態皆可被取消（由 TradeSystem 外部控制）
 */
sealed class TradePlayerState {
    /** 等待上傳交易內容（金額+物品） */
    data object WaitingUpload : TradePlayerState()

    /** 已上傳交易內容，等待對方或等待確認 */
    data object Uploaded : TradePlayerState()

    /** 已確認交易（雙方都確認後執行交換） */
    data object Confirmed : TradePlayerState()
}
