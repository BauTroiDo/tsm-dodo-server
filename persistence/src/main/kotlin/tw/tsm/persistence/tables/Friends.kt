package tw.tsm.persistence.tables

import org.jetbrains.exposed.v1.core.Table

/**
 * 好友關係資料表
 *
 * 每對好友關係存兩筆（雙向），方便各自查詢和獨立更新功能標記。
 */
object Friends : Table("friends") {
    /** 玩家角色 ID */
    val charId = long("char_id").index()
    /** 好友角色 ID */
    val friendId = long("friend_id").index()
    /** 信任度（0-9999） */
    val friendly = integer("friendly").default(0)
    /** 功能標記（bit1=已送禮, bit2=已被送禮, bit3=已領禮） */
    val functionFlag = integer("function_flag").default(0)
    /** 結交時間（OADate） */
    val addTime = double("add_time")

    override val primaryKey = PrimaryKey(charId, friendId)
}
