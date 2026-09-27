package tw.tsm.domain.interfaces.repositories

import tw.tsm.domain.valueobjects.MountData

/**
 * 坐騎資料存取介面
 */
interface MountRepository {
    /** 載入角色坐騎資料 */
    suspend fun findByCharacterId(characterId: Long): MountData?

    /** 儲存角色坐騎資料（upsert 主表 + delete/batchInsert 裝備表） */
    suspend fun save(characterId: Long, data: MountData)
}
