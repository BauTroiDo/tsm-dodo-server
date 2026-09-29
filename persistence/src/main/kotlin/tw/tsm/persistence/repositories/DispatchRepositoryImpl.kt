package tw.tsm.persistence.repositories

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import tw.tsm.domain.data.DispatchSaveData
import tw.tsm.domain.interfaces.repositories.DispatchRepository
import tw.tsm.persistence.tables.CharacterDispatches

/**
 * Triển khai truy xuất dữ liệu phái đi (Exposed ORM)
 */
class DispatchRepositoryImpl : DispatchRepository {

    override suspend fun findByCharacterId(characterId: Long): List<DispatchSaveData> =
        suspendTransaction {
            CharacterDispatches.selectAll()
                .where { CharacterDispatches.characterId eq characterId }
                .map { row ->
                    DispatchSaveData(
                        slotIndex = row[CharacterDispatches.slotIndex],
                        startTime = row[CharacterDispatches.startTime],
                        endTime = row[CharacterDispatches.endTime],
                        dispatchKind = row[CharacterDispatches.dispatchKind],
                        innPetIndex = row[CharacterDispatches.innPetIndex],
                        effectIndex1 = row[CharacterDispatches.effectIndex1],
                        effectIndex2 = row[CharacterDispatches.effectIndex2]
                    )
                }
        }

    override suspend fun insert(characterId: Long, data: DispatchSaveData) {
        suspendTransaction {
            CharacterDispatches.insert {
                it[CharacterDispatches.characterId] = characterId
                it[slotIndex] = data.slotIndex
                it[dispatchKind] = data.dispatchKind
                it[innPetIndex] = data.innPetIndex
                it[startTime] = data.startTime
                it[endTime] = data.endTime
                it[effectIndex1] = data.effectIndex1
                it[effectIndex2] = data.effectIndex2
            }
        }
    }

    override suspend fun deleteBySlot(characterId: Long, slotIndex: Int) {
        suspendTransaction {
            CharacterDispatches.deleteWhere {
                (CharacterDispatches.characterId eq characterId) and
                    (CharacterDispatches.slotIndex eq slotIndex)
            }
        }
    }
}
