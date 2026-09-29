package tw.tsm.domain.interfaces.repositories

/** Persistent DVT non-core currencies (ECurrency 4..16). */
interface CharacterCurrencyRepository {
    suspend fun loadAll(characterId: Long): Map<Int, Int>
    suspend fun setValue(characterId: Long, currencyKind: Int, value: Int)
}
