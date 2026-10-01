package tw.tsm.domain.interfaces.repositories

import tw.tsm.domain.data.DispatchDailyUsage
import tw.tsm.domain.data.DispatchSaveData

/**
 * Giao diện truy xuất dữ liệu điều phái
 */
interface DispatchRepository {

    /** Truy vấn toàn bộ lượt điều phái đang tiến hành của nhân vật*/
    suspend fun findByCharacterId(characterId: Long): List<DispatchSaveData>

    /** Thêm một bản ghi điều phái*/
    suspend fun insert(characterId: Long, data: DispatchSaveData)

    /** Xoá bản ghi điều phái của thanh tiến trình chỉ định*/
    suspend fun deleteBySlot(characterId: Long, slotIndex: Int)

    /** Trạng thái quota Điều phái của ngày hiện tại/gần nhất. */
    suspend fun getDailyUsage(characterId: Long): DispatchDailyUsage?

    /** Ghi đè quota Điều phái theo ngày. */
    suspend fun saveDailyUsage(characterId: Long, usage: DispatchDailyUsage)
}
