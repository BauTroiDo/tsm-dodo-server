package tw.tsm.domain.interfaces.repositories

interface WelfareStateRepository {
    suspend fun getLastLoginClaimDay(characterId: Long): Int
    suspend fun setLastLoginClaimDay(characterId: Long, dayKey: Int)
    /** Atomically start a new DVT online-welfare day; leaves login claims and other flags intact. */
    suspend fun startOnlineDayIfNeeded(characterId: Long, dayKey: Int): Boolean =
        error("Welfare daily-online persistence missing")
    suspend fun recordLoginDay(characterId: Long, dayKey: Int): Int =
        error("Welfare login day persistence missing")
    suspend fun getLoginDayCount(characterId: Long): Int =
        error("Welfare login day persistence missing")
}
