package tw.tsm.persistence.repositories

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.upsert
import tw.tsm.domain.interfaces.repositories.WelfareStateRepository
import tw.tsm.persistence.tables.CharacterWelfareState

class WelfareStateRepositoryImpl : WelfareStateRepository {
    override suspend fun getLastLoginClaimDay(characterId: Long): Int =
        suspendTransaction {
            CharacterWelfareState.selectAll()
                .where { CharacterWelfareState.charId eq characterId }
                .singleOrNull()
                ?.get(CharacterWelfareState.lastLoginClaimDay)
                ?: 0
        }

    override suspend fun setLastLoginClaimDay(characterId: Long, dayKey: Int) {
        suspendTransaction {
            CharacterWelfareState.upsert {
                it[charId] = characterId
                it[lastLoginClaimDay] = dayKey
            }
        }
    }
}
