package tw.tsm.domain.data

/** IOS_PokeData_C.dat: one possible Trạc/Poke reward. */
data class PokeRewardDef(
    val id: Int,
    val kind: Int,
    val itemId: Int,
    val count: Int,
)

/** IOS_PokeBonusReward_C.dat. Kept as authoritative static data even though the normal UIPoke does not read it directly. */
data class PokeBonusRewardDef(
    val boardIndex: Int,
    val id: Int,
    val quantPlus: Int,
)

/** Persistent per-character state for UIPoke. */
data class PokeStateData(
    val coin: Int = 0,
    val openedSlots: Map<Int, Int> = emptyMap(), // rewardId -> gridIndex
)
