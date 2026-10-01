package tw.tsm.domain.interfaces.repositories

import tw.tsm.domain.valueobjects.AstrolabeData

interface AstrolabeRepository {
    suspend fun findByCharacterId(characterId: Long): AstrolabeData?
    suspend fun save(characterId: Long, data: AstrolabeData)
}
