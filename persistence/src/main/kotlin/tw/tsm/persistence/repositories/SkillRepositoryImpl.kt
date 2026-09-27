package tw.tsm.persistence.repositories

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import tw.tsm.domain.interfaces.repositories.SkillRepository
import tw.tsm.persistence.tables.CharacterSkills

/**
 * SkillRepository 的 Exposed ORM 實作
 */
class SkillRepositoryImpl : SkillRepository {

    override suspend fun findByCharacterId(characterId: Long): Map<Int, Int> =
        suspendTransaction {
            CharacterSkills.selectAll()
                .where { CharacterSkills.characterId eq characterId }
                .associate { it[CharacterSkills.skillId] to it[CharacterSkills.level] }
        }

    override suspend fun saveAll(characterId: Long, skills: Map<Int, Int>) {
        suspendTransaction {
            // 刪除舊資料再批量寫入（簡單且安全）
            CharacterSkills.deleteWhere { CharacterSkills.characterId eq characterId }
            if (skills.isNotEmpty()) {
                CharacterSkills.batchInsert(skills.entries.toList()) { (skillId, level) ->
                    this[CharacterSkills.characterId] = characterId
                    this[CharacterSkills.skillId] = skillId
                    this[CharacterSkills.level] = level
                }
            }
        }
    }
}
