package tw.tsm.domain.interfaces.repositories

import tw.tsm.domain.data.PokeStateData

interface PokeRepository {
    suspend fun load(characterId: Long): PokeStateData
    suspend fun save(characterId: Long, state: PokeStateData)
}
