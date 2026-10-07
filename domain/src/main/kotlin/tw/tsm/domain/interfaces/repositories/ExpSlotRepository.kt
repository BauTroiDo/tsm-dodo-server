package tw.tsm.domain.interfaces.repositories

/** Persistent global state for DVT warrior EXP slot (mainKind 76). */
interface ExpSlotRepository {
    suspend fun loadJackpotExp(): Long
    suspend fun saveJackpotExp(value: Long)
}
