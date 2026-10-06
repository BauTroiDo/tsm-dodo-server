package tw.tsm.domain.data

/** Lua PassAwardData.New: one free reward followed by two paid rewards. */
data class BattlePassProgress(val seasonId: Int, val freeLevel: Int, val paidLevel: Int)

data class PassReward(val itemId: Int, val quantity: Long)
data class PassAwardDef(val seasonId: Int, val level: Int, val totalExp: Int, val rewards: List<PassReward>)
