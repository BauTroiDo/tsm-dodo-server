package tw.tsm.persistence.repositories

import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.update
import tw.tsm.domain.interfaces.repositories.MallQuotaRecord
import tw.tsm.domain.interfaces.repositories.MallQuotaRepository
import tw.tsm.persistence.tables.CharacterMallQuotas

class MallQuotaRepositoryImpl : MallQuotaRepository {
    override suspend fun load(charId: Long, flag: Int): MallQuotaRecord? = suspendTransaction {
        CharacterMallQuotas.selectAll().where {
            (CharacterMallQuotas.characterId eq charId) and (CharacterMallQuotas.flag eq flag)
        }.firstOrNull()?.let { row ->
            MallQuotaRecord(flag, row[CharacterMallQuotas.period], row[CharacterMallQuotas.count])
        }
    }

    override suspend fun save(charId: Long, row: MallQuotaRecord) {
        suspendTransaction {
            val filter = (CharacterMallQuotas.characterId eq charId) and
                (CharacterMallQuotas.flag eq row.flag)
            if (CharacterMallQuotas.selectAll().where { filter }.firstOrNull() == null) {
                CharacterMallQuotas.insert {
                    it[characterId] = charId
                    it[flag] = row.flag
                    it[period] = row.period
                    it[count] = row.count
                }
            } else {
                CharacterMallQuotas.update({ filter }) {
                    it[period] = row.period
                    it[count] = row.count
                }
            }
        }
    }

    override suspend fun remove(charId: Long, flag: Int) {
        suspendTransaction {
            CharacterMallQuotas.deleteWhere {
                (CharacterMallQuotas.characterId eq charId) and
                    (CharacterMallQuotas.flag eq flag)
            }
        }
    }
}
