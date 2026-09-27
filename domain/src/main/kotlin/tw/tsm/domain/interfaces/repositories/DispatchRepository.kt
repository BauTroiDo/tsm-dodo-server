package tw.tsm.domain.interfaces.repositories

import tw.tsm.domain.data.DispatchSaveData

/**
 * 派遣資料存取介面
 */
interface DispatchRepository {

    /** 查詢角色所有進行中的派遣 */
    suspend fun findByCharacterId(characterId: Long): List<DispatchSaveData>

    /** 新增一筆派遣記錄 */
    suspend fun insert(characterId: Long, data: DispatchSaveData)

    /** 刪除指定進度條的派遣記錄 */
    suspend fun deleteBySlot(characterId: Long, slotIndex: Int)
}
