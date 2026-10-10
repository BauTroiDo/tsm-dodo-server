package tw.tsm.persistence.repositories

import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import tw.tsm.domain.interfaces.repositories.LimitedAreaDailyRepository
import tw.tsm.domain.interfaces.repositories.LimitedAreaDailyRun
import tw.tsm.persistence.tables.CharacterLimitedAreaDays

class LimitedAreaDailyRepositoryImpl : LimitedAreaDailyRepository {
    private fun read(row: org.jetbrains.exposed.v1.core.ResultRow): LimitedAreaDailyRun =
        LimitedAreaDailyRun(
            row[CharacterLimitedAreaDays.dayKey],
            row[CharacterLimitedAreaDays.startedAtMillis],
            row[CharacterLimitedAreaDays.closed],
        )

    override suspend fun find(characterId: Long, sceneId: Int): LimitedAreaDailyRun? =
        suspendTransaction {
            CharacterLimitedAreaDays.selectAll().where {
                (CharacterLimitedAreaDays.charId eq characterId) and
                    (CharacterLimitedAreaDays.sceneId eq sceneId)
            }.singleOrNull()?.let(::read)
        }

    override suspend fun claim(
        characterId: Long, sceneId: Int, dayKey: Int, nowMillis: Long,
        legacyUsedMinutes: Int, minuteMillis: Long,
    ): Boolean = suspendTransaction {
        val key = (CharacterLimitedAreaDays.charId eq characterId) and
            (CharacterLimitedAreaDays.sceneId eq sceneId)
        val existing = CharacterLimitedAreaDays.selectAll().where { key }.forUpdate().singleOrNull()
        when {
            existing == null -> {
                val legacyConsumed = legacyUsedMinutes > 0
                CharacterLimitedAreaDays.insert {
                    it[charId] = characterId
                    it[CharacterLimitedAreaDays.sceneId] = sceneId
                    it[CharacterLimitedAreaDays.dayKey] = dayKey
                    it[startedAtMillis] = nowMillis -
                        legacyUsedMinutes.coerceAtLeast(0).toLong() * minuteMillis
                    it[closed] = legacyConsumed
                }
                !legacyConsumed
            }
            existing[CharacterLimitedAreaDays.dayKey] >= dayKey -> false
            else -> {
                CharacterLimitedAreaDays.update({ key }) {
                    it[CharacterLimitedAreaDays.dayKey] = dayKey
                    it[startedAtMillis] = nowMillis
                    it[closed] = false
                }
                true
            }
        }
    }

    override suspend fun recover(
        characterId: Long, sceneId: Int, dayKey: Int, nowMillis: Long,
        legacyUsedMinutes: Int, minuteMillis: Long,
    ): LimitedAreaDailyRun = suspendTransaction {
        val key = (CharacterLimitedAreaDays.charId eq characterId) and
            (CharacterLimitedAreaDays.sceneId eq sceneId)
        val existing = CharacterLimitedAreaDays.selectAll().where { key }.forUpdate().singleOrNull()
        if (existing != null) return@suspendTransaction read(existing)
        val started = nowMillis - legacyUsedMinutes.coerceAtLeast(0).toLong() * minuteMillis
        CharacterLimitedAreaDays.insert {
            it[charId] = characterId
            it[CharacterLimitedAreaDays.sceneId] = sceneId
            it[CharacterLimitedAreaDays.dayKey] = dayKey
            it[startedAtMillis] = started
            it[closed] = false
        }
        LimitedAreaDailyRun(dayKey, started, false)
    }

    override suspend fun close(characterId: Long, sceneId: Int) {
        suspendTransaction {
            CharacterLimitedAreaDays.update({
                (CharacterLimitedAreaDays.charId eq characterId) and
                    (CharacterLimitedAreaDays.sceneId eq sceneId)
            }) { it[closed] = true }
        }
    }
}
