package tw.tsm.persistence.tables

import org.jetbrains.exposed.v1.core.Table

/** Persisted Gia Vien ownership, independent from ordinary BAG, BANK and placed scene objects. */
object CharacterHouseFurnitureOwned : Table("character_house_furniture_owned") {
    val characterId = long("character_id")
    val furnitureId = integer("furniture_id")
    val quantity = integer("quantity")
    override val primaryKey = PrimaryKey(characterId, furnitureId)
}
