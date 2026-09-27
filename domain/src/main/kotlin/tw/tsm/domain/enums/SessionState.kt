package tw.tsm.domain.enums

/**
 * 連線階段狀態機
 */
enum class SessionState {
    /** TCP 已連線，尚未驗證 */
    CONNECTED,
    /** 正在驗證帳號 */
    AUTHENTICATING,
    /** 已登入，選角畫面 */
    LOGGED_IN,
    /** 已進入遊戲世界 */
    IN_GAME,
    /** 已斷線 */
    DISCONNECTED
}
