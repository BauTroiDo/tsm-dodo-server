package tw.tsm.domain.interfaces.repositories

/** Durable purchase count for a specific DVT GS_C role counter / period. */
data class MallQuotaRecord(val flag: Int, val period: Int, val count: Int)

interface MallQuotaRepository {
    suspend fun load(charId: Long, flag: Int): MallQuotaRecord?
    suspend fun save(charId: Long, row: MallQuotaRecord)
    suspend fun remove(charId: Long, flag: Int)
}
