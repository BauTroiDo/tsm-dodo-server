package tw.tsm.domain.interfaces.repositories

interface WelfareStateRepository {
    suspend fun getLastLoginClaimDay(characterId: Long): Int
    suspend fun setLastLoginClaimDay(characterId: Long, dayKey: Int)
    suspend fun recordLoginDay(characterId: Long, dayKey: Int): Int =
        error("Welfare login day persistence missing")
    suspend fun getLoginDayCount(characterId: Long): Int =
        error("Welfare login day persistence missing")
}
