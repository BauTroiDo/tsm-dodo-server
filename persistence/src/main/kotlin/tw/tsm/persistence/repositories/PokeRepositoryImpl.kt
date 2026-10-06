package tw.tsm.persistence.repositories

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.upsert
import tw.tsm.domain.data.PokeStateData
import tw.tsm.domain.interfaces.repositories.PokeRepository
import tw.tsm.persistence.tables.CharacterPoke
import tw.tsm.persistence.tables.CharacterPokeOpened

class PokeRepositoryImpl : PokeRepository {
    override suspend fun load(characterId: Long): PokeStateData = suspendTransaction {
        val coin = CharacterPoke.selectAll()
            .where { CharacterPoke.characterId eq characterId }
            .firstOrNull()?.get(CharacterPoke.coin) ?: 0
        val opened = CharacterPokeOpened.selectAll()
            .where { CharacterPokeOpened.characterId eq characterId }
            .associate { it[CharacterPokeOpened.awardId] to it[CharacterPokeOpened.gridIndex] }
        PokeStateData(coin.coerceIn(0, 999), opened)
    }

    override suspend fun save(characterId: Long, state: PokeStateData) {
        suspendTransaction {
            CharacterPoke.upsert {
                it[CharacterPoke.characterId] = characterId
                it[CharacterPoke.coin] = state.coin.coerceIn(0, 999)
            }
            CharacterPokeOpened.deleteWhere { CharacterPokeOpened.characterId eq characterId }
            if (state.opened.isNotEmpty()) {
                CharacterPokeOpened.batchInsert(state.opened.entries.sortedBy { it.key }) { e ->
                    this[CharacterPokeOpened.characterId] = characterId
                    this[CharacterPokeOpened.awardId] = e.key
                    this[CharacterPokeOpened.gridIndex] = e.value
                }
            }
        }
    }
}
