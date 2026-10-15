package tw.tsm.persistence.repositories

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.upsert
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.inList
import tw.tsm.persistence.tables.CharacterRoleCounts
import tw.tsm.persistence.tables.CharacterBitFlags
import tw.tsm.domain.interfaces.repositories.WelfareStateRepository
import tw.tsm.persistence.tables.CharacterWelfareState
import tw.tsm.persistence.tables.CharacterWelfareLoginDays

class WelfareStateRepositoryImpl : WelfareStateRepository {
    override suspend fun startOnlineDayIfNeeded(characterId: Long, dayKey: Int): Boolean =
        suspendTransaction {
            require(characterId > 0 && dayKey in 20000101..99991231)
            val previous = CharacterWelfareState.selectAll()
                .where { CharacterWelfareState.charId eq characterId }
                .forUpdate()
                .singleOrNull()
            if (previous != null && previous[CharacterWelfareState.onlineDayKey] == dayKey) {
                false
            } else {
                if (previous == null) {
                    CharacterWelfareState.insert {
                        it[charId] = characterId
                        it[onlineDayKey] = dayKey
                    }
                } else {
                    CharacterWelfareState.update({ CharacterWelfareState.charId eq characterId }) {
                        it[onlineDayKey] = dayKey
                    }
                }
                // One SQL transaction: clock, reward flags and day marker.
                CharacterRoleCounts.update({
                    (CharacterRoleCounts.charId eq characterId) and
                        (CharacterRoleCounts.countIndex eq 10)
                }) { it[value] = 0 }
                CharacterBitFlags.deleteWhere {
                    (CharacterBitFlags.charId eq characterId) and
                        (CharacterBitFlags.flagId inList (2..7).toList())
                }
                true
            }
        }

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
