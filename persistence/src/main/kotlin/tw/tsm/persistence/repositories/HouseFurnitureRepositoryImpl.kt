package tw.tsm.persistence.repositories

import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import tw.tsm.domain.interfaces.repositories.HouseFurniturePlacement
import tw.tsm.domain.interfaces.repositories.HouseFurnitureRepository
import tw.tsm.persistence.tables.CharacterHouseFurniture

class HouseFurnitureRepositoryImpl : HouseFurnitureRepository {
    override suspend fun load(characterId: Long, building: Int, floor: Int): List<HouseFurniturePlacement> =
        suspendTransaction {
            CharacterHouseFurniture.selectAll().where {
                (CharacterHouseFurniture.characterId eq characterId) and
                    (CharacterHouseFurniture.building eq building) and
                    (CharacterHouseFurniture.floor eq floor)
            }.orderBy(CharacterHouseFurniture.index to SortOrder.ASC).map {
                HouseFurniturePlacement(
                    it[CharacterHouseFurniture.furnitureId],
                    it[CharacterHouseFurniture.x],
                    it[CharacterHouseFurniture.y],
                    it[CharacterHouseFurniture.z],
                    it[CharacterHouseFurniture.direction],
                )
            }
        }

    override suspend fun replace(
        characterId: Long, building: Int, floor: Int, furniture: List<HouseFurniturePlacement>
    ) {
        suspendTransaction {
            CharacterHouseFurniture.deleteWhere {
                (CharacterHouseFurniture.characterId eq characterId) and
                    (CharacterHouseFurniture.building eq building) and
                    (CharacterHouseFurniture.floor eq floor)
            }
            furniture.forEachIndexed { i, f ->
                CharacterHouseFurniture.insert {
                    it[CharacterHouseFurniture.characterId] = characterId
                    it[CharacterHouseFurniture.building] = building
                    it[CharacterHouseFurniture.floor] = floor
                    it[CharacterHouseFurniture.index] = i
                    it[CharacterHouseFurniture.furnitureId] = f.furnitureId
                    it[CharacterHouseFurniture.x] = f.x
                    it[CharacterHouseFurniture.y] = f.y
                    it[CharacterHouseFurniture.z] = f.z
                    it[CharacterHouseFurniture.direction] = f.direction
                }
            }
        }
    }
}
