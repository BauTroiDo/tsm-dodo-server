package tw.tsm.persistence.repositories

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import tw.tsm.domain.interfaces.repositories.SkillRepository
import tw.tsm.domain.interfaces.repositories.Turn2SkillState
import tw.tsm.persistence.tables.CharacterSkills
import tw.tsm.persistence.tables.CharacterTurn2Skills

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

    override suspend fun findTurn2State(characterId: Long): Turn2SkillState =
        suspendTransaction {
            val rows = CharacterTurn2Skills.selectAll()
                .where { CharacterTurn2Skills.characterId eq characterId }
                .toList()
            val available = rows.firstOrNull {
                it[CharacterTurn2Skills.kind] == 1 && it[CharacterTurn2Skills.slotIndex] == 0
            }?.get(CharacterTurn2Skills.value) ?: 0
            val balls = rows.filter {
                it[CharacterTurn2Skills.kind] == 1 && it[CharacterTurn2Skills.slotIndex] in 1..12 &&
                    it[CharacterTurn2Skills.value] != 0
            }.map { it[CharacterTurn2Skills.slotIndex] }.toSet()
            val heroes = rows.filter {
                it[CharacterTurn2Skills.kind] == 2 && it[CharacterTurn2Skills.slotIndex] in 6..13 &&
                    it[CharacterTurn2Skills.value] > 0
            }.associate { it[CharacterTurn2Skills.slotIndex] to it[CharacterTurn2Skills.value] }
            Turn2SkillState(available, balls, heroes)
        }

    override suspend fun saveTurn2State(characterId: Long, state: Turn2SkillState) {
        suspendTransaction {
            CharacterTurn2Skills.deleteWhere { CharacterTurn2Skills.characterId eq characterId }
            val rows = mutableListOf<Triple<Int, Int, Int>>()
            rows += Triple(1, 0, state.availableBalls)
            state.activeBalls.forEach { rows += Triple(1, it, 1) }
            state.heroSkills.forEach { (index, skillId) -> rows += Triple(2, index, skillId) }
            CharacterTurn2Skills.batchInsert(rows) { (kind, index, value) ->
                this[CharacterTurn2Skills.characterId] = characterId
                this[CharacterTurn2Skills.kind] = kind
                this[CharacterTurn2Skills.slotIndex] = index
                this[CharacterTurn2Skills.value] = value
            }
        }
    }
}
