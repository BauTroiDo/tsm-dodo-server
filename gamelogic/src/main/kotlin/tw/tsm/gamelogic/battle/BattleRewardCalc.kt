package tw.tsm.gamelogic.battle

import tw.tsm.domain.data.NpcDef
import tw.tsm.domain.data.NpcDropDef
import kotlin.math.max
import kotlin.random.Random

/**
 * Trình tính toán phần thưởng chiến đấu
 *
 * Tính toán kinh nghiệm, tiền và vật phẩm rơi ra dựa trên quái vật đã bị đánh bại.
 */
object BattleRewardCalc {

    /**
     * Tính toán phần thưởng chiến đấu PvE
     *
     * @param defeatedNpcs Các NPC đã bị đánh bại (npcId + NpcDef)
     * @param playerLevel Cấp độ người chơi
     * @param npcDropTable Bảng rơi đồ NPC (npcId → danh sách vật phẩm rơi)
     * @param expMultiplier Hệ số nhân kinh nghiệm (Bùa Phúc Thần, v.v.)
     * @return Kết quả phần thưởng
     */
    fun calculate(
        defeatedNpcs: List<Pair<Int, NpcDef>>,
        playerLevel: Int,
        npcDropTable: Map<Int, List<NpcDropDef>> = emptyMap(),
        expMultiplier: Double = 1.0
    ): BattleReward {
        var totalExp = 0L
        var totalGold = 0
        val drops = mutableListOf<DropItem>()

        for ((npcId, npc) in defeatedNpcs) {
            // Điểm kinh nghiệm = Cấp độ quái vật × Hệ số cơ bản × Điều chỉnh chênh lệch cấp độ
            val levelDiff = npc.level - playerLevel
            val levelModifier = when {
                levelDiff >= 5 -> 1.2    // Quái vật cao hơn 5 cấp trở lên, có thưởng thêm
                levelDiff >= -5 -> 1.0   // Chênh lệch cấp độ trong khoảng ±5, bình thường
                levelDiff >= -10 -> 0.7  // Người chơi cao hơn 5~10 cấp, bị giảm
                else -> 0.3              // Người chơi cao hơn 10 cấp trở lên, bị giảm mạnh
            }

            val baseExp = max(1L, (npc.level * 10L * levelModifier * expMultiplier).toLong())
            totalExp += baseExp

            // Tiền vàng = Cấp độ quái vật × 2~5
            totalGold += npc.level * Random.nextInt(2, 6)

            // Vật phẩm rơi ra — Truy vấn bảng rơi đồ npc_drops.json
            val dropDefs = npcDropTable[npcId] ?: continue
            for (drop in dropDefs) {
                if (Random.nextInt(100) < drop.rate) {
                    val qty = if (drop.minQty >= drop.maxQty) drop.minQty
                    else Random.nextInt(drop.minQty, drop.maxQty + 1)
                    drops.add(DropItem(drop.itemId, qty))
                }
            }
        }

        return BattleReward(
            exp = totalExp,
            gold = totalGold,
            drops = drops
        )
    }
}

/**
 * Phần thưởng chiến đấu
 */
data class BattleReward(
    val exp: Long,
    val gold: Int,
    val drops: List<DropItem>
)

/**
 * Vật phẩm rơi ra
 */
data class DropItem(
    val itemId: Int,
    val quantity: Int
)