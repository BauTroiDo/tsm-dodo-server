package tw.tsm.domain.enums

/**
 * Enum phương thức đăng nhập
 *
 * Nguồn: định nghĩa ELogin trong Network.lua
 */
enum class LoginKind(val code: Int) {
    /** Đăng nhập bằng tài khoản mật khẩu*/
    ACC_PWD(1),
    /** Đăng nhập khách*/
    GUEST(2),
    /** Đăng nhập Facebook*/
    FACEBOOK(3),
    /** Đăng nhập Google*/
    GOOGLE(4),
    /** Đăng nhập Apple*/
    APPLE(7),
    /** Đăng nhập server không biên giới*/
    UNBOUNDED(255);

    companion object {
        private val byCode = entries.associateBy { it.code }

        /** Lấy phương thức đăng nhập theo mã, mã không xác định trả về null*/
        fun fromCode(code: Int): LoginKind? = byCode[code]
    }
}
