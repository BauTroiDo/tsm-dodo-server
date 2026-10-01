package tw.tsm.persistence.tables

import org.jetbrains.exposed.v1.core.Table

object CharacterCollectionStyles : Table("character_collection_styles") {
    val characterId = long("character_id")
    val styleId = integer("style_id")
    val flag = integer("flag").default(0)

    override val primaryKey = PrimaryKey(characterId, styleId)
}
