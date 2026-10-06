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
    suspend fun saveMoney(characterId: Long, gold: Int, auctionGold: Int)
}
