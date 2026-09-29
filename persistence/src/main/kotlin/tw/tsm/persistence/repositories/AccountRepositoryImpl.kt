package tw.tsm.persistence.repositories

import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.update
import tw.tsm.domain.interfaces.repositories.AccountRecord
import tw.tsm.domain.interfaces.repositories.AccountRepository
import tw.tsm.persistence.tables.Accounts

private val logger = KotlinLogging.logger {}

/**
 * Triển khai Repository tài khoản
 */
class AccountRepositoryImpl : AccountRepository {

    override suspend fun findByAccount(account: String): AccountRecord? =
        suspendTransaction {
            Accounts.selectAll().where { Accounts.account eq account }
                .firstOrNull()
                ?.let {
                    AccountRecord(
                        id = it[Accounts.id],
                        account = it[Accounts.account],
                        passwordHash = it[Accounts.passwordHash],
                        isSuspended = it[Accounts.isSuspended],
                        gmLevel = it[Accounts.gmLevel]
                    )
                }
        }

    override suspend fun create(account: String, passwordHash: String): Long =
        suspendTransaction {
            val id = Accounts.insert {
                it[Accounts.account] = account
                it[Accounts.passwordHash] = passwordHash
                it[Accounts.createdAt] = System.currentTimeMillis()
            } get Accounts.id

            logger.info { "Tạo tài khoản mới: $account (id=$id)" }
            id
        }

    override suspend fun updatePassword(accountId: Long, passwordHash: String) {
        suspendTransaction {
            Accounts.update({ Accounts.id eq accountId }) {
                it[Accounts.passwordHash] = passwordHash
            }
        }
    }

    override suspend fun updateGmLevel(accountId: Long, gmLevel: Int) {
        suspendTransaction {
            Accounts.update({ Accounts.id eq accountId }) {
                it[Accounts.gmLevel] = gmLevel
            }
        }
        logger.info { "Tài khoản $accountId được đổi cấp GM thành $gmLevel" }
    }
}
