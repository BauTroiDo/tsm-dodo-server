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
    /**
     * Pure EXP path used by both player and active follow NPC.
     * Keeping this separate prevents pet EXP calculation from rolling gold/drop RNG a second time.
     */
    fun calculateExp(
        defeatedNpcs: List<Pair<Int, NpcDef>>,
        actorLevel: Int,
        expMultiplier: Double = 1.0
    ): Long {
        var totalExp = 0L
        for ((_, npc) in defeatedNpcs) {
            val levelDiff = npc.level - actorLevel
            val levelModifier = when {
                levelDiff >= 5 -> 1.2
                levelDiff >= -5 -> 1.0
                levelDiff >= -10 -> 0.7
                else -> 0.3
            }
            totalExp += max(1L, (npc.level * 10L * levelModifier * expMultiplier).toLong())
        }
        return totalExp
    }

    fun calculate(
        defeatedNpcs: List<Pair<Int, NpcDef>>,
        playerLevel: Int,
        npcDropTable: Map<Int, List<NpcDropDef>> = emptyMap(),
        expMultiplier: Double = 1.0
    ): BattleReward {
        val totalExp = calculateExp(defeatedNpcs, playerLevel, expMultiplier)
        var totalGold = 0
        val drops = mutableListOf<DropItem>()

        for ((npcId, npc) in defeatedNpcs) {
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