package tw.tsm.persistence.repositories

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.upsert
import tw.tsm.domain.interfaces.repositories.WelfareStateRepository
import tw.tsm.persistence.tables.CharacterWelfareState
import tw.tsm.persistence.tables.CharacterWelfareLoginDays

class WelfareStateRepositoryImpl : WelfareStateRepository {
    override suspend fun recordLoginDay(characterId: Long, dayKey: Int): Int =
        suspendTransaction {
            require(characterId > 0L && dayKey in 20000101..99991231)
            CharacterWelfareLoginDays.insertIgnore {
                it[charId] = characterId
                it[CharacterWelfareLoginDays.dayKey] = dayKey
            }
            CharacterWelfareLoginDays.selectAll()
                .where { CharacterWelfareLoginDays.charId eq characterId }
                .count().coerceAtMost(65535L).toInt()
        }

    override suspend fun getLoginDayCount(characterId: Long): Int =
        suspendTransaction {
            CharacterWelfareLoginDays.selectAll()
                .where { CharacterWelfareLoginDays.charId eq characterId }
                .count().coerceAtMost(65535L).toInt()
        }

    override suspend fun getLastLoginClaimDay(characterId: Long): Int =
        suspendTransaction {
            CharacterWelfareState.selectAll()
                .where { CharacterWelfareState.charId eq characterId }
                .singleOrNull()
                ?.get(CharacterWelfareState.lastLoginClaimDay)
                ?: 0
        }

    override suspend fun setLastLoginClaimDay(characterId: Long, dayKey: Int) {
        suspendTransaction {
            CharacterWelfareState.upsert {
                it[charId] = characterId
                it[lastLoginClaimDay] = dayKey
            }
        }
    }
}
