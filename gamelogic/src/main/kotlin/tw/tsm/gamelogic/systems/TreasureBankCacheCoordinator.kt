package tw.tsm.gamelogic.systems

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import tw.tsm.domain.interfaces.repositories.TreasureRepository

/**
 * STAGED ONLY - not registered or called from a network handler.
 *
 * Coordinates the *already proven* SQL BAG -> Treasure transfer with an
 * online InventorySystem snapshot. CharacterPersistenceGuard is also used by
 * InventorySystem.save/saveAndUnload, so those full-bag snapshot writers
 * cannot interleave between the debit and cache publication.
 *
 * IMPORTANT: Other bag mutations that don't participate in this guard can
 * still race. This coordinator must NOT be wired into C023/123 until all
 * relevant writers adopt the same fence and original ability245 semantics
 * are identified.
 */
class TreasureBankCacheCoordinator(
    private val repository: TreasureRepository,
    private val inventory: InventorySystem,
    private val originalCardItemIds: Map<Int, Int> = emptyMap(),
) {
    init {
        require(originalCardItemIds.keys.all { it in 1..114 })
        require(originalCardItemIds.values.all { it in 25601..25714 })
        require(originalCardItemIds.values.size == originalCardItemIds.values.toSet().size)
    }

    /**
     * Transfer an original Treasure item from the online BAG to the bank.
     *
     * Returns the changed BAG slot for later C023 cache synchronization; it
     * deliberately sends no packet and creates no new Treasure RPC or unlock.
     * A false SQL transaction returns null and leaves the online cache intact.
     */
    suspend fun stageBank(
        characterId: Long,
        treasureId: Int,
        expectedVersion: Long,
        bagSlot: Int,
        quantity: Int,
    ): BankedTreasureSlot? {
        if (characterId <= 0 || expectedVersion < 0 || bagSlot !in 0..65535 ||
            quantity <= 0
        ) return null
        val expectedItemId = originalCardItemIds[treasureId] ?: return null

        return CharacterPersistenceGuard.withLock(characterId) {
            val before = inventory.getBagItem(characterId, bagSlot) ?: return@withLock null
            if (before.itemId != expectedItemId || before.quantity < quantity) {
                return@withLock null
            }

            // If cancellation arrives after the SQL debit, online BAG state
            // MUST be updated before releasing the same autosave/logout mutex.
            withContext(NonCancellable) {
                if (!repository.bankCard(
                        characterId, treasureId, expectedVersion, bagSlot, quantity
                    )
                ) return@withContext null

                val after = if (before.quantity == quantity) null
                    else before.copy(quantity = before.quantity - quantity)
                inventory.setBagItemSync(characterId, bagSlot, after)
                BankedTreasureSlot(
                    treasureId = treasureId,
                    bagSlot = bagSlot,
                    originalItemId = expectedItemId,
                    remainingQuantity = after?.quantity ?: 0,
                )
            }
        }
    }
}

/** Source-grounded BAG delta; never a fabricated S123 success packet. */
data class BankedTreasureSlot(
    val treasureId: Int,
    val bagSlot: Int,
    val originalItemId: Int,
    val remainingQuantity: Int,
)
