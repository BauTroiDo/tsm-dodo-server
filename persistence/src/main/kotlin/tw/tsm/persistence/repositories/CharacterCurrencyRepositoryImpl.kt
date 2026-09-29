package tw.tsm.persistence.repositories

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.upsert
import tw.tsm.domain.interfaces.repositories.CharacterCurrencyRepository
import tw.tsm.persistence.tables.CharacterCurrencies

class CharacterCurrencyRepositoryImpl : CharacterCurrencyRepository {
    override suspend fun loadAll(characterId: Long): Map<Int, Int> = suspendTransaction {
        CharacterCurrencies.selectAll()
            .where { CharacterCurrencies.characterId eq characterId }
            .associate { row -> row[CharacterCurrencies.currencyKind] to row[CharacterCurrencies.value] }
    }

    override suspend fun setValue(characterId: Long, currencyKind: Int, value: Int) {
        suspendTransaction {
            CharacterCurrencies.upsert {
                it[CharacterCurrencies.characterId] = characterId
                it[CharacterCurrencies.currencyKind] = currencyKind
                it[CharacterCurrencies.value] = value.coerceAtLeast(0)
            }
        }
    }
}
