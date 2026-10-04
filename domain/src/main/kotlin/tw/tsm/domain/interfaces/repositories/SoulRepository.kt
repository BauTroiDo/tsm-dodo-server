package tw.tsm.domain.interfaces.repositories

import tw.tsm.domain.valueobjects.SoulThingData

interface SoulRepository {
    suspend fun findByCharacterId(characterId: Long): List<SoulThingData>
    suspend fun save(characterId: Long, soul: SoulThingData)
    suspend fun delete(characterId: Long, sn: Long)
}
