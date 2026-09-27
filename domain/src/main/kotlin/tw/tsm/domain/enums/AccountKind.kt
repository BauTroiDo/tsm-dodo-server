package tw.tsm.domain.enums

/**
 * 帳號綁定類型
 *
 * 來源：Network.lua 的 EAccount 定義
 */
enum class AccountKind(val code: Int) {
    /** 遊客帳號 */
    GUEST(0),
    /** Facebook 綁定 */
    FACEBOOK(1);

    companion object {
        private val byCode = entries.associateBy { it.code }
        fun fromCode(code: Int): AccountKind? = byCode[code]
    }
}
