package tw.tsm.persistence.tables

import org.jetbrains.exposed.v1.core.Table

/** One exact wire-compatible 93-byte ChildData snapshot per DVT character. */
object CharacterChildStates : Table("character_child_states") {
    val characterId = long("character_id")
    val payload = binary("payload", 93)
    /**
     * Server-authenticated binding to a *specific* owned pet UUID.
     * Empty means not bound; never infer a relationship from NPC ID / roster slot.
     */
    val petInstanceId = varchar("pet_instance_id", 36).default("")
    override val primaryKey = PrimaryKey(characterId)
}
