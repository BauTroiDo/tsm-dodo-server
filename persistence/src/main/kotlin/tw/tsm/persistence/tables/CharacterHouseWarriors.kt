package tw.tsm.persistence.tables

import org.jetbrains.exposed.v1.core.Table

object CharacterHouseWarriors : Table("character_house_warriors") {
    val characterId = long("character_id")
    val building = integer("building")
    val floor = integer("floor")
    val slot = integer("slot")
    val npcId = integer("npc_id")
    override val primaryKey = PrimaryKey(characterId, building, floor, slot)
}
