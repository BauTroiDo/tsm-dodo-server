package tw.tsm.persistence.repositories

import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.upsert
import tw.tsm.domain.interfaces.repositories.BitFlagRepository
import tw.tsm.persistence.tables.CharacterBitFlags

/**
 * BitFlagRepository 的 Exposed ORM 實作
 *
 * 使用 character_bit_flags 表存儲永標狀態。
 */
class BitFlagRepositoryImpl : BitFlagRepository {

    override suspend fun getSetFlagIds(charId: Long): Set<Int> =
        suspendTransaction {
            CharacterBitFlags.selectAll()
                .where { CharacterBitFlags.charId eq charId }
                .map { it[CharacterBitFlags.flagId] }
                .toSet()
        }

    override suspend fun setFlag(charId: Long, flagId: Int) =
        suspendTransaction {
            CharacterBitFlags.upsert {
                it[CharacterBitFlags.charId] = charId
                it[CharacterBitFlags.flagId] = flagId
            }
            Unit
        }

    override suspend fun clearFlag(charId: Long, flagId: Int) =
        suspendTransaction {
            CharacterBitFlags.deleteWhere {
                (CharacterBitFlags.charId eq charId) and
                (CharacterBitFlags.flagId eq flagId)
            }
            Unit
        }
}
