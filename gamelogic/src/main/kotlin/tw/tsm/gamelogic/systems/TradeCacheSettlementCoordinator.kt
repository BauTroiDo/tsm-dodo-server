package tw.tsm.gamelogic.systems

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import tw.tsm.domain.interfaces.repositories.InventoryRepository
import tw.tsm.domain.interfaces.repositories.TradeAccountSnapshot

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
