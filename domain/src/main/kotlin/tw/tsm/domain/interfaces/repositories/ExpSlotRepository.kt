package tw.tsm.domain.interfaces.repositories

/** Persistent global state for DVT warrior EXP slot (mainKind 76). */
interface ExpSlotRepository {
    suspend fun loadJackpotExp(): Long
    suspend fun saveJackpotExp(value: Long)

    /** Commit exact coin debit, pet EXP fields and optional jackpot claim in one DB transaction. */
    suspend fun settleSpin(
        characterId: Long, bagSlot: Int, coin: tw.tsm.domain.valueobjects.ThingData, count: Int,
        before: tw.tsm.domain.valueobjects.FollowNpcData,
        after: tw.tsm.domain.valueobjects.FollowNpcData,
        expectedJackpot: Long?, nextJackpot: Long,
    )
}
