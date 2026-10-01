package tw.tsm.persistence.tables

import org.jetbrains.exposed.v1.core.Table

/** Daily offline-server dispatch quota; not exposed as a custom RoleCount to the client. */
object CharacterDispatchDaily : Table("character_dispatch_daily") {
    val characterId = long("character_id")
    /** Local calendar day encoded as yyyyMMdd. */
    val dayKey = integer("day_key")
    val freeUsed = integer("free_used").default(0)
    val paidUsed = integer("paid_used").default(0)

    override val primaryKey = PrimaryKey(characterId)
}
