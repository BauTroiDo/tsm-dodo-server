package tw.tsm.persistence.repositories

import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.update
import tw.tsm.domain.interfaces.repositories.RewardLedgerEntry
import tw.tsm.domain.interfaces.repositories.RewardLedgerRepository
import tw.tsm.persistence.tables.CharacterRewardLedger
import tw.tsm.persistence.tables.CharacterRewardPendingScopes

/**
 * Independent ledger for non-Eve payouts. Integration into reward dispatch is
 * deliberately deferred until stable, persistent run IDs have been established.
 */
class RewardLedgerRepositoryImpl : RewardLedgerRepository {
    private fun validate(characterId: Long, rewardKind: String, sourceKey: String) {
        require(characterId > 0)
        require(rewardKind.length in 1..32 && rewardKind.matches(Regex("[A-Z0-9_]+")))
        require(sourceKey.length in 1..128)
    }

    private fun validateAttempt(attemptToken: String, nowEpochMs: Long) {
        require(attemptToken.length in 1..64)
        require(nowEpochMs > 0)
    }

    private fun key(characterId: Long, rewardKind: String, sourceKey: String) =
        (CharacterRewardLedger.characterId eq characterId) and
            (CharacterRewardLedger.rewardKind eq rewardKind) and
            (CharacterRewardLedger.sourceKey eq sourceKey)

    override suspend fun reserve(
        characterId: Long, rewardKind: String, sourceKey: String,
        attemptToken: String, nowEpochMs: Long,
    ): Boolean {
        validate(characterId, rewardKind, sourceKey)
        validateAttempt(attemptToken, nowEpochMs)
        return suspendTransaction {
            // First reserve this character+kind *regardless of sourceKey*.
            // A crash with an uncommitted run must not be bypassed by creating
            // another run with a new UUID or dungeon start timestamp.
            val scopeInserted = CharacterRewardPendingScopes.insertIgnore {
                it[CharacterRewardPendingScopes.characterId] = characterId
                it[CharacterRewardPendingScopes.rewardKind] = rewardKind
                it[CharacterRewardPendingScopes.sourceKey] = sourceKey
                it[CharacterRewardPendingScopes.attemptToken] = attemptToken
            }.insertedCount == 1
            if (!scopeInserted) return@suspendTransaction false

            // The source-specific receipt is permanent, so the same source
            // cannot pay again after a lost commit acknowledgement.
            val ledgerInserted = CharacterRewardLedger.insertIgnore {
                it[CharacterRewardLedger.characterId] = characterId
                it[CharacterRewardLedger.rewardKind] = rewardKind
                it[CharacterRewardLedger.sourceKey] = sourceKey
                it[CharacterRewardLedger.attemptToken] = attemptToken
                it[CharacterRewardLedger.reservedAtEpochMs] = nowEpochMs
                it[CharacterRewardLedger.settledAtEpochMs] = null
            }.insertedCount == 1

            if (!ledgerInserted) {
                // We own the freshly inserted scope row; release it within
                // this same transaction. Do not remove an older person's row.
                val deleted = CharacterRewardPendingScopes.deleteWhere {
                    (CharacterRewardPendingScopes.characterId eq characterId) and
                        (CharacterRewardPendingScopes.rewardKind eq rewardKind) and
                        (CharacterRewardPendingScopes.sourceKey eq sourceKey) and
                        (CharacterRewardPendingScopes.attemptToken eq attemptToken)
                }
                check(deleted == 1) { "Could not release duplicate reward scope" }
                return@suspendTransaction false
            }
            true
        }
    }

    override suspend fun settle(
        characterId: Long, rewardKind: String, sourceKey: String,
        attemptToken: String, nowEpochMs: Long,
    ): Boolean {
        validate(characterId, rewardKind, sourceKey)
        validateAttempt(attemptToken, nowEpochMs)
        return suspendTransaction {
            // Reserve writes scope then receipt, so take locks in that same
            // order. The pending row must belong to this exact attempt.
            val scope = CharacterRewardPendingScopes.selectAll()
                .where {
                    (CharacterRewardPendingScopes.characterId eq characterId) and
                        (CharacterRewardPendingScopes.rewardKind eq rewardKind)
                }
                .forUpdate()
                .singleOrNull() ?: return@suspendTransaction false
            if (scope[CharacterRewardPendingScopes.sourceKey] != sourceKey ||
                scope[CharacterRewardPendingScopes.attemptToken] != attemptToken
            ) return@suspendTransaction false

            val pending = CharacterRewardLedger.selectAll()
                .where { key(characterId, rewardKind, sourceKey) }
                .forUpdate()
                .singleOrNull() ?: return@suspendTransaction false
            if (pending[CharacterRewardLedger.attemptToken] != attemptToken ||
                pending[CharacterRewardLedger.settledAtEpochMs] != null
            ) return@suspendTransaction false

            val updated = CharacterRewardLedger.update({
                key(characterId, rewardKind, sourceKey) and
                    (CharacterRewardLedger.attemptToken eq attemptToken)
            }) {
                it[CharacterRewardLedger.settledAtEpochMs] = nowEpochMs
            }
            check(updated == 1) { "Reward receipt changed during settlement" }

            val released = CharacterRewardPendingScopes.deleteWhere {
                (CharacterRewardPendingScopes.characterId eq characterId) and
                    (CharacterRewardPendingScopes.rewardKind eq rewardKind) and
                    (CharacterRewardPendingScopes.sourceKey eq sourceKey) and
                    (CharacterRewardPendingScopes.attemptToken eq attemptToken)
            }
            check(released == 1) { "Reward pending scope changed during settlement" }
            true
        }
    }

    override suspend fun find(
        characterId: Long, rewardKind: String, sourceKey: String
    ): RewardLedgerEntry? {
        validate(characterId, rewardKind, sourceKey)
        return suspendTransaction {
            CharacterRewardLedger.selectAll()
                .where { key(characterId, rewardKind, sourceKey) }
                .singleOrNull()
                ?.let { row ->
                    RewardLedgerEntry(
                        characterId = row[CharacterRewardLedger.characterId],
                        rewardKind = row[CharacterRewardLedger.rewardKind],
                        sourceKey = row[CharacterRewardLedger.sourceKey],
                        attemptToken = row[CharacterRewardLedger.attemptToken],
                        reservedAtEpochMs = row[CharacterRewardLedger.reservedAtEpochMs],
                        settledAtEpochMs = row[CharacterRewardLedger.settledAtEpochMs],
                    )
                }
        }
    }

    override suspend fun unresolvedForCharacter(characterId: Long): List<RewardLedgerEntry> {
        require(characterId > 0)
        return suspendTransaction {
            CharacterRewardLedger.selectAll()
                .where { CharacterRewardLedger.characterId eq characterId }
                .filter { it[CharacterRewardLedger.settledAtEpochMs] == null }
                .map { row ->
                RewardLedgerEntry(
                    characterId = row[CharacterRewardLedger.characterId],
                    rewardKind = row[CharacterRewardLedger.rewardKind],
                    sourceKey = row[CharacterRewardLedger.sourceKey],
                    attemptToken = row[CharacterRewardLedger.attemptToken],
                    reservedAtEpochMs = row[CharacterRewardLedger.reservedAtEpochMs],
                    settledAtEpochMs = row[CharacterRewardLedger.settledAtEpochMs],
                )
            }
        }
    }
}
