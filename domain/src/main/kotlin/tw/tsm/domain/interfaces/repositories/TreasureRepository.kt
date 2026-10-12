package tw.tsm.domain.interfaces.repositories

/**
 * Server-side Treasure ownership. cardCopiesTotal counts card copies banked in
 * Treasure, INCLUDING copies invested into completed levels, as DVT's
 * Treasure.GetCardCount subtracts the cumulative completed level count.
 * It must not include an unconsumed copy also present in inventories.
 */
data class TreasureState(
    val treasureId: Int,
    val cardCopiesTotal: Int,
    val level: Int,
    val star: Int,
    val starExp: Int,
    val version: Long,
) {
    init {
        require(treasureId in 1..114)
        require(cardCopiesTotal >= 0 && level in 0..10 && star in 0..6)
        require(starExp in 0..100 && version >= 0)
    }
}

/** Exact trusted ORIGINAL DVT next-level cost; not a client-submitted quote. */
data class TreasureLevelRequirement(
    val treasureId: Int,
    val targetLevel: Int,
    val copies: Int,
    val materials: Int,
    val gold: Int,
) {
    init {
        require(treasureId in 1..114 && targetLevel in 1..10)
        require(copies > 0 && materials > 0 && gold > 0)
    }
}

interface TreasureRepository {
    suspend fun load(characterId: Long): List<TreasureState>

    /**
     * Future-only transaction primitive, not yet registered or called by C123.
     * Must fail closed if the character has no legitimately banked Treasure row,
     * the client slot is wrong, original source cost is unknown, gold/cards are
     * insufficient, or optimistic version/level are stale. DB writes must all
     * commit or all roll back.
     */
    suspend fun purchaseLevel(
        characterId: Long,
        treasureId: Int,
        expectedLevel: Int,
        expectedVersion: Long,
        materialBagSlot: Int,
    ): Boolean
}
