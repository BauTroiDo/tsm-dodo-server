package tw.tsm.persistence.tables

import org.jetbrains.exposed.v1.core.Table

object CharacterAstrolabes : Table("character_astrolabes") {
    val id = long("id").autoIncrement()
    val characterId = long("character_id").uniqueIndex()
    val natalPoint = integer("natal_point").default(0)
    val star1 = integer("star_1").default(0)
    val star2 = integer("star_2").default(0)
    val star3 = integer("star_3").default(0)
    val star4 = integer("star_4").default(0)
    val star5 = integer("star_5").default(0)
    val star6 = integer("star_6").default(0)
    val star7 = integer("star_7").default(0)

    override val primaryKey = PrimaryKey(id)
}
