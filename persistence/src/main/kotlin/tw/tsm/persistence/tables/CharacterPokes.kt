package tw.tsm.persistence.tables

import org.jetbrains.exposed.v1.core.Table

object CharacterPokes : Table("character_pokes") {
    val characterId = long("character_id")
    val coin = integer("coin").default(0)
    override val primaryKey = PrimaryKey(characterId)
}

object CharacterPokeAwards : Table("character_poke_awards") {
    val characterId = long("character_id")
    val rewardId = integer("reward_id")
    val slotIndex = integer("slot_index")
    override val primaryKey = PrimaryKey(characterId, rewardId)
}
