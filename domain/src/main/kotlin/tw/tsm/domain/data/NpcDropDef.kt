package tw.tsm.domain.data

/**
 * NPC 掉落定義
 *
 * 定義單個 NPC 可能掉落的物品及其機率。
 * 來源：伺服器端 `gamedata/npc_drops.json`
 *
 * @param itemId 掉落物品 ID
 * @param rate 掉落機率（0-100，百分比）
 * @param minQty 最小掉落數量
 * @param maxQty 最大掉落數量
 */
data class NpcDropDef(
    val itemId: Int,
    val rate: Int,
    val minQty: Int = 1,
    val maxQty: Int = 1
)
