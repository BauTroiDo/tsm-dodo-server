package tw.tsm.domain.valueobjects

data class AstrolabeBonusData(
    val hpx: Int = 0,
    val def: Int = 0,
    val intelligence: Int = 0,
    val spx: Int = 0,
    val agi: Int = 0,
    val atk: Int = 0,
    val criticle: Int = 0
) {
    companion object {
        val EMPTY = AstrolabeBonusData()
    }
}
