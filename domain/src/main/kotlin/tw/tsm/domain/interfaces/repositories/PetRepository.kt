package tw.tsm.domain.interfaces.repositories

import tw.tsm.domain.valueobjects.FollowNpcData

/**
 * Giao diện truy xuất dữ liệu tướng
 */
interface PetRepository {
    /**
     * Truy vấn toàn bộ tướng của nhân vật (gồm cả mang theo và trong xe ngựa)
     */
    suspend fun findByCharacterId(characterId: Long): List<FollowNpcData>

    /**
     * Lưu toàn bộ tướng của nhân vật (ghi đè toàn bộ)
     */
    suspend fun saveAll(characterId: Long, pets: List<FollowNpcData>)

    /**
     * Xóa tướng chỉ định
     */
    suspend fun delete(characterId: Long, storageType: Int, slotIndex: Int)
}
