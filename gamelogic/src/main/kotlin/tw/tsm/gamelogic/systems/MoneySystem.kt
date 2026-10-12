package tw.tsm.gamelogic.systems

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.constants.ItemConstants
import tw.tsm.domain.interfaces.repositories.InventoryRepository
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

/**
 * Hệ thống quản lý tiền tệ
 *
 * Duy trì trạng thái tiền của mỗi nhân vật đang online trong bộ nhớ.
 * Tương ứng với `Role.lua` của playerGold / playerSaleGold.
 *
 * @param inventoryRepo Truy cập lưu trữ liên tục (Dùng chung loadMoney/saveMoney của InventoryRepository)
 */
class MoneySystem(
    private val inventoryRepo: InventoryRepository
) {
    /** characterId → (gold, auctionGold) */
    private val moneyMap = ConcurrentHashMap<Long, LongArray>()

    /**
     * Tải tiền của nhân vật từ DB
     */
    suspend fun loadMoney(characterId: Long) {
        CharacterPersistenceGuard.withLock(characterId) {
        val (gold, auctionGold) = inventoryRepo.loadMoney(characterId)
        moneyMap[characterId] = longArrayOf(gold.toLong(), auctionGold.toLong())
        logger.info { "Tải tiền nhân vật: characterId=$characterId gold=$gold auctionGold=$auctionGold" }
        }
    }

    /**
     * Lấy lượng tiền của nhân vật
     *
     * @return (gold, auctionGold)
     */
    fun getMoney(characterId: Long): Pair<Int, Int> {
        val arr = moneyMap[characterId] ?: return Pair(0, 0)
        return synchronized(arr) { Pair(arr[0].toInt(), arr[1].toInt()) }
    }

    /** Lấy số tiền vàng */
    fun getGold(characterId: Long): Int =
        moneyMap[characterId]?.get(0)?.toInt() ?: 0

    /** Lấy số tiền đấu giá */
    fun getAuctionGold(characterId: Long): Int =
        moneyMap[characterId]?.get(1)?.toInt() ?: 0

    /**
     * Thêm tiền vàng (synchronized bảo vệ tính nguyên tử của quá trình đọc-kiểm tra-ghi)
     *
     * @return true=Thành công, false=Tràn (Vượt quá giới hạn tối đa)
     */
    fun addGold(characterId: Long, amount: Int): Boolean {
        if (amount <= 0) return false
        val arr = moneyMap[characterId] ?: return false
        synchronized(arr) {
            val newGold = arr[0] + amount
            if (newGold < 0 || newGold > ItemConstants.MAX_GOLD) return false
            arr[0] = newGold
            return true
        }
    }

    /**
     * Trừ tiền vàng (synchronized bảo vệ tính nguyên tử của quá trình đọc-kiểm tra-ghi)
     *
     * @return true=Thành công, false=Số dư không đủ
     */
    fun subtractGold(characterId: Long, amount: Int): Boolean {
        if (amount <= 0) return false
        val arr = moneyMap[characterId] ?: return false
        synchronized(arr) {
            if (arr[0] < amount) return false
            arr[0] -= amount
            return true
        }
    }

    /**
     * Lưu tiền của nhân vật vào DB
     */
    suspend fun saveMoney(characterId: Long) {
        CharacterPersistenceGuard.withLock(characterId) {
            saveMoneyUnfenced(characterId)
        }
    }

    private suspend fun saveMoneyUnfenced(characterId: Long) {
        val arr = moneyMap[characterId] ?: return
        // Gold and auctionGold are mutated under this same array monitor;
        // persist a coherent pair rather than two unprotected field reads.
        val snapshot = synchronized(arr) { Pair(arr[0].toInt(), arr[1].toInt()) }
        inventoryRepo.saveMoney(characterId, snapshot.first, snapshot.second)
    }

    /**
     * Lưu và gỡ khỏi bộ nhớ
     */
    suspend fun saveAndUnload(characterId: Long) {
        CharacterPersistenceGuard.withLock(characterId) {
            saveMoneyUnfenced(characterId)
            moneyMap.remove(characterId)
        }
    }
}