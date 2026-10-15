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
    private fun upsertOfflineCount(characterId: Long, index: Int, value: Int) {
        val exists = CharacterRoleCounts.selectAll().where {
            (CharacterRoleCounts.charId eq characterId) and (CharacterRoleCounts.countIndex eq index)
        }.singleOrNull()
        if (exists == null) {
            CharacterRoleCounts.insert {
                it[charId] = characterId
                it[countIndex] = index
                it[CharacterRoleCounts.value] = value
            }
        } else {
            CharacterRoleCounts.update({
                (CharacterRoleCounts.charId eq characterId) and (CharacterRoleCounts.countIndex eq index)
            }) { it[CharacterRoleCounts.value] = value }
        }
    }

    override suspend fun markCharacterOffline(characterId: Long, atMillis: Long) {
        require(characterId > 0L && atMillis > 0L)
        suspendTransaction {
            CharacterWelfareState.insertIgnore { it[charId] = characterId }
            val row = CharacterWelfareState.selectAll()
                .where { CharacterWelfareState.charId eq characterId }
                .forUpdate().single()
            if (row[CharacterWelfareState.offlineSinceMillis] == 0L) {
                CharacterWelfareState.update({ CharacterWelfareState.charId eq characterId }) {
                    it[offlineSinceMillis] = atMillis
                }
            }
        }
    }

    override suspend fun resumeCharacterOfflineMinutes(characterId: Long, nowMillis: Long) {
        require(characterId > 0L && nowMillis > 0L)
        suspendTransaction {
            CharacterWelfareState.insertIgnore { it[charId] = characterId }
            val row = CharacterWelfareState.selectAll()
                .where { CharacterWelfareState.charId eq characterId }
                .forUpdate().single()
            fun count(index: Int): Int = CharacterRoleCounts.selectAll().where {
                (CharacterRoleCounts.charId eq characterId) and (CharacterRoleCounts.countIndex eq index)
            }.singleOrNull()?.get(CharacterRoleCounts.value) ?: 0

            val calculated = OfflineWelfareMinutePolicy.calculate(
                row[CharacterWelfareState.offlineSinceMillis],
                nowMillis,
                row[CharacterWelfareState.offlineCounterDay],
                count(172),
                count(171),
            )
            // Update both counters and the consumed timestamp in the same SQL commit.
            upsertOfflineCount(characterId, 171, calculated.unclaimedMinutes)
            upsertOfflineCount(characterId, 172, calculated.todayMinutes)
            CharacterWelfareState.update({ CharacterWelfareState.charId eq characterId }) {
                it[offlineSinceMillis] = 0L
                it[offlineCounterDay] = OfflineWelfareMinutePolicy.dayKey(nowMillis)
            }
        }
    }


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
