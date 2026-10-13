package tw.tsm.gamelogic.systems

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import tw.tsm.domain.interfaces.repositories.InventoryRepository
import tw.tsm.domain.interfaces.repositories.TradeAccountSnapshot
import tw.tsm.domain.interfaces.repositories.TradeReceiptLookup

/**
 * STAGED ONLY. No C025 wiring.
 *
 * A lost SQL acknowledgement is NOT permission to retry a BAG/gold transfer.
 * Read the durable receipt and BOTH persisted accounts under one SQL snapshot,
 * then repair interrupted RAM publication only when DB is exactly at the
 * committed post-trade state and each local BAG/money cache is either the
 * matching pre-state or matching post-state. Any newer local or persisted
 * mutation must remain untouched for a separate audited reconciliation.
 *
 * Caller must never issue success solely from RESTORED / ALREADY_SYNCED; a
 * higher-level state machine must also bind its session UUID and notification.
 */
class TradeReceiptCacheReconciler(
    private val repository: InventoryRepository,
    private val inventory: InventorySystem,
    private val money: MoneySystem,
) {
    enum class Result {
        RESTORED,
        ALREADY_SYNCED,
        RECEIPT_NOT_FOUND,
        TOKEN_CONFLICT,
        DATABASE_DIVERGED,
        CACHE_DIVERGED,
        CACHE_UNLOADED,
        INDETERMINATE,
        INVALID,
    }

    suspend fun reconcile(
        operationId: String,
        first: TradeAccountSnapshot,
        second: TradeAccountSnapshot,
    ): Result {
        if (first.characterId <= 0 || second.characterId <= 0 ||
            first.characterId == second.characterId
        ) return Result.INVALID

        // An indeterminate SQL result can outlive an already-cancelled
        // dispatcher. Do not abandon cache repair halfway through publication.
        return withContext(NonCancellable) {
            try {
                CharacterPersistenceGuard.withLocks(first.characterId, second.characterId) {
                    money.withTradeGoldMutationFence(first.characterId, second.characterId) {
                        val observation = repository.inspectTwoCharacterTradeReceiptAtomic(
                            operationId, first, second
                        )
                        when (observation.receipt) {
                            TradeReceiptLookup.NOT_FOUND ->
                                return@withTradeGoldMutationFence Result.RECEIPT_NOT_FOUND
                            TradeReceiptLookup.TOKEN_CONFLICT ->
                                return@withTradeGoldMutationFence Result.TOKEN_CONFLICT
                            TradeReceiptLookup.MATCHED -> Unit
                        }

                        val dbFirst = observation.first
                        val dbSecond = observation.second
                        if (dbFirst == null || dbSecond == null ||
                            dbFirst.characterId != first.characterId ||
                            dbSecond.characterId != second.characterId ||
                            dbFirst.bag != first.afterBag ||
                            dbSecond.bag != second.afterBag ||
                            dbFirst.gold != first.afterGold ||
                            dbSecond.gold != second.afterGold ||
                            dbFirst.auctionGold != first.auctionGold ||
                            dbSecond.auctionGold != second.auctionGold
                        ) return@withTradeGoldMutationFence Result.DATABASE_DIVERGED

                        val sides = listOf(first, second)
                        val cachedBags = sides.map { inventory.getLoadedBagSnapshot(it.characterId) }
                        val cachedMoney = sides.map { money.getLoadedMoney(it.characterId) }
                        if (cachedBags.any { it == null } || cachedMoney.any { it == null }) {
                            return@withTradeGoldMutationFence Result.CACHE_UNLOADED
                        }

                        // No partial publication is allowed if ANY cache has
                        // moved on since this specific trade was planned.
                        val allSafe = sides.indices.all { i ->
                            val side = sides[i]
                            cachedBags[i] == side.beforeBag || cachedBags[i] == side.afterBag
                        } && sides.indices.all { i ->
                            val side = sides[i]
                            cachedMoney[i] == (side.beforeGold to side.auctionGold) ||
                                cachedMoney[i] == (side.afterGold to side.auctionGold)
                        }
                        if (!allSafe) return@withTradeGoldMutationFence Result.CACHE_DIVERGED

                        val already = sides.indices.all { i ->
                            cachedBags[i] == sides[i].afterBag &&
                                cachedMoney[i] == (sides[i].afterGold to sides[i].auctionGold)
                        }
                        if (already) return@withTradeGoldMutationFence Result.ALREADY_SYNCED

                        // This block makes NO suspend calls. It holds BOTH
                        // character persistence locks and BOTH gold fences.
                        for (side in sides) {
                            inventory.publishCommittedTradeBagSync(
                                side.characterId, side.afterBag
                            )
                            money.publishCommittedTradeGoldSync(
                                side.characterId, side.afterGold, side.auctionGold
                            )
                        }
                        Result.RESTORED
                    }
                }
            } catch (e: Exception) {
                // A network/database exception does not imply a rollback.
                Result.INDETERMINATE
            }
        }
    }
}
