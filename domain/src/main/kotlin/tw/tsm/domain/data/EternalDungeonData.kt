package tw.tsm.domain.data

data class EternalDungeonLayerDef(
    val id: Int,
    val effects: List<Int>,
    val compare: Int,
    val needs: List<Int>,
)

data class EternalDungeonPrizeEntry(
    val itemId: Int,
    val count: Int,
)

data class EternalDungeonPrizeDef(
    val id: Int,
    val prizes: List<EternalDungeonPrizeEntry>,
)
