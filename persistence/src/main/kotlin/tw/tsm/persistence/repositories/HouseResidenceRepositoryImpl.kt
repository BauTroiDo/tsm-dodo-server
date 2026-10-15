package tw.tsm.persistence.repositories

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.upsert
import tw.tsm.domain.interfaces.repositories.HouseResidenceRepository
import tw.tsm.domain.interfaces.repositories.HouseResidenceSettings
import tw.tsm.persistence.tables.CharacterHouseSettings

class HouseResidenceRepositoryImpl : HouseResidenceRepository {
    override suspend fun load(characterId: Long): HouseResidenceSettings = suspendTransaction {
        CharacterHouseSettings.selectAll()
            .where { CharacterHouseSettings.characterId eq characterId }
            .firstOrNull()?.let {
                HouseResidenceSettings(
                    name = it[CharacterHouseSettings.houseName],
                    signature = it[CharacterHouseSettings.houseSignature],
                )
            } ?: HouseResidenceSettings()
    }

    override suspend fun save(characterId: Long, settings: HouseResidenceSettings) {
        suspendTransaction {
            CharacterHouseSettings.upsert {
                it[CharacterHouseSettings.characterId] = characterId
                it[CharacterHouseSettings.houseName] = settings.name
                it[CharacterHouseSettings.houseSignature] = settings.signature
            }
        }
    }
}
