package tw.tsm.persistence.repositories

import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import tw.tsm.domain.interfaces.repositories.HouseWarriorRepository
import tw.tsm.persistence.tables.CharacterHouseWarriors

class HouseWarriorRepositoryImpl : HouseWarriorRepository {
    override suspend fun load(characterId: Long, building: Int, floor: Int): List<Int> =
        suspendTransaction {
            val output = MutableList(4) { 0 }
            CharacterHouseWarriors.selectAll().where {
                (CharacterHouseWarriors.characterId eq characterId) and
                    (CharacterHouseWarriors.building eq building) and
                    (CharacterHouseWarriors.floor eq floor)
            }.forEach {
                val slot = it[CharacterHouseWarriors.slot]
                if (slot in 1..4) output[slot-1] = it[CharacterHouseWarriors.npcId]
            }
            output
        }

    override suspend fun replace(characterId: Long, building: Int, floor: Int, npcIds: List<Int>) {
        require(npcIds.size == 4)
        suspendTransaction {
            CharacterHouseWarriors.deleteWhere {
                (CharacterHouseWarriors.characterId eq characterId) and
                    (CharacterHouseWarriors.building eq building) and
                    (CharacterHouseWarriors.floor eq floor)
            }
            npcIds.forEachIndexed { i, id ->
                if (id != 0) CharacterHouseWarriors.insert {
                    it[CharacterHouseWarriors.characterId] = characterId
                    it[CharacterHouseWarriors.building] = building
                    it[CharacterHouseWarriors.floor] = floor
                    it[CharacterHouseWarriors.slot] = i+1
                    it[CharacterHouseWarriors.npcId] = id
                }
            }
        }
    }
}
