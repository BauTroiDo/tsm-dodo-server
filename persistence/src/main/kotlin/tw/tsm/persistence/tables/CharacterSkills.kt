package tw.tsm.persistence.tables

import org.jetbrains.exposed.v1.core.Table

/**
 * Bảng kỹ năng đã học của nhân vật
 *
 * Lưu các kỹ năng đã học và cấp độ của mỗi nhân vật.
 * Khoá chính ghép (characterId, skillId) đảm bảo mỗi nhân vật mỗi kỹ năng chỉ có một bản ghi.
 */
object CharacterSkills : Table("character_skills") {
    /** ID nhân vật*/
    val characterId = long("character_id")

    /** ID kỹ năng (tương ứng SkillDef)*/
    val skillId = integer("skill_id")

    /** Cấp kỹ năng (tính từ 1)*/
    val level = integer("level").default(1)

    override val primaryKey = PrimaryKey(characterId, skillId)

    init {
        index(false, characterId)
    }
}
