package tw.tsm.persistence.tables

import org.jetbrains.exposed.v1.core.Table

object MessageBoardMessages : Table("message_board_messages") {
    val id = long("id").autoIncrement()
    val characterId = long("character_id").index()
    val playerId = long("player_id")
    val name = varchar("name", 64)
    val sex = integer("sex")
    val headStyle = integer("head_style")
    val color1 = integer("color_1")
    val color2 = integer("color_2")
    val createdAt = long("created_at").index()
    val message = text("message")

    override val primaryKey = PrimaryKey(id)
}
