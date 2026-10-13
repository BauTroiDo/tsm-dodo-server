package tw.tsm.gamelogic.systems

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import tw.tsm.domain.interfaces.repositories.InventoryRepository
import tw.tsm.domain.interfaces.repositories.TradeAccountSnapshot
import tw.tsm.domain.interfaces.repositories.TradeReceiptCommit

/**
 * STAGED ONLY — unregistered and NOT called by TradeSystem/C025.
 *
 * Builds a strict two-character cache publication boundary on top of the
 * atomic SQL BAG+GOLD transfer. All participating save/logout/suspend BAG
 * operations share CharacterPersistenceGuard, so none can save a stale BAG
 * snapshot between SQL commit and RAM publication.
 *
 * CRITICAL: unrelated synchronous MoneySystem gold mutators and external
 * direct BAG writers do not all acquire this coroutine mutex yet. Turning
 * this on in live C025 without fencing them would allow stale cache gold or
 * a lost concurrent reward. Do NOT wire it until these writers are audited.
 */
class TradeCacheSettlementCoordinator(
    private val repository: InventoryRepository,
    private val inventory: InventorySystem,
    private val money: MoneySystem,
) {
    /** STAGED ONLY: never call from active C025 until all BAG writers fenced. */
    enum class ReceiptStage {
        COMMITTED_NOW,
        ALREADY_COMMITTED,
        REJECTED,
        TOKEN_CONFLICT,
        INDETERMINATE,
    }

    /**
     * One durable SQL BAG+gold receipt and two guarded cache publications.
     *
     * If SQL throws, treat its result as UNKNOWN: the transaction could have
     * committed. Never retry. The higher-level settlement must invoke
     * TradeReceiptCacheReconciler OUTSIDE these locks, because attempting to
     * acquire the same persistence mutex recursively would deadlock.
     *
     * On ALREADY_COMMITTED we intentionally do NOT publish stale planned
     * caches; the external reconciler must verify actual SQL and local state.
     */
    suspend fun stageCommitWithReceipt(
        operationId: String,
        first: TradeAccountSnapshot,
        second: TradeAccountSnapshot,
    ): ReceiptStage {
        if (operationId.isBlank() || first.characterId <= 0 ||
            second.characterId <= 0 || first.characterId == second.characterId
        ) return ReceiptStage.REJECTED

        return CharacterPersistenceGuard.withLocks(first.characterId, second.characterId) {
            money.withTradeGoldMutationFence(first.characterId, second.characterId) {
                val sides = listOf(first, second)
                val sameBefore = sides.all { side ->
                    inventory.getLoadedBagSnapshot(side.characterId) == side.beforeBag &&
                        money.getLoadedMoney(side.characterId) ==
                            (side.beforeGold to side.auctionGold)
                }
                if (!sameBefore) return@withTradeGoldMutationFence ReceiptStage.REJECTED

                withContext(NonCancellable) {
                    val outcome = try {
                        repository.commitTwoCharacterTradeWithReceiptAtomic(
                            operationId, first, second
                        )
                    } catch (e: Exception) {
                        // A lost DB commit ACK is never a proven rollback.
                        return@withContext ReceiptStage.INDETERMINATE
                    }
                    when (outcome) {
                        TradeReceiptCommit.COMMITTED_NOW -> {
                            // Both SQL records and permanent receipt committed.
                            // Do NOT suspend between these RAM publications.
                            for (side in sides) {
                                inventory.publishCommittedTradeBagSync(
                                    side.characterId, side.afterBag
                                )
                                money.publishCommittedTradeGoldSync(
                                    side.characterId, side.afterGold, side.auctionGold
                                )
                            }
                            ReceiptStage.COMMITTED_NOW
                        }
                        TradeReceiptCommit.ALREADY_COMMITTED ->
                            ReceiptStage.ALREADY_COMMITTED
                        TradeReceiptCommit.REJECTED ->
                            ReceiptStage.REJECTED
                        TradeReceiptCommit.TOKEN_CONFLICT ->
                            ReceiptStage.TOKEN_CONFLICT
                    }
                }
            }
        }
    }

    suspend fun stageCommit(
        first: TradeAccountSnapshot,
        second: TradeAccountSnapshot,
    ): Boolean {
        if (first.characterId <= 0 || second.characterId <= 0 ||
            first.characterId == second.characterId
        ) return false

        return CharacterPersistenceGuard.withLocks(first.characterId, second.characterId) {
            // Existing sync gold writers don't hold the coroutine mutex.
            // Acquire both short-lived gold fences before pre-state reads,
            // keep them until SQL commit and both RAM snapshots are published.
            money.withTradeGoldMutationFence(first.characterId, second.characterId) {
            val sides = listOf(first, second)
            // The entire online BAG snapshot, not merely offered slots,
            // must equal the input to the optimistic SQL transaction.
            if (sides.any { side ->
                    inventory.getLoadedBagSnapshot(side.characterId) != side.beforeBag ||
                        money.getLoadedMoney(side.characterId) !=
                            (side.beforeGold to side.auctionGold)
                }
            ) return@withTradeGoldMutationFence false

            // Cancellation after the database commits must not interrupt
            // updating either character's RAM cache; otherwise autosave/
            // logout could resurrect transferred items or stale gold.
            withContext(NonCancellable) {
                if (!repository.commitTwoCharacterTradeAtomic(first, second)) {
                    return@withContext false
                }

                // This section must perform NO suspending calls. The two
                // character locks are still owned until both snapshots
                // and both currency balances are published.
                for (side in sides) {
                    inventory.publishCommittedTradeBagSync(side.characterId, side.afterBag)
                    money.publishCommittedTradeGoldSync(
                        side.characterId, side.afterGold, side.auctionGold
                    )
                }
                true
            }
            }
        }
    }
}
