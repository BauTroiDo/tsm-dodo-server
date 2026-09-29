package tw.tsm.domain.enums

/**
 * Loại liên kết tài khoản
 *
 * Nguồn: định nghĩa EAccount trong Network.lua
 */
enum class AccountKind(val code: Int) {
    /** Tài khoản khách*/
    GUEST(0),
    /** Liên kết Facebook*/
    FACEBOOK(1);

    companion object {
        private val byCode = entries.associateBy { it.code }
        fun fromCode(code: Int): AccountKind? = byCode[code]
    }
}
