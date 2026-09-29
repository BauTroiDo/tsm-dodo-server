package tw.tsm.domain.interfaces.repositories

/**
 * giao diện thao tác cơ sở dữ liệu của hệ thống bạn bè
 *
 * nguồn: protocal.lua protocolTable[14], Social.lua
 */
interface FriendRepository {

    /** truy vấn toàn bộ bạn bè của nhân vật*/
    suspend fun findFriends(charId: Long): List<FriendRecord>

    /** thêm quan hệ bạn bè (ghi hai chiều)*/
    suspend fun addFriend(charId1: Long, charId2: Long, addTime: Double)

    /** xoá quan hệ bạn bè (xoá hai chiều)*/
    suspend fun removeFriend(charId1: Long, charId2: Long)

    /** truy vấn số lượng bạn bè*/
    suspend fun countFriends(charId: Long): Int

    /** cập nhật độ tin cậy*/
    suspend fun updateFriendly(charId: Long, friendId: Long, friendly: Int)

    /** cập nhật cờ chức năng*/
    suspend fun updateFunctionFlag(charId: Long, friendId: Long, flag: Int)

    /** cập nhật hàng loạt cờ chức năng (dùng cho tặng quà một chạm)*/
    suspend fun batchUpdateFunctionFlag(charId: Long, friendIds: List<Long>, flag: Int)

    /** đặt lại toàn bộ cờ chức năng mỗi ngày (trạng thái tặng / nhận quà)*/
    suspend fun resetAllFunctionFlags()

    /** đã là bạn bè hay chưa*/
    suspend fun isFriend(charId1: Long, charId2: Long): Boolean

    // ── liên quan đến lời mời ──

    /** truy vấn lời mời kết bạn đang chờ xử lý*/
    suspend fun findPendingInvites(targetCharId: Long): List<FriendInviteRecord>

    /** thêm lời mời kết bạn*/
    suspend fun addInvite(inviterCharId: Long, targetCharId: Long)

    /** xoá lời mời kết bạn*/
    suspend fun removeInvite(inviterCharId: Long, targetCharId: Long)

    /** Đã có lời mời chưa (theo bất kỳ hướng nào)*/
    suspend fun hasInvite(inviterCharId: Long, targetCharId: Long): Boolean
}

/**
 * Bản ghi bạn bè
 */
data class FriendRecord(
    val charId: Long,
    val friendId: Long,
    /** Độ tin cậy (0-9999)*/
    val friendly: Int,
    /** Cờ chức năng (bit1=đã tặng quà, bit2=được tặng quà, bit3=đã nhận quà)*/
    val functionFlag: Int,
    /** Thời điểm kết bạn (OADate)*/
    val addTime: Double
)

/**
 * Bản ghi lời mời kết bạn
 */
data class FriendInviteRecord(
    val id: Long,
    val inviterCharId: Long,
    val targetCharId: Long,
    val createdAt: Long
)
