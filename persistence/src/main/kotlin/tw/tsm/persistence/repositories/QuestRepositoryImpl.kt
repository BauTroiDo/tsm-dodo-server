package tw.tsm.persistence.repositories

import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert
import tw.tsm.domain.interfaces.repositories.MissionFlagRecord
import tw.tsm.domain.interfaces.repositories.MissionRecord
import tw.tsm.domain.interfaces.repositories.QuestRepository
import tw.tsm.persistence.tables.CharacterCompletedEvents
import tw.tsm.persistence.tables.CharacterMissionFlags
import tw.tsm.persistence.tables.CharacterMissions

/**
 * Triển khai QuestRepository bằng Exposed ORM
 *
 * Dùng hai bảng character_missions và character_mission_flags.
 */
class QuestRepositoryImpl : QuestRepository {

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
