package tw.tsm.persistence.tables

import org.jetbrains.exposed.v1.core.Table

/** DVT Role.playerData.currencys entries not stored by point/gold fields. */
object CharacterCurrencies : Table("character_currencies") {
    val characterId = long("character_id").index()
    val currencyKind = integer("currency_kind")
    val value = integer("value").default(0)

    override val primaryKey = PrimaryKey(characterId, currencyKind)
}
