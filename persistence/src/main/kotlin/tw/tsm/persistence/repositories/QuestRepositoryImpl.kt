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
import tw.tsm.domain.interfaces.repositories.DungeonWeeklyResetResult
import tw.tsm.domain.interfaces.repositories.MissionFlagRecord
import tw.tsm.domain.interfaces.repositories.MissionRecord
import tw.tsm.domain.interfaces.repositories.QuestRepository
import tw.tsm.persistence.tables.CharacterDungeonDays
import tw.tsm.persistence.tables.CharacterDungeonWeeks
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
            val removed = if (oldDay == null) emptyList() else CharacterMissions.selectAll()
                .where { (CharacterMissions.charId eq charId) and
                    (CharacterMissions.missionId inList missionIds.toList()) }
                .map { it[CharacterMissions.missionId] }
            if (removed.isNotEmpty()) {
                CharacterMissions.deleteWhere {
                    (CharacterMissions.charId eq charId) and
                        (CharacterMissions.missionId inList removed)
                }
            }
            CharacterDungeonDays.upsert {
                it[CharacterDungeonDays.charId] = charId
                it[CharacterDungeonDays.dayKey] = dayKey
            }
            removed
        }


    override suspend fun refreshDungeonWeek(
        charId: Long,
        weekKey: Int,
        missionIds: Set<Int>,
        claimFlagBits: Set<Int>,
    ): DungeonWeeklyResetResult =
        suspendTransaction {
            val oldWeek = CharacterDungeonWeeks.selectAll()
                .where { CharacterDungeonWeeks.charId eq charId }
                .singleOrNull()?.get(CharacterDungeonWeeks.weekKey)
            if (oldWeek != null && oldWeek >= weekKey) {
                return@suspendTransaction DungeonWeeklyResetResult()
            }

            // First observation only establishes the calendar marker. Existing weekly
            // progress/claim state is preserved because its previous boundary is unknown.
            val removedMissionIds = if (oldWeek == null || missionIds.isEmpty()) {
                emptyList()
            } else {
                CharacterMissions.selectAll()
                    .where {
                        (CharacterMissions.charId eq charId) and
                            (CharacterMissions.missionId inList missionIds.toList())
                    }
                    .map { it[CharacterMissions.missionId] }
            }

            val clearedFlagBits = if (oldWeek == null || claimFlagBits.isEmpty()) {
                emptyList()
            } else {
                CharacterMissionFlags.selectAll()
                    .where {
                        (CharacterMissionFlags.charId eq charId) and
                            (CharacterMissionFlags.bitIndex inList claimFlagBits.toList())
                    }
                    .map { it[CharacterMissionFlags.bitIndex] }
            }

            if (removedMissionIds.isNotEmpty()) {
                CharacterMissions.deleteWhere {
                    (CharacterMissions.charId eq charId) and
                        (CharacterMissions.missionId inList removedMissionIds)
                }
            }
            if (clearedFlagBits.isNotEmpty()) {
                CharacterMissionFlags.deleteWhere {
                    (CharacterMissionFlags.charId eq charId) and
                        (CharacterMissionFlags.bitIndex inList clearedFlagBits)
                }
            }

            CharacterDungeonWeeks.upsert {
                it[CharacterDungeonWeeks.charId] = charId
                it[CharacterDungeonWeeks.weekKey] = weekKey
            }
            DungeonWeeklyResetResult(removedMissionIds, clearedFlagBits)
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
