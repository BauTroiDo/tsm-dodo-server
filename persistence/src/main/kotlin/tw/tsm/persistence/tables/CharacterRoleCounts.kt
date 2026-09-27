package tw.tsm.persistence.tables

import org.jetbrains.exposed.v1.core.Table

/**
 * 角色計數器表（RoleCount）
 *
 * 對應客戶端 RoleCount.lua 的 ERoleCount 枚舉。
 * 追蹤各種累計/每日計數：捕捉次數、死亡次數、好友數量等。
 *
 * 用途：成就條件（ECondition.RoleCount=14, FriendCount=18）、背包/錢莊格數、登入天數等。
 */
object CharacterRoleCounts : Table("character_role_counts") {
    /** 角色 ID */
    val charId = long("char_id")

    /** RoleCount 索引（對應 ERoleCount 枚舉值） */
    val countIndex = integer("count_index")

    /** 當前值 */
    val value = integer("value").default(0)

    /** 上限值 */
    val maxValue = integer("max_value").default(0)

    override val primaryKey = PrimaryKey(charId, countIndex)
}
