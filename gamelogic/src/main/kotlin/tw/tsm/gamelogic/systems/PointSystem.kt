package tw.tsm.gamelogic.systems

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.interfaces.repositories.CharacterRepository
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

private val logger = KotlinLogging.logger {}

/** Giới hạn điểm Kim Nguyên Bảo (bảo vệ chống tràn) */
private const val MAX_POINT = 999_999_999

/**
 * Hệ thống điểm Kim Nguyên Bảo — Tương ứng với Client Role.SetPoint / Role.SetLockPoint
 *
 * Duy trì điểm Kim Nguyên Bảo (KNB) của mỗi nhân vật trực tuyến trong bộ nhớ, kết hợp với bảng Characters để lưu trữ cố định (persistence).
 * - Điểm thực (pointReal) = KNB: Nhận được qua nạp thẻ, dùng để tiêu phí / mua sắm trong Cash shop
 * - Điểm ảo (pointVirtual) = KNB khóa: Bản Đài Loan chưa mở tính năng tiêu phí (ECurrency.Silver ghi chú "chưa ra"), hiện chỉ giữ lại trường dữ liệu
 *
 * Gói tin (Packet) làm mới số dư: S:035-005 <Hiển thị điểm còn lại> +Điểm thực(4) +Điểm ảo(4)
 *
 * @param characterRepo Truy cập lưu trữ (Characters.point_real / point_virtual)
 */
class PointSystem(
    private val characterRepo: CharacterRepository
) {
    /** characterId → [Điểm thực, Điểm ảo] */
    private val pointsMap = ConcurrentHashMap<Long, IntArray>()
    private val writerFences = Array(256) { Semaphore(1,true) }
    private fun fence(id: Long) = writerFences[(id xor (id ushr 32)).toInt() and 255]
    private inline fun <T> withPointWriter(id: Long, operation: () -> T): T {
        val semaphore=fence(id)
        semaphore.acquireUninterruptibly()
        try { return operation() } finally { semaphore.release() }
    }
    /**
     * Coordinate SQL transaction + point RAM publication with autosave, logout,
     * and every synchronous point add/debit. No await after SQL commit outside fence.
     */
    suspend fun <T> withOfflineExpClaimFence(
        id: Long, action: suspend (Int) -> Pair<T,Int>?
    ): T? = withContext(NonCancellable + Dispatchers.IO) {
        CharacterPersistenceGuard.withLock(id) {
            val semaphore=fence(id)
            semaphore.acquire()
            try {
                val arr=pointsMap[id] ?: return@withLock null
                val old = synchronized(arr) { arr[0] }
                val claim=action(old) ?: return@withLock null
                synchronized(arr) { arr[0]=claim.second }
                claim.first
            } finally { semaphore.release() }
        }
    }

    /**
     * Tải điểm nhân vật từ DB
     */
    suspend fun loadPoints(characterId: Long) {
        val (real, virtual) = characterRepo.loadPoints(characterId)
        pointsMap[characterId] = intArrayOf(real, virtual)
        logger.debug { "Tải điểm nhân vật: characterId=$characterId real=$real virtual=$virtual" }
    }

    /**
     * Lấy điểm nhân vật
     *
     * @return (Điểm thực, Điểm ảo)
     */
    fun getPoints(characterId: Long): Pair<Int, Int> {
        val arr = pointsMap[characterId] ?: return Pair(0, 0)
        return Pair(arr[0], arr[1])
    }

    /** Lấy điểm thực Kim Nguyên Bảo */
    fun getRealPoint(characterId: Long): Int =
        pointsMap[characterId]?.get(0) ?: 0

    /**
     * Thêm điểm thực Kim Nguyên Bảo (sử dụng synchronized để bảo vệ tính nguyên tử đọc-kiểm tra-ghi)
     *
     * @return true=Thành công, false=Tràn (vượt quá giới hạn)
     */
    fun addRealPoint(characterId: Long, amount: Int): Boolean {
        if (amount <= 0) return false
        return withPointWriter(characterId) {
            val arr=pointsMap[characterId] ?: return@withPointWriter false
            synchronized(arr) {
                val n=arr[0].toLong()+amount
                if (n > MAX_POINT) return@synchronized false
                arr[0]=n.toInt()
                true
            }
        }
    }

    /**
     * Thêm điểm ảo Kim Nguyên Bảo (sử dụng synchronized để bảo vệ tính nguyên tử đọc-kiểm tra-ghi)
     *
     * @return true=Thành công, false=Tràn (vượt quá giới hạn)
     */
    fun addVirtualPoint(characterId: Long, amount: Int): Boolean {
        if (amount <= 0) return false
        val arr = pointsMap[characterId] ?: return false
        synchronized(arr) {
            val newValue = arr[1].toLong() + amount
            if (newValue > MAX_POINT) return false
            arr[1] = newValue.toInt()
            return true
        }
    }

    /**
     * Trừ điểm thực Kim Nguyên Bảo (sử dụng synchronized để bảo vệ tính nguyên tử đọc-kiểm tra-ghi)
     *
     * @return true=Thành công, false=Số dư không đủ
     */
    fun subtractRealPoint(characterId: Long, amount: Int): Boolean {
        if (amount <= 0) return false
        return withPointWriter(characterId) {
            val arr=pointsMap[characterId] ?: return@withPointWriter false
            synchronized(arr) {
                if (arr[0]<amount) return@synchronized false
                arr[0]-=amount
                true
            }
        }
    }

    /**
     * Lưu điểm nhân vật vào DB
     */
    suspend fun savePoints(characterId: Long) {
        CharacterPersistenceGuard.withLock(characterId) {
            val semaphore=fence(characterId)
            semaphore.acquire()
            try {
                val arr=pointsMap[characterId] ?: return@withLock
                val snapshot=synchronized(arr) { arr[0] to arr[1] }
                characterRepo.savePoints(characterId,snapshot.first,snapshot.second)
            } finally { semaphore.release() }
        }
    }

    /**
     * Lưu và hủy nạp (unload) khỏi bộ nhớ
     */
    suspend fun saveAndUnload(characterId: Long) {
        savePoints(characterId)
        pointsMap.remove(characterId)
    }
}