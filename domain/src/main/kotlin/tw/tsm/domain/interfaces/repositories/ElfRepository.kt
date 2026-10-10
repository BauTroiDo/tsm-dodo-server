package tw.tsm.domain.interfaces.repositories

data class ElfProgressRecord(val elfNo: Int, val level: Int, val exp: Int)
data class ElfProfileRecord(val activeNo: Int = 999, val feedTotal: Int = 0, val godMission: Int = 0)
data class ElfSnapshotRecord(val profile: ElfProfileRecord, val progress: List<ElfProgressRecord>)

/** Persistent per-character DVT UIElf state (protocol 107). */
interface ElfRepository {
    suspend fun load(characterId: Long): ElfSnapshotRecord
    suspend fun setActive(characterId: Long, activeNo: Int)
    suspend fun setProgress(characterId: Long, row: ElfProgressRecord, feedTotal: Int)
    /** Atomically adjust remaining battle charges, rejecting values outside 0..65535. */
    suspend fun changeGodMission(characterId: Long, delta: Int): Int?
}
