package tw.tsm.persistence.tables

import org.jetbrains.exposed.v1.core.Table

/**
 * Dormant per-character DVT Treasure progress. Not in DatabaseFactory schema
 * creation yet: original card acquisition and StarExp need source parity first.
 * All 114 card identities are original TreasureLv.id/Item_C.kind98.skillLink.
 */
object CharacterTreasures : Table("character_treasures") {
    val characterId = long("character_id")
    val treasureId = integer("treasure_id")
    val cardCopiesTotal = integer("card_copies_total").default(0)
    val level = integer("level").default(0)
    val star = integer("star").default(0)
    val starExp = integer("star_exp").default(0)
    val version = long("version").default(0L)
    val updatedAtMs = long("updated_at_ms").default(0L)
    override val primaryKey = PrimaryKey(characterId, treasureId)
}
