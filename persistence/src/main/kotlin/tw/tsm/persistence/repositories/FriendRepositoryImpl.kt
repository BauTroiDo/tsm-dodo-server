package tw.tsm.persistence.repositories

import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.update
import tw.tsm.domain.interfaces.repositories.FriendInviteRecord
import tw.tsm.domain.interfaces.repositories.FriendRecord
import tw.tsm.domain.interfaces.repositories.FriendRepository
import tw.tsm.persistence.tables.FriendInvites
import tw.tsm.persistence.tables.Friends

private val logger = KotlinLogging.logger {}

/**
 * Triển khai thao tác CSDL của hệ thống bạn bè
 */
class FriendRepositoryImpl : FriendRepository {

    override suspend fun findFriends(charId: Long): List<FriendRecord> = suspendTransaction {
        Friends.selectAll().where { Friends.charId eq charId }
            .map { row ->
                FriendRecord(
                    charId = row[Friends.charId],
                    friendId = row[Friends.friendId],
                    friendly = row[Friends.friendly],
                    functionFlag = row[Friends.functionFlag],
                    addTime = row[Friends.addTime]
                )
            }
    }

    override suspend fun addFriend(charId1: Long, charId2: Long, addTime: Double): Unit =
        suspendTransaction {
            // Ghi hai chiều
            Friends.insert {
                it[charId] = charId1
                it[friendId] = charId2
                it[friendly] = 0
                it[functionFlag] = 0
                it[Friends.addTime] = addTime
            }
            Friends.insert {
                it[charId] = charId2
                it[friendId] = charId1
                it[friendly] = 0
                it[functionFlag] = 0
                it[Friends.addTime] = addTime
            }
            logger.info { "Thêm quan hệ bạn bè: $charId1 ↔ $charId2" }
        }

    override suspend fun removeFriend(charId1: Long, charId2: Long): Unit = suspendTransaction {
        Friends.deleteWhere {
            ((charId eq charId1) and (friendId eq charId2)) or
                    ((charId eq charId2) and (friendId eq charId1))
        }
        logger.info { "Xóa quan hệ bạn bè: $charId1 ↔ $charId2" }
    }

    override suspend fun countFriends(charId: Long): Int = suspendTransaction {
        Friends.selectAll().where { Friends.charId eq charId }.count().toInt()
    }

    override suspend fun updateFriendly(charId: Long, friendId: Long, friendly: Int): Unit =
        suspendTransaction {
            Friends.update({ (Friends.charId eq charId) and (Friends.friendId eq friendId) }) {
                it[Friends.friendly] = friendly
            }
        }

    override suspend fun updateFunctionFlag(charId: Long, friendId: Long, flag: Int): Unit =
        suspendTransaction {
            Friends.update({ (Friends.charId eq charId) and (Friends.friendId eq friendId) }) {
                it[functionFlag] = flag
            }
        }

    override suspend fun batchUpdateFunctionFlag(charId: Long, friendIds: List<Long>, flag: Int): Unit =
        suspendTransaction {
            Friends.update({
                (Friends.charId eq charId) and (Friends.friendId inList friendIds)
            }) {
                it[functionFlag] = flag
            }
        }

    override suspend fun resetAllFunctionFlags(): Unit = suspendTransaction {
        Friends.update { it[functionFlag] = 0 }
        logger.info { "Reset hằng ngày toàn bộ mốc chức năng bạn bè" }
    }

    override suspend fun isFriend(charId1: Long, charId2: Long): Boolean = suspendTransaction {
        Friends.selectAll()
            .where { (Friends.charId eq charId1) and (Friends.friendId eq charId2) }
            .count() > 0
    }

    // ── liên quan đến lời mời ──

    override suspend fun findPendingInvites(targetCharId: Long): List<FriendInviteRecord> =
        suspendTransaction {
            FriendInvites.selectAll()
                .where { FriendInvites.targetCharId eq targetCharId }
                .map { row ->
                    FriendInviteRecord(
                        id = row[FriendInvites.id],
                        inviterCharId = row[FriendInvites.inviterCharId],
                        targetCharId = row[FriendInvites.targetCharId],
                        createdAt = row[FriendInvites.createdAt]
                    )
                }
        }

    override suspend fun addInvite(inviterCharId: Long, targetCharId: Long): Unit =
        suspendTransaction {
            FriendInvites.insert {
                it[FriendInvites.inviterCharId] = inviterCharId
                it[FriendInvites.targetCharId] = targetCharId
                it[createdAt] = System.currentTimeMillis()
            }
        }

    override suspend fun removeInvite(inviterCharId: Long, targetCharId: Long): Unit =
        suspendTransaction {
            FriendInvites.deleteWhere {
                (FriendInvites.inviterCharId eq inviterCharId) and
                        (FriendInvites.targetCharId eq targetCharId)
            }
        }

    override suspend fun hasInvite(inviterCharId: Long, targetCharId: Long): Boolean =
        suspendTransaction {
            FriendInvites.selectAll().where {
                (FriendInvites.inviterCharId eq inviterCharId) and
                        (FriendInvites.targetCharId eq targetCharId)
            }.count() > 0
        }
}
