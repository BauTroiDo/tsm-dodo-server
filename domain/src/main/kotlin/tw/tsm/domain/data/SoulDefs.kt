package tw.tsm.domain.data

/**
 * Static DVT Soul quality row from IOS_Soul_C.dat.
 *
 * convertRate is indexed by star level 0..5 and stores basis points
 * exactly as the client data does (10000 = 100%).
 */
data class SoulDef(
    val quality: Int,
    val startPoint: Int,
    val basicLvLimit: Int,
    val skillGetProb: List<Int>,
    val convertRate: List<Int>,
    val compoundHigherProb: Int,
    val limitBreakthrough: Int,
) {
    fun levelLimit(starLv: Int): Int = basicLvLimit + starLv * 20

    fun convertRateAt(starLv: Int): Int =
        convertRate.getOrElse(starLv) { error("Soul starLv out of range: $starLv") }
}

/** Cost row for one quality at one Soul level. */
data class SoulLevelCostDef(
    val reel: Int,
    val coin: Int,
    val affCoin: Int,
)

/** Static DVT Soul level row from IOS_SoulLevel_C.dat. */
data class SoulLevelDef(
    val level: Int,
    val costs: List<SoulLevelCostDef>,
) {
    fun costForQuality(quality: Int): SoulLevelCostDef =
        costs.getOrElse(quality) { error("Soul quality out of range: $quality") }
}

/** Soul item -> display NPC/avatar mapping from IOS_NpcSoul_C.dat. */
data class NpcSoulDef(
    val itemId: Int,
    val npcId: Int,
    val quality: Int,
)
