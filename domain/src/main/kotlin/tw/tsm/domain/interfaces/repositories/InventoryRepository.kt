package tw.tsm.domain.interfaces.repositories

import tw.tsm.domain.valueobjects.ThingData

/**
 * 物品倉儲介面 — 背包/裝備/錢莊的持久化存取
 *
 * storageType 對應 [tw.tsm.domain.constants.EThings]：BAG=1, BANK=4, EQUIP=8
 */
interface InventoryRepository {

    /**
     * 載入指定角色、指定儲存類型的所有物品
     *
     * @return slot → ThingData 的對應表
     */
    suspend fun loadItems(characterId: Long, storageType: Int): Map<Int, ThingData>

    /**
     * 儲存單一格位的物品（thing 為 null 時刪除該格）
     */
    suspend fun saveItem(characterId: Long, storageType: Int, slot: Int, thing: ThingData?)

    /**
     * 批次儲存指定儲存類型的所有物品（先清空再寫入）
     */
    suspend fun saveAllItems(characterId: Long, storageType: Int, items: Map<Int, ThingData>)

    /**
     * 載入角色金錢
     *
     * @return (金錢, 拍賣金錢)
     */
    suspend fun loadMoney(characterId: Long): Pair<Int, Int>

    /**
     * 儲存角色金錢
     */
    suspend fun saveMoney(characterId: Long, gold: Int, auctionGold: Int)
}
