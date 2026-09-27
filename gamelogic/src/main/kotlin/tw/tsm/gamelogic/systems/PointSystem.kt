package tw.tsm.gamelogic.systems

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.interfaces.repositories.CharacterRepository
import java.util.concurrent.ConcurrentHashMap

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
        val arr = pointsMap[characterId] ?: return false
        synchronized(arr) {
            val newValue = arr[0].toLong() + amount
            if (newValue > MAX_POINT) return false
            arr[0] = newValue.toInt()
            return true
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
        val arr = pointsMap[characterId] ?: return false
        synchronized(arr) {
            if (arr[0] < amount) return false
            arr[0] -= amount
            return true
        }
    }

    /**
     * Lưu điểm nhân vật vào DB
     */
    suspend fun savePoints(characterId: Long) {
        val arr = pointsMap[characterId] ?: return
        characterRepo.savePoints(characterId, arr[0], arr[1])
    }

    /**
     * Lưu và hủy nạp (unload) khỏi bộ nhớ
     */
    suspend fun saveAndUnload(characterId: Long) {
        savePoints(characterId)
        pointsMap.remove(characterId)
    }
}