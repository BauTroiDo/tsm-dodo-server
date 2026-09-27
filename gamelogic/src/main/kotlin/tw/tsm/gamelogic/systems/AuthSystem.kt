package tw.tsm.gamelogic.systems

import at.favre.lib.crypto.bcrypt.BCrypt
import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.interfaces.repositories.AccountRepository

private val logger = KotlinLogging.logger {}

/**
 * Kết quả xác thực tài khoản
 */
sealed class AuthResult {
    /** Xác thực thành công (Tài khoản đã tồn tại) */
    data class Success(val accountId: Long, val gmLevel: Int = 0) : AuthResult()
    /** Tài khoản mới đã được tạo */
    data class NewAccount(val accountId: Long) : AuthResult()
    /** Sai mật khẩu */
    data object WrongPassword : AuthResult()
    /** Tài khoản đã bị khóa */
    data object Suspended : AuthResult()
}

/**
 * Hệ thống xác thực tài khoản
 *
 * Đặc tính bản mobile: Tự động tạo tài khoản khi tài khoản chưa tồn tại
 */
class AuthSystem(private val accountRepo: AccountRepository) {

    /**
     * Xác thực tài khoản và mật khẩu
     *
     * @return Kết quả xác thực (Success/NewAccount/WrongPassword/Suspended)
     */
    suspend fun authenticate(account: String, password: String): AuthResult {
        val record = accountRepo.findByAccount(account)

        if (record == null) {
            // Tự động tạo tài khoản
            val hash = BCrypt.withDefaults().hashToString(BCRYPT_COST, password.toCharArray())
            val newId = accountRepo.create(account, hash)
            logger.info { "Tự động tạo tài khoản: $account (id=$newId)" }
            return AuthResult.NewAccount(newId)
        }

        if (record.isSuspended) {
            logger.warn { "Tài khoản bị khóa cố gắng đăng nhập: $account" }
            return AuthResult.Suspended
        }

        val verified = BCrypt.verifyer().verify(password.toCharArray(), record.passwordHash)
        if (!verified.verified) {
            logger.warn { "Sai mật khẩu: $account" }
            return AuthResult.WrongPassword
        }

        logger.info { "Xác thực thành công: $account (id=${record.id}) gmLevel=${record.gmLevel}" }
        return AuthResult.Success(record.id, record.gmLevel)
    }

    /**
     * Đăng nhập khách (Tự động tạo tài khoản ẩn danh)
     */
    suspend fun authenticateGuest(): AuthResult {
        val guestAccount = "Guest_${System.currentTimeMillis()}"
        val hash = BCrypt.withDefaults().hashToString(BCRYPT_COST, "guest".toCharArray())
        val newId = accountRepo.create(guestAccount, hash)
        logger.info { "Đăng nhập khách: $guestAccount (id=$newId)" }
        return AuthResult.NewAccount(newId)
    }

    companion object {
        private const val BCRYPT_COST = 10
    }
}