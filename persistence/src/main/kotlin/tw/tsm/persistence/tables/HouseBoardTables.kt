package tw.tsm.persistence.tables

import org.jetbrains.exposed.v1.core.Table

object CharacterHouseProfiles : Table("character_house_profiles") {
    val characterId = long("character_id")
    val sign = integer("sign").default(0)
    val birthMonth = integer("birth_month").default(0)
    val birthDay = integer("birth_day").default(0)
    val charm = integer("charm").default(0)
    val signature = varchar("signature", 255).default("")
    val impression = varchar("impression", 255).default("")
    val favorite = varchar("favorite", 255).default("")
    val area = varchar("area", 255).default("")
    val hobby = varchar("hobby", 255).default("")
    override val primaryKey = PrimaryKey(characterId)
}

object CharacterGiftRecords : Table("character_gift_records") {
    val id = long("id").autoIncrement()
    val senderId = long("sender_id").index()
    val targetId = long("target_id").index()
    val itemId = integer("item_id")
    val quantity = integer("quantity")
    val time = double("time")
    override val primaryKey = PrimaryKey(id)
}
