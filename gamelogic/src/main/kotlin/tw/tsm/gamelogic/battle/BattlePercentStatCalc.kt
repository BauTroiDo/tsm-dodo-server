package tw.tsm.gamelogic.battle

/** DVT UIStatusDetail percentage contribution for base ATK/DEF. */
object BattlePercentStatCalc {
    /**
     * DVT math.round(x): x%1 < 0.5 => floor, otherwise ceil.
     * For the non-negative combat stats used here, Math.round matches that contract.
     */
    fun addBasePercent(base: Int, percent: Int): Int {
        val percentAdd = java.lang.Math.round(base * (percent * 0.01)).toInt()
        return base + percentAdd
    }
}
