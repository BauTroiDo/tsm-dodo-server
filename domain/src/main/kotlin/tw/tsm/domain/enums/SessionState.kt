package tw.tsm.domain.enums

/**
 * Máy trạng thái phiên kết nối
 */
enum class SessionState {
    /** Đã kết nối TCP, chưa xác thực*/
    CONNECTED,
    /** Đang xác thực tài khoản*/
    AUTHENTICATING,
    /** Đã đăng nhập, màn hình chọn nhân vật*/
    LOGGED_IN,
    /** Đã vào thế giới game*/
    IN_GAME,
    /** Đã mất kết nối*/
    DISCONNECTED
}
