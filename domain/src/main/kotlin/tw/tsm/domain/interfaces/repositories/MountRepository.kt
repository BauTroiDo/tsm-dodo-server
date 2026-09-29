package tw.tsm.domain.interfaces.repositories

import tw.tsm.domain.valueobjects.MountData

/**
 * Giao diện truy xuất dữ liệu vật cưỡi
 */
interface MountRepository {
    /** Tải dữ liệu vật cưỡi của nhân vật*/
    suspend fun findByCharacterId(characterId: Long): MountData?

    /** Lưu dữ liệu vật cưỡi của nhân vật (upsert bảng chính + delete/batchInsert bảng trang bị)*/
    suspend fun save(characterId: Long, data: MountData)
}
