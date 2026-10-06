package tw.tsm.domain.data

data class PokeCandidate(val id: Int, val kind: Int, val itemId: Int, val count: Int)

data class PokeAward(
    val id: Int,
    val kind: Int,
    val itemId: Int,
    val count: Int,
    val index: Int,
    val prWeight: Int,
)

data class PokeStateData(
    val coin: Int = 0,
    val opened: Map<Int, Int> = emptyMap(),
)
