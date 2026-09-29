package tw.tsm.domain.enums

/**
 * Trạng thái của một người chơi trong phiên giao dịch
 *
 * Chuyển trạng thái: WaitingUpload → Uploaded → Confirmed
 * Mọi trạng thái đều có thể bị huỷ (do bên ngoài TradeSystem điều khiển)
 */
sealed class TradePlayerState {
    /** Chờ tải lên nội dung giao dịch (tiền + vật phẩm)*/
    data object WaitingUpload : TradePlayerState()

    /** Đã tải nội dung giao dịch, chờ đối phương hoặc chờ xác nhận*/
    data object Uploaded : TradePlayerState()

    /** Đã xác nhận giao dịch (trao đổi được thực thi khi cả hai bên xác nhận)*/
    data object Confirmed : TradePlayerState()
}
