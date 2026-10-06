package tw.tsm.persistence.repositories

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.upsert
import tw.tsm.domain.data.PokeStateData
import tw.tsm.domain.interfaces.repositories.PokeRepository
import tw.tsm.persistence.tables.CharacterPokeAwards
import tw.tsm.persistence.tables.CharacterPokes

class PokeRepositoryImpl : PokeRepository {
    override suspend fun load(characterId: Long): PokeStateData = suspendTransaction {
        val coin = CharacterPokes.selectAll()
            .where { CharacterPokes.characterId eq characterId }
            .firstOrNull()?.get(CharacterPokes.coin) ?: 0
        val opened = CharacterPokeAwards.selectAll()
            .where { CharacterPokeAwards.characterId eq characterId }
            .associate {
                it[CharacterPokeAwards.rewardId] to it[CharacterPokeAwards.slotIndex]
            }
        PokeStateData(coin.coerceIn(0, 999), opened)
    }

    override suspend fun save(characterId: Long, state: PokeStateData) {
        suspendTransaction {
            CharacterPokes.upsert {
                it[CharacterPokes.characterId] = characterId
                it[CharacterPokes.coin] = state.coin.coerceIn(0, 999)
            }
            CharacterPokeAwards.deleteWhere { CharacterPokeAwards.characterId eq characterId }
            if (state.openedSlots.isNotEmpty()) {
                CharacterPokeAwards.batchInsert(state.openedSlots.entries) { (rewardId, slotIndex) ->
                    this[CharacterPokeAwards.characterId] = characterId
                    this[CharacterPokeAwards.rewardId] = rewardId
                    this[CharacterPokeAwards.slotIndex] = slotIndex
                }
            }
        }
    }
}
