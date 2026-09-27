package tw.tsm.persistence.tables

import org.jetbrains.exposed.v1.core.Table

/**
 * 角色已學技能表
 *
 * 儲存每個角色已學會的技能及等級。
 * 聯合主鍵 (characterId, skillId) 確保每角色每技能只有一筆。
 */
object CharacterSkills : Table("character_skills") {
    /** 角色 ID */
    val characterId = long("character_id")

    /** 技能 ID（對應 SkillDef） */
    val skillId = integer("skill_id")

    /** 技能等級（1 起算） */
    val level = integer("level").default(1)

    override val primaryKey = PrimaryKey(characterId, skillId)

    init {
        index(false, characterId)
    }
}
