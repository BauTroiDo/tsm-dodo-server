package tw.tsm.persistence.repositories

import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.update
import tw.tsm.domain.interfaces.repositories.ElfRepository
import tw.tsm.domain.interfaces.repositories.ElfProfileRecord
import tw.tsm.domain.interfaces.repositories.ElfProgressRecord
import tw.tsm.domain.interfaces.repositories.ElfSnapshotRecord
import tw.tsm.persistence.tables.CharacterElfProfiles
import tw.tsm.persistence.tables.CharacterElfProgress

class ElfRepositoryImpl : ElfRepository {
    override suspend fun load(characterId: Long): ElfSnapshotRecord = suspendTransaction {
        val p = CharacterElfProfiles.selectAll()
            .where { CharacterElfProfiles.characterId eq characterId }.firstOrNull()
        val profile = if (p == null) ElfProfileRecord() else
            ElfProfileRecord(p[CharacterElfProfiles.activeNo], p[CharacterElfProfiles.feedTotal])
        val elves = CharacterElfProgress.selectAll()
            .where { CharacterElfProgress.characterId eq characterId }
            .map { ElfProgressRecord(it[CharacterElfProgress.elfNo], it[CharacterElfProgress.level], it[CharacterElfProgress.exp]) }
        ElfSnapshotRecord(profile, elves)
    }

    override suspend fun setActive(characterId: Long, activeNo: Int) {
        suspendTransaction {
            val old = CharacterElfProfiles.selectAll()
                .where { CharacterElfProfiles.characterId eq characterId }.firstOrNull()
            if (old == null) {
                CharacterElfProfiles.insert {
                    it[CharacterElfProfiles.characterId] = characterId
                    it[CharacterElfProfiles.activeNo] = activeNo
                }
            } else {
                CharacterElfProfiles.update({ CharacterElfProfiles.characterId eq characterId }) {
                    it[CharacterElfProfiles.activeNo] = activeNo
                }
            }
        }
    }

    override suspend fun setProgress(characterId: Long, row: ElfProgressRecord, feedTotal: Int) {
        suspendTransaction {
            val old = CharacterElfProfiles.selectAll()
                .where { CharacterElfProfiles.characterId eq characterId }.firstOrNull()
            if (old == null) {
                CharacterElfProfiles.insert {
                    it[CharacterElfProfiles.characterId] = characterId
                    it[CharacterElfProfiles.feedTotal] = feedTotal
                }
            } else {
                CharacterElfProfiles.update({ CharacterElfProfiles.characterId eq characterId }) {
                    it[CharacterElfProfiles.feedTotal] = feedTotal
                }
            }
            val previous = CharacterElfProgress.selectAll().where {
                (CharacterElfProgress.characterId eq characterId) and (CharacterElfProgress.elfNo eq row.elfNo)
            }.firstOrNull()
            if (previous == null) {
                CharacterElfProgress.insert {
                    it[CharacterElfProgress.characterId] = characterId
                    it[CharacterElfProgress.elfNo] = row.elfNo
                    it[CharacterElfProgress.level] = row.level
                    it[CharacterElfProgress.exp] = row.exp
                }
            } else {
                CharacterElfProgress.update({
                    (CharacterElfProgress.characterId eq characterId) and (CharacterElfProgress.elfNo eq row.elfNo)
                }) {
                    it[CharacterElfProgress.level] = row.level
                    it[CharacterElfProgress.exp] = row.exp
                }
            }
        }
    }
}
