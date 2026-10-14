package tw.tsm.persistence.tables

import org.jetbrains.exposed.v1.core.Table

/** DVT decorative placements are not inventory items and cannot be cashed out. */
object CharacterHouseFurniture : Table("character_house_furniture") {
    val characterId = long("character_id")
    val building = integer("building")
    val floor = integer("floor")
    val index = integer("item_index")
    val furnitureId = integer("furniture_id")
    val x = integer("block_x")
    val y = integer("block_y")
    val z = integer("block_z")
    val direction = integer("direction")
    override val primaryKey = PrimaryKey(characterId, building, floor, index)
}
