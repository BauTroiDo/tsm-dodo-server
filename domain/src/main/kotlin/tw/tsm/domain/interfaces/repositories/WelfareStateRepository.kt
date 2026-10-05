package tw.tsm.domain.interfaces.repositories

interface WelfareStateRepository {
    suspend fun getLastLoginClaimDay(characterId: Long): Int
    suspend fun setLastLoginClaimDay(characterId: Long, dayKey: Int)
}
