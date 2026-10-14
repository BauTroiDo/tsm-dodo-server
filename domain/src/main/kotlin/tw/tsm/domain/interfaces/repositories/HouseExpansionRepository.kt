package tw.tsm.domain.interfaces.repositories

/** Durable paid Gia Viên unlocks; transaction updates house level and real gold together. */
interface HouseExpansionRepository {
    suspend fun loadLevel(characterId: Long): Int

    /** Caller owns the CharacterPersistenceGuard and MoneySystem writer fence. */
    suspend fun purchaseNextLevel(
        characterId: Long, expectedLevel: Int, expectedGold: Int,
        auctionGold: Int, requiredFurnitureKinds: Int, goldCost: Int,
    ): Boolean
}
