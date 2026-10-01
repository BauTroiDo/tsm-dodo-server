package tw.tsm.domain.util

object DvtPercentMath {
    /** Lua: math.floor(result * (100 + percent) * 0.01). */
    fun applyFinalPercentFloor(base: Long, percent: Int): Long {
        return Math.floorDiv(base * (100L + percent.toLong()), 100L)
    }

    /** Lua: base + math.round(base * percent * 0.01). */
    fun addBasePercentRound(base: Int, percent: Int): Int {
        val add = java.lang.Math.round(base * (percent * 0.01)).toInt()
        return base + add
    }
}
