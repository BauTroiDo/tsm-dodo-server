package tw.tsm.persistence.repositories

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import tw.tsm.domain.interfaces.repositories.SkillRepository
import tw.tsm.domain.interfaces.repositories.Turn2SkillState
import tw.tsm.domain.interfaces.repositories.Turn3State
import tw.tsm.persistence.tables.CharacterSkills
import tw.tsm.persistence.tables.CharacterTurn2Skills
import tw.tsm.persistence.tables.CharacterTurn3States
import tw.tsm.persistence.tables.CharacterTurn3Skills

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

    override suspend fun findTurn3State(characterId: Long): Turn3State =
        suspendTransaction {
            val row = CharacterTurn3States.selectAll()
                .where { CharacterTurn3States.characterId eq characterId }
                .firstOrNull()
            val skills = CharacterTurn3Skills.selectAll()
                .where { CharacterTurn3Skills.characterId eq characterId }
                .associate { it[CharacterTurn3Skills.skillId] to it[CharacterTurn3Skills.level] }
            if (row == null) Turn3State(skills = skills) else Turn3State(
                element = row[CharacterTurn3States.element],
                exp = row[CharacterTurn3States.exp],
                intelligence = row[CharacterTurn3States.int_],
                atk = row[CharacterTurn3States.atk],
                def = row[CharacterTurn3States.def],
                agi = row[CharacterTurn3States.agi],
                hpx = row[CharacterTurn3States.hpx],
                spx = row[CharacterTurn3States.spx],
                skills = skills
            )
        }

    override suspend fun saveTurn3State(characterId: Long, state: Turn3State) {
        suspendTransaction {
            CharacterTurn3States.deleteWhere { CharacterTurn3States.characterId eq characterId }
            CharacterTurn3States.insert {
                it[CharacterTurn3States.characterId] = characterId
                it[CharacterTurn3States.element] = state.element
                it[CharacterTurn3States.exp] = state.exp
                it[CharacterTurn3States.int_] = state.intelligence
                it[CharacterTurn3States.atk] = state.atk
                it[CharacterTurn3States.def] = state.def
                it[CharacterTurn3States.agi] = state.agi
                it[CharacterTurn3States.hpx] = state.hpx
                it[CharacterTurn3States.spx] = state.spx
            }
            CharacterTurn3Skills.deleteWhere { CharacterTurn3Skills.characterId eq characterId }
            if (state.skills.isNotEmpty()) {
                CharacterTurn3Skills.batchInsert(state.skills.entries.toList()) { (skillId, level) ->
                    this[CharacterTurn3Skills.characterId] = characterId
                    this[CharacterTurn3Skills.skillId] = skillId
                    this[CharacterTurn3Skills.level] = level
                }
            }
        }
    }

}
