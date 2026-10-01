package tw.tsm.domain.valueobjects

data class AstrolabeData(
    val natalPoint: Int = 0,
    val starLevels: IntArray = IntArray(7)
) {
    fun copySafe(): AstrolabeData =
        AstrolabeData(natalPoint, starLevels.copyOf())
}
