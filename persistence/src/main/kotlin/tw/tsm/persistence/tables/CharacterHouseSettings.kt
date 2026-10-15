package tw.tsm.persistence.tables

import org.jetbrains.exposed.v1.core.Table

/** DVT protocol 106 custom house name/signature, keyed by persistent DB character ID. */
object CharacterHouseSettings : Table("character_house_settings") {
    val characterId = long("character_id")
    val houseName = varchar("house_name", 80).default("Gia Vien")
    val houseSignature = varchar("house_signature", 240).default("")
    override val primaryKey = PrimaryKey(characterId)
}
