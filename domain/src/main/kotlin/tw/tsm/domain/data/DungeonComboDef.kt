package tw.tsm.domain.data

/** Raw DVT DungeonComboData.New. kindValue is preserved, never treated as RNG weight. */
data class DungeonComboReward(val itemId: Int, val quantity: Long)

data class DungeonComboDef(
    val id: Int,
    val kind: Int,
    val kindValue: Long,
    val items: List<DungeonComboReward>,
) {
    /** Lua DungeonComboData:GetReward: fall back through lower rebirth slots to slot zero. */
    fun rewardForTurn(turn: Int): DungeonComboReward? {
        if (turn !in 0..3 || items.size != 4) return null
        for (index in turn downTo 1) {
            if (items[index].itemId != 0) return items[index]
        }
        return items[0].takeIf { it.itemId != 0 }
    }
}
