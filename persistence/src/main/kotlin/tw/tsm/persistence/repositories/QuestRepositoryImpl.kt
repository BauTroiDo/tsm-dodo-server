package tw.tsm.persistence.repositories

import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert
import tw.tsm.domain.interfaces.repositories.DungeonWeekRefreshResult
import tw.tsm.domain.interfaces.repositories.MissionFlagRecord
import tw.tsm.domain.interfaces.repositories.MissionRecord
import tw.tsm.domain.interfaces.repositories.QuestRepository
import tw.tsm.persistence.tables.CharacterDungeonDays
import tw.tsm.persistence.tables.CharacterCompletedEvents
import tw.tsm.persistence.tables.CharacterMissionFlags
import tw.tsm.persistence.tables.CharacterMissions

/**
 * Triển khai QuestRepository bằng Exposed ORM
 *
 * Dùng hai bảng character_missions và character_mission_flags.
 */
class QuestRepositoryImpl : QuestRepository {

    override suspend fun refreshDungeonDay(charId: Long, dayKey: Int, missionIds: Set<Int>): List<Int> =
        suspendTransaction {
            val oldDay = CharacterDungeonDays.selectAll()
                .where { CharacterDungeonDays.charId eq charId }
                .singleOrNull()?.get(CharacterDungeonDays.dayKey)
            if (oldDay != null && oldDay >= dayKey) return@suspendTransaction emptyList()
            // dayKey=0 is an uninitialized row that may have been created by the
            // weekly calendar path. Preserve legacy counters on first observation.
            val removed = if (oldDay == null || oldDay == 0) emptyList() else CharacterMissions.selectAll()
                .where { (CharacterMissions.charId eq charId) and
                    (CharacterMissions.missionId inList missionIds.toList()) }
                .map { it[CharacterMissions.missionId] }
            if (removed.isNotEmpty()) {
                CharacterMissions.deleteWhere {
                    (CharacterMissions.charId eq charId) and
                        (CharacterMissions.missionId inList removed)
                }
            }
            if (oldDay == null) {
                CharacterDungeonDays.insert {
                    it[CharacterDungeonDays.charId] = charId
                    it[CharacterDungeonDays.dayKey] = dayKey
                    it[CharacterDungeonDays.weekKey] = 0
                }
            } else {
                CharacterDungeonDays.update(
                    where = { CharacterDungeonDays.charId eq charId }
                ) {
                    it[CharacterDungeonDays.dayKey] = dayKey
                }
            }
            removed
        }


    override suspend fun refreshDungeonWeek(
        charId: Long,
        weekKey: Int,
        missionIds: Set<Int>,
        flagBits: Set<Int>,
    ): DungeonWeekRefreshResult =
        suspendTransaction {
            val oldWeek = CharacterDungeonDays.selectAll()
                .where { CharacterDungeonDays.charId eq charId }
                .singleOrNull()?.get(CharacterDungeonDays.weekKey)

            // Same/future key protects clock rollback. weekKey=0/null is first
            // observation after migration and must preserve existing progress.
            if (oldWeek != null && oldWeek > 0 && oldWeek >= weekKey) {
                return@suspendTransaction DungeonWeekRefreshResult()
            }
            val shouldReset = oldWeek != null && oldWeek > 0 && oldWeek < weekKey

            val removed = if (shouldReset && missionIds.isNotEmpty()) {
                CharacterMissions.selectAll()
                    .where {
                        (CharacterMissions.charId eq charId) and
                            (CharacterMissions.missionId inList missionIds.toList())
                    }
                    .map { it[CharacterMissions.missionId] }
            } else {
                emptyList()
            }
            if (removed.isNotEmpty()) {
                CharacterMissions.deleteWhere {
                    (CharacterMissions.charId eq charId) and
                        (CharacterMissions.missionId inList removed)
                }
            }

            val clearedBits = if (shouldReset && flagBits.isNotEmpty()) {
                CharacterMissionFlags.selectAll()
                    .where {
                        (CharacterMissionFlags.charId eq charId) and
                            (CharacterMissionFlags.bitIndex inList flagBits.toList())
                    }
                    .filter { it[CharacterMissionFlags.bitValue] != 0 }
                    .map { it[CharacterMissionFlags.bitIndex] }
            } else {
                emptyList()
            }
            if (shouldReset && flagBits.isNotEmpty()) {
                CharacterMissionFlags.deleteWhere {
                    (CharacterMissionFlags.charId eq charId) and
                        (CharacterMissionFlags.bitIndex inList flagBits.toList())
                }
            }

            if (oldWeek == null) {
                CharacterDungeonDays.insert {
                    it[CharacterDungeonDays.charId] = charId
                    it[CharacterDungeonDays.dayKey] = 0
                    it[CharacterDungeonDays.weekKey] = weekKey
                }
            } else {
                CharacterDungeonDays.update(
                    where = { CharacterDungeonDays.charId eq charId }
                ) {
                    it[CharacterDungeonDays.weekKey] = weekKey
                }
            }
            DungeonWeekRefreshResult(
                removedMissionIds = removed,
                clearedFlagBits = clearedBits,
            )
        }


    override suspend fun getMissions(charId: Long): List<MissionRecord> =
        suspendTransaction {
            CharacterMissions.selectAll()
                .where { CharacterMissions.charId eq charId }
                .map { MissionRecord(it[CharacterMissions.missionId], it[CharacterMissions.step]) }
        }

    override suspend fun getMissionFlags(charId: Long): List<MissionFlagRecord> =
        suspendTransaction {
            CharacterMissionFlags.selectAll()
                .where { CharacterMissionFlags.charId eq charId }
                .map { MissionFlagRecord(it[CharacterMissionFlags.bitIndex], it[CharacterMissionFlags.bitValue]) }
        }

    override suspend fun upsertMission(charId: Long, missionId: Int, step: Int) =
        suspendTransaction {
            CharacterMissions.upsert {
                it[CharacterMissions.charId] = charId
                it[CharacterMissions.missionId] = missionId
                it[CharacterMissions.step] = step
            }
            Unit
        }

    override suspend fun removeMission(charId: Long, missionId: Int) =
        suspendTransaction {
            CharacterMissions.deleteWhere {
                (CharacterMissions.charId eq charId) and
                (CharacterMissions.missionId eq missionId)
            }
            Unit
        }

    override suspend fun upsertFlag(charId: Long, bitIndex: Int, bitValue: Int) =
        suspendTransaction {
            CharacterMissionFlags.upsert {
                it[CharacterMissionFlags.charId] = charId
                it[CharacterMissionFlags.bitIndex] = bitIndex
                it[CharacterMissionFlags.bitValue] = bitValue
            }
            Unit
        }

    override suspend fun finishMission(charId: Long, missionId: Int, bitId: Int) =
        suspendTransaction {
            CharacterMissions.deleteWhere {
                (CharacterMissions.charId eq charId) and (CharacterMissions.missionId eq missionId)
            }
            if (bitId > 0) {
                CharacterMissionFlags.upsert {
                    it[CharacterMissionFlags.charId] = charId
                    it[CharacterMissionFlags.bitIndex] = bitId
                    it[CharacterMissionFlags.bitValue] = 1
                }
            }
            Unit
        }

    // ── Theo dõi hoàn thành sự kiện ──

    override suspend fun getCompletedEventCounts(charId: Long, mapId: Int): Map<Int, Int> =
        suspendTransaction {
            CharacterCompletedEvents.selectAll()
                .where {
                    (CharacterCompletedEvents.charId eq charId) and
                    (CharacterCompletedEvents.mapId eq mapId)
                }
                .associate { it[CharacterCompletedEvents.eveNo] to it[CharacterCompletedEvents.completionCount] }
        }

    override suspend fun insertCompletedEvent(charId: Long, mapId: Int, eveNo: Int) =
        suspendTransaction {
            val existing = CharacterCompletedEvents.selectAll()
                .where {
                    (CharacterCompletedEvents.charId eq charId) and
                    (CharacterCompletedEvents.mapId eq mapId) and
                    (CharacterCompletedEvents.eveNo eq eveNo)
                }
                .singleOrNull()

            if (existing != null) {
                val newCount = existing[CharacterCompletedEvents.completionCount] + 1
                CharacterCompletedEvents.update(
                    where = {
                        (CharacterCompletedEvents.charId eq charId) and
                        (CharacterCompletedEvents.mapId eq mapId) and
                        (CharacterCompletedEvents.eveNo eq eveNo)
                    }
                ) {
                    it[completionCount] = newCount
                }
            } else {
                CharacterCompletedEvents.insert {
                    it[CharacterCompletedEvents.charId] = charId
                    it[CharacterCompletedEvents.mapId] = mapId
                    it[CharacterCompletedEvents.eveNo] = eveNo
                    it[CharacterCompletedEvents.completionCount] = 1
                }
            }
            Unit
        }

    override suspend fun clearCompletedEvents(charId: Long, mapId: Int) =
        suspendTransaction {
            CharacterCompletedEvents.deleteWhere {
                (CharacterCompletedEvents.charId eq charId) and
                (CharacterCompletedEvents.mapId eq mapId)
            }
            Unit
        }
}
