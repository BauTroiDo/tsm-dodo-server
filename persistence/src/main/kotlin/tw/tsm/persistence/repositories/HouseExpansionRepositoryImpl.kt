package tw.tsm.persistence.repositories

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import tw.tsm.domain.interfaces.repositories.HouseExpansionRepository
import tw.tsm.persistence.tables.CharacterHouseExpansion
import tw.tsm.persistence.tables.CharacterHouseFurnitureOwned
import tw.tsm.persistence.tables.CharacterMoney

/** An upgrade is one DB commit; stale level, missing furniture and low funds fail closed. */
class HouseExpansionRepositoryImpl : HouseExpansionRepository {
    override suspend fun loadLevel(characterId: Long): Int = suspendTransaction {
        CharacterHouseExpansion.selectAll().where {
            CharacterHouseExpansion.characterId eq characterId
        }.singleOrNull()?.get(CharacterHouseExpansion.totalLevel) ?: 1
    }

    override suspend fun purchaseNextLevel(
        characterId: Long, expectedLevel: Int, expectedGold: Int,
        auctionGold: Int, requiredFurnitureKinds: Int, goldCost: Int,
    ): Boolean = suspendTransaction {
        if (characterId <= 0 || expectedLevel !in 1..11 || expectedGold < goldCost ||
            goldCost !in 1..1_000_000 || requiredFurnitureKinds !in 1..255) {
            return@suspendTransaction false
        }

        // CharacterPersistenceGuard + MoneySystem gold semaphore serialize writers.
        // The SQL money row lock also blocks competing direct DB updates.
        val row = CharacterMoney.selectAll().where {
            CharacterMoney.characterId eq characterId
        }.forUpdate().singleOrNull() ?: return@suspendTransaction false
        if (row[CharacterMoney.gold] != expectedGold ||
            row[CharacterMoney.auctionGold] != auctionGold) return@suspendTransaction false

        val current = CharacterHouseExpansion.selectAll().where {
            CharacterHouseExpansion.characterId eq characterId
        }.forUpdate().singleOrNull()?.get(CharacterHouseExpansion.totalLevel) ?: 1
        if (current != expectedLevel) return@suspendTransaction false

        val distinct = CharacterHouseFurnitureOwned.selectAll().where {
            CharacterHouseFurnitureOwned.characterId eq characterId
        }.count { it[CharacterHouseFurnitureOwned.quantity] > 0 }
        if (distinct < requiredFurnitureKinds) return@suspendTransaction false

        CharacterHouseExpansion.upsert {
            it[CharacterHouseExpansion.characterId] = characterId
            it[CharacterHouseExpansion.totalLevel] = expectedLevel + 1
        }
        val debitedRows = CharacterMoney.update({ CharacterMoney.characterId eq characterId }) {
            it[CharacterMoney.gold] = expectedGold - goldCost
        }
        // The level upsert is in the same SQL transaction. An unexpected
        // missing/duplicate money update must throw and roll back both writes;
        // never acknowledge a paid level with no actual gold debit.
        check(debitedRows == 1) {
            "Gia Vien upgrade money update affected $debitedRows rows for charId=$characterId"
        }
        true
    }
}
