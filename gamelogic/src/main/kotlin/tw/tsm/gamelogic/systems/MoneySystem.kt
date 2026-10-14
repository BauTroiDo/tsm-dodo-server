package tw.tsm.gamelogic.systems

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.constants.ItemConstants
import tw.tsm.domain.interfaces.repositories.InventoryRepository
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Job

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
     * SYNC writer fence. Like CharacterPersistenceGuard it uses 256 bounded
     * character stripes; acquiring by sorted stripe prevents two-player
     * deadlocks. All existing addGold/subtractGold call sites keep their
     * original non-suspending API and never drop legitimate combat rewards.
     *
     * Trade settlement is still staged/unwired: holding these permits around
     * suspending SQL on a live Netty event loop could create unacceptable
     * blocking. Do not register TradeCacheSettlementCoordinator until its
     * completion protocol and caller dispatch are validated.
     */
    private val goldMutationFences = Array(256) { Semaphore(1, true) }

    private fun goldStripe(characterId: Long): Int =
        (characterId xor (characterId ushr 32)).toInt() and 255

    private inline fun <T> withGoldMutationFence(characterId: Long, block: () -> T): T {
        val permit = goldMutationFences[goldStripe(characterId)]
        permit.acquireUninterruptibly()
        try {
            return block()
        } finally {
            permit.release()
        }
    }

    /**
     * Unwired two-player SQL window: synchronous gold rewards/payments wait
     * until both committed SQL balances are safely published to RAM.
     * NonCancellable IO permit acquisition cannot strand a semaphore when
     * the caller is cancelled while switching back to its original dispatcher.
     */
    suspend fun <T> withTradeGoldMutationFence(
        firstId: Long,
        secondId: Long,
        action: suspend () -> T,
    ): T {
        require(firstId > 0 && secondId > 0 && firstId != secondId)
        val low = goldMutationFences[minOf(goldStripe(firstId), goldStripe(secondId))]
        val high = goldMutationFences[maxOf(goldStripe(firstId), goldStripe(secondId))]
        val callerJob = currentCoroutineContext()[Job]
        // Keep acquisition, action, and release in one non-cancellable
        // dispatcher context. Re-dispatching to a cancelled caller after
        // acquiring permits would otherwise leak both semaphores.
        return withContext(NonCancellable + Dispatchers.IO) {
            low.acquire()
            if (high !== low) {
                try { high.acquire() } catch (ex: Throwable) {
                    low.release()
                    throw ex
                }
            }
            try {
                // A cancelled waiter must never start a fresh SQL trade after
                // the permits finally become available.
                callerJob?.ensureActive()
                action()
            } finally {
                if (high !== low) high.release()
                low.release()
            }
        }
    }


    /**
     * Tải tiền của nhân vật từ DB
     */
    /**
     * Paid Gia Viên expansion: all synchronous add/subtractGold writers share
     * the same semaphore, while CharacterPersistenceGuard fences autosaves,
     * logout, and furniture redemption. The callback commits gold + level in
     * ONE transaction; RAM is only published after DB success.
     */
    suspend fun purchaseHouseExpansionGold(
        characterId: Long,
        cost: Int,
        commit: suspend (gold: Int, auctionGold: Int) -> Boolean,
    ): Boolean {
        if (characterId <= 0 || cost <= 0) return false
        val requestedBy = currentCoroutineContext()[Job]
        return withContext(NonCancellable + Dispatchers.IO) {
            CharacterPersistenceGuard.withLock(characterId) {
                val fence = goldMutationFences[goldStripe(characterId)]
                fence.acquire()
                try {
                    requestedBy?.ensureActive()
                    val arr = moneyMap[characterId] ?: return@withLock false
                    val (gold, auctionGold) = synchronized(arr) {
                        Pair(arr[0].toInt(), arr[1].toInt())
                    }
                    if (gold < cost || auctionGold < 0) return@withLock false
                    // Never write a RAM snapshot outside the paid SQL transaction.
                    // If DB and RAM balances disagree, the repository rejects
                    // the upgrade: no currency or house-level mutation occurs.
                    // This fails closed instead of overwriting another committed
                    // DB change before a rejected purchase.
                    if (!commit(gold, auctionGold)) return@withLock false
                    synchronized(arr) { arr[0] = (gold - cost).toLong() }
                    true
                } finally {
                    fence.release()
                }
            }
        }
    }

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

    /** Staged trade only: distinguish an unloaded cache from a legitimate zero balance. */
    fun getLoadedMoney(characterId: Long): Pair<Int, Int>? =
        moneyMap[characterId]?.let { arr ->
            synchronized(arr) { Pair(arr[0].toInt(), arr[1].toInt()) }
        }

    /**
     * STAGED, unwired TradeCacheSettlementCoordinator post-DB publication.
     * Caller must already own both per-character coroutine locks. All
     * unrelated synchronous gold mutators must join a common fence before
     * this can safely be activated in C025.
     */
    fun publishCommittedTradeGoldSync(characterId: Long, afterGold: Int, auctionGold: Int) {
        require(afterGold in 0..ItemConstants.MAX_GOLD && auctionGold >= 0)
        val arr = moneyMap[characterId] ?: error("Trade MONEY cache unloaded: $characterId")
        synchronized(arr) {
            arr[0] = afterGold.toLong()
            arr[1] = auctionGold.toLong()
        }
    }

    /** Lấy số tiền vàng */
    fun getGold(characterId: Long): Int =
        moneyMap[characterId]?.let { synchronized(it) { it[0].toInt() } } ?: 0

    /** Lấy số tiền đấu giá */
    fun getAuctionGold(characterId: Long): Int =
        moneyMap[characterId]?.let { synchronized(it) { it[1].toInt() } } ?: 0

    /**
     * Thêm tiền vàng (synchronized bảo vệ tính nguyên tử của quá trình đọc-kiểm tra-ghi)
     *
     * @return true=Thành công, false=Tràn (Vượt quá giới hạn tối đa)
     */
    fun addGold(characterId: Long, amount: Int): Boolean {
        if (amount <= 0) return false
        return withGoldMutationFence(characterId) {
            val arr = moneyMap[characterId] ?: return@withGoldMutationFence false
            synchronized(arr) {
            val newGold = arr[0] + amount
            if (newGold < 0 || newGold > ItemConstants.MAX_GOLD) return@withGoldMutationFence false
            arr[0] = newGold
            true
            }
        }
    }

    /**
     * Trừ tiền vàng (synchronized bảo vệ tính nguyên tử của quá trình đọc-kiểm tra-ghi)
     *
     * @return true=Thành công, false=Số dư không đủ
     */
    fun subtractGold(characterId: Long, amount: Int): Boolean {
        if (amount <= 0) return false
        return withGoldMutationFence(characterId) {
            val arr = moneyMap[characterId] ?: return@withGoldMutationFence false
            synchronized(arr) {
            if (arr[0] < amount) return@withGoldMutationFence false
            arr[0] -= amount
            true
            }
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