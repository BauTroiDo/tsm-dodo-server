package tw.tsm.domain.data

data class CollectStyleDef(
    val id: Int,
    val itemIds: List<Int>,
    val itemScores: List<Int>,
    val nft: Int,
)

data class CollectStyleValueDef(
    val score: Int,
    val attributes: List<Pair<Int, Int>>,
    val nft: Int,
)

data class CollectCardAttributeDef(
    val kind: Int,
    val value: Int,
    val grow: Int,
)

data class CollectCardDef(
    val id: Int,
    val index: Int,
    val cardIds: List<Int>,
    val maxLevel: Int,
    val levelUpBase: Int,
    val levelUpMultiplier: Int,
    val values: List<CollectCardAttributeDef>,
)

data class CollectCardSortDef(
    val id: Int,
    val warriorId: Int,
    val roleCount: Int,
    val getFlag: Int,
)

/** DVT IOS_CollectNFT_C.dat row. kind=1 card, kind=2 mount. */
data class CollectNftDef(
    val kind: Int,
    val flag: Int,
    /** For mount this is NPC ID; for card DVT uses this as the collection display key. */
    val warriorId: Int,
    /** Physical item consumed/returned when putting into/taking out of NFT collection. */
    val getId: Int,
    val canGet: Int,
    val shineValue: Int,
)

data class CollectionCardState(
    val cardId: Int,
    val level: Int,
    val equipSlot: Int,
)
