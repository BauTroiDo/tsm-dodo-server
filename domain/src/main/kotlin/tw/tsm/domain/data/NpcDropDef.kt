package tw.tsm.domain.data

/**
 * Định nghĩa rơi đồ của NPC
 *
 * Định nghĩa các vật phẩm mà một NPC có thể rơi và xác suất tương ứng.
 * Nguồn: phía server `gamedata/npc_drops.json`
 *
 * @param itemId ID vật phẩm rơi
 * @param rate xác suất rơi (0-100, phần trăm)
 * @param minQty số lượng rơi nhỏ nhất
 * @param maxQty số lượng rơi lớn nhất
 */
data class NpcDropDef(
    val itemId: Int,
    val rate: Int,
    val minQty: Int = 1,
    val maxQty: Int = 1
)
