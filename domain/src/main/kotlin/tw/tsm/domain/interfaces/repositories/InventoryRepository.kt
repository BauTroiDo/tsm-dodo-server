package tw.tsm.domain.interfaces.repositories

import tw.tsm.domain.valueobjects.ThingData

/**
 * Interface kho vật phẩm — truy cập lưu trữ bền vững cho túi đồ/trang bị/Tiền Trang
 *
 * storageType tương ứng [tw.tsm.domain.constants.EThings]: BAG=1, BANK=4, EQUIP=8
 */
interface InventoryRepository {

    /**
     * Nạp toàn bộ vật phẩm của nhân vật chỉ định với loại lưu trữ chỉ định
     *
     * @return bảng ánh xạ slot → ThingData
     */
    suspend fun loadItems(characterId: Long, storageType: Int): Map<Int, ThingData>

    /**
     * Lưu vật phẩm của một ô đơn lẻ (xóa ô đó khi thing là null)
     */
    suspend fun saveItem(characterId: Long, storageType: Int, slot: Int, thing: ThingData?)

    /**
     * Lưu nhiều ô trong cùng một giao dịch logic.
     * Implement mặc định giữ tương thích các fake repository trong test; persistence thật override
     * bằng một DB transaction duy nhất.
     */
    suspend fun saveItemsAtomic(characterId: Long, storageType: Int, changes: Map<Int, ThingData?>) {
        for ((slot, thing) in changes) {
            saveItem(characterId, storageType, slot, thing)
        }
    }

    suspend fun loadBattlePassProgress(characterId: Long): tw.tsm.domain.data.BattlePassProgress? = null

    /** Real persistence commits bag, both claim counters and season identity together. */
    suspend fun saveBattlePassClaimAtomic(
        characterId: Long, seasonId: Int, changes: Map<Int, ThingData?>,
        expectedProgress: Map<Int, Int>, nextProgress: Map<Int, Pair<Int, Int>>,
    ) { error("Atomic BattlePass persistence unavailable") }

    /**
     * Lưu hàng loạt toàn bộ vật phẩm của loại lưu trữ chỉ định (xóa sạch rồi ghi vào)
     */
    suspend fun saveAllItems(characterId: Long, storageType: Int, items: Map<Int, ThingData>)

    /**
     * Nạp tiền của nhân vật
     *
     * @return (tiền, tiền đấu giá)
     */
    suspend fun loadMoney(characterId: Long): Pair<Int, Int>

    /**
     * Lưu tiền của nhân vật
     */
    /**
     * Source-only two-character BAG/gold settlement. Deliberately NOT used by
     * TradeSystem yet: both online caches must be published after SQL commit.
     */
    suspend fun commitTwoCharacterTradeAtomic(
        first: TradeAccountSnapshot,
        second: TradeAccountSnapshot,
    ): Boolean = error("Atomic trade integration is not enabled")

    /**
     * STAGED ONLY: one permanent UUID per immutable two-character trade plan.
     * ALREADY_COMMITTED means SQL completed earlier, NOT that RAM is fresh.
     * Never republish stale caches on replay without DB reconciliation.
     */
    suspend fun commitTwoCharacterTradeWithReceiptAtomic(
        operationId: String, first: TradeAccountSnapshot, second: TradeAccountSnapshot,
    ): TradeReceiptCommit = error("Durable item trade is not enabled")

    suspend fun findTwoCharacterTradeReceipt(
        operationId: String, first: TradeAccountSnapshot, second: TradeAccountSnapshot,
    ): TradeReceiptLookup = error("Durable item trade reconciliation is not enabled")

    suspend fun saveMoney(characterId: Long, gold: Int, auctionGold: Int)
}

/** Only COMMITTED_NOW allows the caller to publish both online caches. */
enum class TradeReceiptCommit { COMMITTED_NOW, ALREADY_COMMITTED, REJECTED, TOKEN_CONFLICT }

/** MATCHED proves the immutable plan committed atomically despite lost ACK. */
enum class TradeReceiptLookup { NOT_FOUND, MATCHED, TOKEN_CONFLICT }


/** Full BAG snapshot + paired money values, asserted against locked SQL rows. */
data class TradeAccountSnapshot(
    val characterId: Long,
    val beforeBag: Map<Int, ThingData>,
    val afterBag: Map<Int, ThingData>,
    val beforeGold: Int,
    val afterGold: Int,
    val auctionGold: Int,
)
