package tw.tsm.persistence.tables

import org.jetbrains.exposed.v1.core.Table

object CharacterPoke : Table("character_poke") {
    val characterId = long("character_id")
    val coin = integer("coin").default(0)
    override val primaryKey = PrimaryKey(characterId)
}

object CharacterPokeOpened : Table("character_poke_opened") {
    val characterId = long("character_id").index()
    val awardId = integer("award_id")
    val gridIndex = integer("grid_index")
    override val primaryKey = PrimaryKey(characterId, awardId)
}
