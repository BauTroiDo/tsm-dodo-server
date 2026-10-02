package tw.tsm.persistence.tables

import org.jetbrains.exposed.v1.core.Table

/** Trạng thái Mỏ Thôn Thực / protocol 170 theo nhân vật. */
object CharacterMining : Table("character_mining") {
    val characterId = long("character_id")
    val mineType = integer("mine_type").default(1)
    val full1 = integer("full_1").default(0)
    val full2 = integer("full_2").default(0)
    val full3 = integer("full_3").default(0)
    val full4 = integer("full_4").default(0)
    val updatedAtMillis = long("updated_at_ms").default(0L)
    val dayLimit = bool("day_limit").default(false)

    override val primaryKey = PrimaryKey(characterId)
}
