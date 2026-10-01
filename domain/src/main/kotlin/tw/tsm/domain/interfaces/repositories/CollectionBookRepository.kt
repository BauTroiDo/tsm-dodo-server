package tw.tsm.domain.interfaces.repositories

import tw.tsm.domain.data.CollectionCardState

interface CollectionBookRepository {
    suspend fun loadStyleFlags(characterId: Long): Map<Int, Int>
    suspend fun saveStyleFlag(characterId: Long, styleId: Int, flag: Int)

    suspend fun loadCardStates(characterId: Long): Map<Int, CollectionCardState>
    suspend fun saveCardState(characterId: Long, state: CollectionCardState)
}
