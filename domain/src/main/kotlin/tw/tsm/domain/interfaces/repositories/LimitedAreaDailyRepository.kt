package tw.tsm.domain.interfaces.repositories

/** One authoritative, durable 120-minute visit per character and Vietnam calendar day. */
data class LimitedAreaDailyRun(
    val dayKey: Int,
    val startedAtMillis: Long,
    val closed: Boolean,
)

interface LimitedAreaDailyRepository {
    suspend fun find(characterId: Long, sceneId: Int): LimitedAreaDailyRun?

    /** Atomically claims the calendar day's sole entry. Legacy used minutes count as today's used entry. */
    suspend fun claim(
        characterId: Long, sceneId: Int, dayKey: Int, nowMillis: Long,
        legacyUsedMinutes: Int, minuteMillis: Long,
    ): Boolean

    /** Only for legitimate in-map reconnects/recovery of a pre-migration session. */
    suspend fun recover(
        characterId: Long, sceneId: Int, dayKey: Int, nowMillis: Long,
        legacyUsedMinutes: Int, minuteMillis: Long,
    ): LimitedAreaDailyRun

    /** Leaving a scene uses up any unspent minutes; disconnect inside the scene does not. */
    suspend fun close(characterId: Long, sceneId: Int)
}
