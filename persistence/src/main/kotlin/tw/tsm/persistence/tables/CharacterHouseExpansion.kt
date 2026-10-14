package tw.tsm.persistence.tables

import org.jetbrains.exposed.v1.core.Table

object CharacterHouseExpansion : Table("character_house_expansion") {
    val characterId = long("character_id")
    val totalLevel = integer("total_level").default(1)
    override val primaryKey = PrimaryKey(characterId)
}
