package tw.tsm.persistence.tables

import org.jetbrains.exposed.v1.core.Table

/** 三轉技能列表，wire 為 skillId(UInt16)+level(Byte)。 */
object CharacterTurn3Skills : Table("character_turn3_skills") {
    val characterId = long("character_id")
    val skillId = integer("skill_id")
    val level = integer("level").default(1)
    override val primaryKey = PrimaryKey(characterId, skillId)
}
