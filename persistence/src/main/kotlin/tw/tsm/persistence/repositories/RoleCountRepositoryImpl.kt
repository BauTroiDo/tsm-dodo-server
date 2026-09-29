package tw.tsm.persistence.repositories

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.upsert
import tw.tsm.domain.interfaces.repositories.RoleCountRepository
import tw.tsm.persistence.tables.CharacterRoleCounts

/**
 * Triển khai RoleCountRepository bằng Exposed ORM
 *
 * Dùng bảng character_role_counts lưu bộ đếm của nhân vật.
 */
class RoleCountRepositoryImpl : RoleCountRepository {

    override suspend fun loadAll(charId: Long): Map<Int, Pair<Int, Int>> =
        suspendTransaction {
            CharacterRoleCounts.selectAll()
                .where { CharacterRoleCounts.charId eq charId }
                .associate {
                    it[CharacterRoleCounts.countIndex] to Pair(
                        it[CharacterRoleCounts.value],
                        it[CharacterRoleCounts.maxValue]
                    )
                }
        }

    override suspend fun upsert(charId: Long, countIndex: Int, value: Int, maxValue: Int) =
        suspendTransaction {
            CharacterRoleCounts.upsert {
                it[CharacterRoleCounts.charId] = charId
                it[CharacterRoleCounts.countIndex] = countIndex
                it[CharacterRoleCounts.value] = value
                it[CharacterRoleCounts.maxValue] = maxValue
            }
            Unit
        }

    override suspend fun saveAll(charId: Long, counts: Map<Int, Pair<Int, Int>>) =
        suspendTransaction {
            CharacterRoleCounts.deleteWhere { CharacterRoleCounts.charId eq charId }
            CharacterRoleCounts.batchInsert(counts.entries) { (index, valuePair) ->
                this[CharacterRoleCounts.charId] = charId
                this[CharacterRoleCounts.countIndex] = index
                this[CharacterRoleCounts.value] = valuePair.first
                this[CharacterRoleCounts.maxValue] = valuePair.second
            }
            Unit
        }
}
