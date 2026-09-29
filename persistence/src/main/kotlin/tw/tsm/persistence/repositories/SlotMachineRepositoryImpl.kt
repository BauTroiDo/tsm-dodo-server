package tw.tsm.persistence.repositories

import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.upsert
import tw.tsm.domain.data.SlotMachineAward
import tw.tsm.domain.data.SlotMachineStateData
import tw.tsm.domain.interfaces.repositories.SlotMachineRepository
import tw.tsm.persistence.tables.CharacterSlotMachineAwards
import tw.tsm.persistence.tables.CharacterSlotMachines

class SlotMachineRepositoryImpl : SlotMachineRepository {
    override suspend fun load(characterId: Long): SlotMachineStateData = suspendTransaction {
        val coin = CharacterSlotMachines.selectAll()
            .where { CharacterSlotMachines.characterId eq characterId }
            .firstOrNull()
            ?.get(CharacterSlotMachines.coin)
            ?: 0

        val awards = CharacterSlotMachineAwards.selectAll()
            .where { CharacterSlotMachineAwards.characterId eq characterId }
            .map { row ->
                SlotMachineAward(
                    awardIndex = row[CharacterSlotMachineAwards.awardIndex],
                    itemId = row[CharacterSlotMachineAwards.itemId],
                    count = row[CharacterSlotMachineAwards.quantity],
                    slotIndex = row[CharacterSlotMachineAwards.slotIndex]
                )
            }
            .sortedBy { it.awardIndex }

        SlotMachineStateData(coin = coin.coerceIn(0, 999), awards = awards)
    }

    override suspend fun save(characterId: Long, state: SlotMachineStateData) {
        suspendTransaction {
            CharacterSlotMachines.upsert {
                it[CharacterSlotMachines.characterId] = characterId
                it[CharacterSlotMachines.coin] = state.coin.coerceIn(0, 999)
            }

            CharacterSlotMachineAwards.deleteWhere {
                CharacterSlotMachineAwards.characterId eq characterId
            }
            if (state.awards.isNotEmpty()) {
                CharacterSlotMachineAwards.batchInsert(state.awards.sortedBy { it.awardIndex }) { award ->
                    this[CharacterSlotMachineAwards.characterId] = characterId
                    this[CharacterSlotMachineAwards.awardIndex] = award.awardIndex
                    this[CharacterSlotMachineAwards.itemId] = award.itemId
                    this[CharacterSlotMachineAwards.quantity] = award.count
                    this[CharacterSlotMachineAwards.slotIndex] = award.slotIndex
                }
            }
        }
    }
}
