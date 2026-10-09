package tw.tsm.persistence.tables

import org.jetbrains.exposed.v1.core.Table

object CharacterElfProfiles : Table("character_elf_profiles") {
    val characterId = long("character_id")
    val activeNo = integer("active_no").default(999)
    val feedTotal = integer("feed_total").default(0)
    override val primaryKey = PrimaryKey(characterId)
}

object CharacterElfProgress : Table("character_elf_progress") {
    val characterId = long("character_id")
    val elfNo = integer("elf_no")
    val level = integer("level").default(1)
    val exp = integer("exp").default(0)
    override val primaryKey = PrimaryKey(characterId, elfNo)
}
