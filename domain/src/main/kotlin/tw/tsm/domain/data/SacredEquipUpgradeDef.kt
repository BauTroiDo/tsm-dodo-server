package tw.tsm.domain.data

data class SacredEquipUpgradeDef(
    val level: Int,
    val successPercent: Int,
    val currencyKind: Int,
    val spendByThreshold: List<Int>
) {
    init {
        require(level in 1..15)
        require(successPercent in 0..100)
        require(currencyKind in 0..0xFF)
        require(spendByThreshold.size == 6)
        require(spendByThreshold.all { it in 0..0xFFFF })
    }

    fun spendForThreshold(threshold: Int): Int? =
        if (threshold in 1..6) spendByThreshold[threshold - 1] else null
}
