package tw.tsm.persistence.repositories

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.upsert
import tw.tsm.domain.interfaces.repositories.DungeonRunRecord
import tw.tsm.domain.interfaces.repositories.DungeonRunRepository
import tw.tsm.persistence.tables.CharacterDungeonRuns

class DungeonRunRepositoryImpl : DungeonRunRepository {
    override suspend fun load(characterId: Long): DungeonRunRecord? =
        suspendTransaction {
            CharacterDungeonRuns.selectAll()
                .where { CharacterDungeonRuns.characterId eq characterId }
                .singleOrNull()
                ?.let { row ->
                    DungeonRunRecord(
                        characterId = row[CharacterDungeonRuns.characterId],
                        dungeonId = row[CharacterDungeonRuns.dungeonId],
                        startTime = row[CharacterDungeonRuns.startTime],
                        startedAtMillis = row[CharacterDungeonRuns.startedAtMillis],
                        instanceId = row[CharacterDungeonRuns.instanceId],
                        originMapId = row[CharacterDungeonRuns.originMapId],
                        originX = row[CharacterDungeonRuns.originX],
                        originY = row[CharacterDungeonRuns.originY],
                        originInstanceId = row[CharacterDungeonRuns.originInstanceId],
                        settled = row[CharacterDungeonRuns.settled],
                        runToken = row[CharacterDungeonRuns.runToken],
                    )
                }
        }

    override suspend fun save(record: DungeonRunRecord) {
        suspendTransaction {
            CharacterDungeonRuns.upsert {
                it[CharacterDungeonRuns.characterId] = record.characterId
                it[CharacterDungeonRuns.dungeonId] = record.dungeonId
                it[CharacterDungeonRuns.startTime] = record.startTime
                it[CharacterDungeonRuns.startedAtMillis] = record.startedAtMillis
                it[CharacterDungeonRuns.instanceId] = record.instanceId
                it[CharacterDungeonRuns.originMapId] = record.originMapId
                it[CharacterDungeonRuns.originX] = record.originX
                it[CharacterDungeonRuns.originY] = record.originY
                it[CharacterDungeonRuns.originInstanceId] = record.originInstanceId
                it[CharacterDungeonRuns.settled] = record.settled
                it[CharacterDungeonRuns.runToken] = record.runToken
            }
        }
    }

    override suspend fun clear(characterId: Long) {
        suspendTransaction {
            CharacterDungeonRuns.deleteWhere {
                CharacterDungeonRuns.characterId eq characterId
            }
        }
    }
}
