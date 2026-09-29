package tw.tsm.persistence.tables

import org.jetbrains.exposed.v1.core.Table

/** Danh sách kỹ năng tam chuyển, dạng wire là skillId(UInt16)+level(Byte).*/
object CharacterTurn3Skills : Table("character_turn3_skills") {
    val characterId = long("character_id")
    val skillId = integer("skill_id")
    val level = integer("level").default(1)
    override val primaryKey = PrimaryKey(characterId, skillId)
}
