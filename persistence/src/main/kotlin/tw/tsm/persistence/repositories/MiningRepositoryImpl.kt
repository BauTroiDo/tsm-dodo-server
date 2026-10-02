package tw.tsm.persistence.repositories

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.upsert
import tw.tsm.domain.interfaces.repositories.MiningRecord
import tw.tsm.domain.interfaces.repositories.MiningRepository
import tw.tsm.persistence.tables.CharacterMining

class MiningRepositoryImpl : MiningRepository {
    override suspend fun load(charId: Long): MiningRecord? = suspendTransaction {
        CharacterMining.selectAll()
            .where { CharacterMining.characterId eq charId }
            .firstOrNull()
            ?.let { row ->
                MiningRecord(
                    mineType = row[CharacterMining.mineType],
                    machineFull = listOf(
                        row[CharacterMining.full1], row[CharacterMining.full2],
                        row[CharacterMining.full3], row[CharacterMining.full4],
                    ),
                    updatedAtMillis = row[CharacterMining.updatedAtMillis],
                    dayLimit = row[CharacterMining.dayLimit],
                )
            }
    }

    override suspend fun save(charId: Long, record: MiningRecord) {
        val full = (record.machineFull + listOf(0, 0, 0, 0)).take(4)
        suspendTransaction {
            CharacterMining.upsert {
                it[characterId] = charId
                it[mineType] = record.mineType
                it[full1] = full[0]
                it[full2] = full[1]
                it[full3] = full[2]
                it[full4] = full[3]
                it[updatedAtMillis] = record.updatedAtMillis
                it[dayLimit] = record.dayLimit
            }
        }
    }
}
