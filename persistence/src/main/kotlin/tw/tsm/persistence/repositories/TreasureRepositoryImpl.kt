package tw.tsm.persistence.repositories

import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.update
import tw.tsm.domain.interfaces.repositories.TreasureLevelRequirement
import tw.tsm.domain.interfaces.repositories.TreasureRepository
import tw.tsm.domain.interfaces.repositories.TreasureState
import tw.tsm.persistence.tables.CharacterMoney
import tw.tsm.persistence.tables.CharacterTreasures
import tw.tsm.persistence.tables.Inventories

/**
 * STAGED, UNWIRED source-cost and ownership transaction. No C123 call reaches
 * this repository. The caller supplies only a card ID, expected state/version,
 * and actual material BAG slot; source requirements are injected at construction,
 * never accepted from the client packet.
 *
 * Character Treasures, inventories and gold are updated in ONE SQL transaction.
 * This is not ready to connect to the online MoneySystem/InventorySystem because
 * their in-memory caches need coordinated commit/resync. Crucially, no stand-
 * alone state "save()" method can accidentally grant progress without a debit.
 */
class TreasureRepositoryImpl(
    private val originalLevelCosts: Map<Pair<Int, Int>, TreasureLevelRequirement>,
    // Original Item_C.kind98.skillLink mapping, injected from trusted source.
    // Defaults to empty so an unwired repository can NEVER bank a card.
    private val originalCardItemIds: Map<Int, Int> = emptyMap(),
) : TreasureRepository {
    init {
        require(originalCardItemIds.keys.all { it in 1..114 })
        require(originalCardItemIds.values.all { it in 25601..25714 })
        require(originalCardItemIds.values.size == originalCardItemIds.values.toSet().size)
        for ((key, cost) in originalLevelCosts) {
            require(key == (cost.treasureId to cost.targetLevel))
        }
    }

    override suspend fun load(characterId: Long): List<TreasureState> {
        require(characterId > 0)
        return suspendTransaction {
            CharacterTreasures.selectAll()
                .where { CharacterTreasures.characterId eq characterId }
                .map { row ->
                    TreasureState(
                        treasureId = row[CharacterTreasures.treasureId],
                        cardCopiesTotal = row[CharacterTreasures.cardCopiesTotal],
                        level = row[CharacterTreasures.level],
                        star = row[CharacterTreasures.star],
                        starExp = row[CharacterTreasures.starExp],
                        version = row[CharacterTreasures.version],
                    )
                }
        }
    }

    override suspend fun bankCard(
        characterId: Long,
        treasureId: Int,
        expectedVersion: Long,
        cardBagSlot: Int,
        quantity: Int,
    ): Boolean {
        if (characterId <= 0 || treasureId !in 1..114 ||
            expectedVersion < 0 || cardBagSlot !in 0..65535 || quantity <= 0
        ) return false
        // No trusted original definition => no card can be minted.
        val expectedItemId = originalCardItemIds[treasureId] ?: return false

        return suspendTransaction {
            // Lock the actual BAG slot and verify the stored physical item.
            // A forged client skillLink, item ID or stale slot cannot credit S123.
            val storedItem = Inventories.selectAll().where {
                (Inventories.characterId eq characterId) and
                    (Inventories.storageType eq 1) and
                    (Inventories.slot eq cardBagSlot)
            }.forUpdate().singleOrNull() ?: return@suspendTransaction false
            val beforeQty = storedItem[Inventories.quantity]
            if (storedItem[Inventories.itemId] != expectedItemId ||
                beforeQty < quantity
            ) return@suspendTransaction false

            val owned = CharacterTreasures.selectAll().where {
                (CharacterTreasures.characterId eq characterId) and
                    (CharacterTreasures.treasureId eq treasureId)
            }.forUpdate().singleOrNull()

            if ((owned?.get(CharacterTreasures.version) ?: 0L) != expectedVersion) {
                return@suspendTransaction false
            }
            val beforeCards = owned?.get(CharacterTreasures.cardCopiesTotal) ?: 0
            // Avoid integer overflow silently creating negative entitlements.
            if (beforeCards.toLong() + quantity.toLong() > Int.MAX_VALUE) {
                return@suspendTransaction false
            }

            val remainder = beforeQty - quantity
            if (remainder == 0) {
                check(Inventories.deleteWhere {
                    (Inventories.characterId eq characterId) and
                        (Inventories.storageType eq 1) and
                        (Inventories.slot eq cardBagSlot)
                } == 1)
            } else {
                check(Inventories.update({
                    (Inventories.characterId eq characterId) and
                        (Inventories.storageType eq 1) and
                        (Inventories.slot eq cardBagSlot)
                }) { it[Inventories.quantity] = remainder } == 1)
            }
            if (owned == null) {
                check(expectedVersion == 0L)
                // Crediting a new S123 row is allowed ONLY in this debit
                // transaction, not on C123-001 read or character login.
                CharacterTreasures.insert {
                    it[CharacterTreasures.characterId] = characterId
                    it[CharacterTreasures.treasureId] = treasureId
                    it[CharacterTreasures.cardCopiesTotal] = quantity
                    it[CharacterTreasures.level] = 0
                    it[CharacterTreasures.star] = 0
                    it[CharacterTreasures.starExp] = 0
                    it[CharacterTreasures.version] = 1L
                    it[CharacterTreasures.updatedAtMs] = System.currentTimeMillis()
                }
            } else {
                check(CharacterTreasures.update({
                    (CharacterTreasures.characterId eq characterId) and
                        (CharacterTreasures.treasureId eq treasureId) and
                        (CharacterTreasures.version eq expectedVersion)
                }) {
                    it[CharacterTreasures.cardCopiesTotal] = beforeCards + quantity
                    it[CharacterTreasures.version] = expectedVersion + 1L
                    it[CharacterTreasures.updatedAtMs] = System.currentTimeMillis()
                } == 1)
            }
            true
        }
    }

    override suspend fun purchaseLevel(
        characterId: Long,
        treasureId: Int,
        expectedLevel: Int,
        expectedVersion: Long,
        materialBagSlot: Int,
    ): Boolean {
        if (characterId <= 0 || treasureId !in 1..114 ||
            expectedLevel !in 0..9 || expectedVersion < 0 ||
            materialBagSlot !in 0..65535
        ) return false
        val targetLevel = expectedLevel + 1
        val cost = originalLevelCosts[treasureId to targetLevel] ?: return false

        return suspendTransaction {
            // A missing row means NO verified card acquisition yet. Never
            // create one via opening C123 UI or attempted upgrading.
            val owned = CharacterTreasures.selectAll().where {
                (CharacterTreasures.characterId eq characterId) and
                    (CharacterTreasures.treasureId eq treasureId)
            }.forUpdate().singleOrNull() ?: return@suspendTransaction false

            if (owned[CharacterTreasures.level] != expectedLevel ||
                owned[CharacterTreasures.version] != expectedVersion
            ) return@suspendTransaction false

            val investedCopies = (1..targetLevel).sumOf { step ->
                originalLevelCosts[treasureId to step]?.copies
                    ?: return@suspendTransaction false
            }
            if (owned[CharacterTreasures.cardCopiesTotal] < investedCopies) {
                return@suspendTransaction false
            }

            val material = Inventories.selectAll().where {
                (Inventories.characterId eq characterId) and
                    (Inventories.storageType eq 1) and // Original EThings.BAG
                    (Inventories.slot eq materialBagSlot)
            }.forUpdate().singleOrNull() ?: return@suspendTransaction false

            // Source Treasure level-up material is item29869. The client may
            // provide an arbitrary bag slot: the actual DB item must match.
            val quantity = material[Inventories.quantity]
            if (material[Inventories.itemId] != 29869 || quantity < cost.materials) {
                return@suspendTransaction false
            }

            val money = CharacterMoney.selectAll().where {
                CharacterMoney.characterId eq characterId
            }.forUpdate().singleOrNull() ?: return@suspendTransaction false
            if (money[CharacterMoney.gold] < cost.gold) return@suspendTransaction false

            // These THREE mutations share one transaction and roll back as a
            // unit on ANY database error; no rewards/stat boosts here.
            val remaining = quantity - cost.materials
            if (remaining == 0) {
                check(Inventories.deleteWhere {
                    (Inventories.characterId eq characterId) and
                        (Inventories.storageType eq 1) and
                        (Inventories.slot eq materialBagSlot)
                } == 1)
            } else {
                check(Inventories.update({
                    (Inventories.characterId eq characterId) and
                        (Inventories.storageType eq 1) and
                        (Inventories.slot eq materialBagSlot)
                }) { it[Inventories.quantity] = remaining } == 1)
            }
            check(CharacterMoney.update({ CharacterMoney.characterId eq characterId }) {
                it[CharacterMoney.gold] = money[CharacterMoney.gold] - cost.gold
            } == 1)
            check(CharacterTreasures.update({
                (CharacterTreasures.characterId eq characterId) and
                    (CharacterTreasures.treasureId eq treasureId) and
                    (CharacterTreasures.version eq expectedVersion)
            }) {
                it[CharacterTreasures.level] = targetLevel
                it[CharacterTreasures.version] = expectedVersion + 1
                it[CharacterTreasures.updatedAtMs] = System.currentTimeMillis()
            } == 1)
            true
        }
    }
}
