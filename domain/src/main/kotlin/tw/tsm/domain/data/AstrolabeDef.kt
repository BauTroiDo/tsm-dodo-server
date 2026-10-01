package tw.tsm.domain.data

data class AstrolabeLevelDef(
    val value: Int,
    val needPoint: Int
)

data class AstrolabeDef(
    val kind: Int,
    val levels: List<AstrolabeLevelDef>
) {
    init {
        require(kind in 1..7)
        require(levels.size == 10)
    }

    fun level(level: Int): AstrolabeLevelDef? =
        levels.getOrNull(level - 1)
}
