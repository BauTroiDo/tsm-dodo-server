package tw.tsm.domain.interfaces.repositories

/**
 * 好友系統資料庫操作介面
 *
 * 來源：protocal.lua protocolTable[14], Social.lua
 */
interface FriendRepository {

    /** 查詢角色的所有好友 */
    suspend fun findFriends(charId: Long): List<FriendRecord>

    /** 新增好友關係（雙向寫入） */
    suspend fun addFriend(charId1: Long, charId2: Long, addTime: Double)

    /** 刪除好友關係（雙向刪除） */
    suspend fun removeFriend(charId1: Long, charId2: Long)

    /** 查詢好友數量 */
    suspend fun countFriends(charId: Long): Int

    /** 更新信任度 */
    suspend fun updateFriendly(charId: Long, friendId: Long, friendly: Int)

    /** 更新功能標記 */
    suspend fun updateFunctionFlag(charId: Long, friendId: Long, flag: Int)

    /** 批量更新功能標記（一鍵送禮用） */
    suspend fun batchUpdateFunctionFlag(charId: Long, friendIds: List<Long>, flag: Int)

    /** 每日重置所有功能標記（送禮/收禮狀態） */
    suspend fun resetAllFunctionFlags()

    /** 是否已是好友 */
    suspend fun isFriend(charId1: Long, charId2: Long): Boolean

    // ── 邀請相關 ──

    /** 查詢待處理的好友邀請 */
    suspend fun findPendingInvites(targetCharId: Long): List<FriendInviteRecord>

    /** 新增好友邀請 */
    suspend fun addInvite(inviterCharId: Long, targetCharId: Long)

    /** 刪除好友邀請 */
    suspend fun removeInvite(inviterCharId: Long, targetCharId: Long)

    /** 是否已有邀請（任一方向） */
    suspend fun hasInvite(inviterCharId: Long, targetCharId: Long): Boolean
}

/**
 * 好友記錄
 */
data class FriendRecord(
    val charId: Long,
    val friendId: Long,
    /** 信任度（0-9999） */
    val friendly: Int,
    /** 功能標記（bit1=已送禮, bit2=已被送禮, bit3=已領禮） */
    val functionFlag: Int,
    /** 結交時間（OADate） */
    val addTime: Double
)

/**
 * 好友邀請記錄
 */
data class FriendInviteRecord(
    val id: Long,
    val inviterCharId: Long,
    val targetCharId: Long,
    val createdAt: Long
)
