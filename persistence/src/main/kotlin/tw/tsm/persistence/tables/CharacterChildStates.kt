package tw.tsm.persistence.tables

import org.jetbrains.exposed.v1.core.Table

/** One exact wire-compatible 93-byte ChildData snapshot per DVT character. */
object CharacterChildStates : Table("character_child_states") {
    val characterId = long("character_id")
    val payload = binary("payload", 93)
    override val primaryKey = PrimaryKey(characterId)
}
