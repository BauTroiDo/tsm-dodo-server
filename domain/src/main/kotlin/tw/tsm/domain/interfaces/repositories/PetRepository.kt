package tw.tsm.domain.interfaces.repositories

import tw.tsm.domain.valueobjects.FollowNpcData

/**
 * 武將資料存取介面
 */
interface PetRepository {
    /**
     * 查詢角色所有武將（含隨身和馬車）
     */
    suspend fun findByCharacterId(characterId: Long): List<FollowNpcData>

    /**
     * 保存角色所有武將（全量覆蓋）
     */
    suspend fun saveAll(characterId: Long, pets: List<FollowNpcData>)

    /**
     * 刪除指定武將
     */
    suspend fun delete(characterId: Long, storageType: Int, slotIndex: Int)
}
