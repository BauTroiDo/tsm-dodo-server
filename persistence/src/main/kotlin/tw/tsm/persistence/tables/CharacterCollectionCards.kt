package tw.tsm.persistence.tables

import org.jetbrains.exposed.v1.core.Table

object CharacterCollectionCards : Table("character_collection_cards") {
    val characterId = long("character_id")
    val cardId = integer("card_id")
    val level = integer("level").default(0)
    /** 0=not equipped; 1..N=equipped order/slot. */
    val equipSlot = integer("equip_slot").default(0)

    override val primaryKey = PrimaryKey(characterId, cardId)
}
