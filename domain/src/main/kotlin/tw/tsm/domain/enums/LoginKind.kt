package tw.tsm.domain.enums

/**
 * 登入方式枚舉
 *
 * 來源：Network.lua 的 ELogin 定義
 */
enum class LoginKind(val code: Int) {
    /** 帳號密碼登入 */
    ACC_PWD(1),
    /** 遊客登入 */
    GUEST(2),
    /** Facebook 登入 */
    FACEBOOK(3),
    /** Google 登入 */
    GOOGLE(4),
    /** Apple 登入 */
    APPLE(7),
    /** 無界伺服器登入 */
    UNBOUNDED(255);

    companion object {
        private val byCode = entries.associateBy { it.code }

        /** 根據代碼取得登入方式，未知代碼回傳 null */
        fun fromCode(code: Int): LoginKind? = byCode[code]
    }
}
