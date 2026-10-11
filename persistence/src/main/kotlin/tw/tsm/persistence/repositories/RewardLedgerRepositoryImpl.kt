package tw.tsm.persistence.repositories

import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.update
import tw.tsm.domain.interfaces.repositories.RewardLedgerEntry
import tw.tsm.domain.interfaces.repositories.RewardLedgerRepository
import tw.tsm.persistence.tables.CharacterRewardLedger

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
            // The unique PK serializes simultaneous callers, including across
            // JVMs. insertedCount is the only permission to execute a payout.
            // A duplicate attempt MUST NOT "resume" and pay again.
            CharacterRewardLedger.insertIgnore {
                it[CharacterRewardLedger.characterId] = characterId
                it[CharacterRewardLedger.rewardKind] = rewardKind
                it[CharacterRewardLedger.sourceKey] = sourceKey
                it[CharacterRewardLedger.attemptToken] = attemptToken
                it[CharacterRewardLedger.reservedAtEpochMs] = nowEpochMs
                it[CharacterRewardLedger.settledAtEpochMs] = null
            }.insertedCount == 1
        }
    }

    override suspend fun settle(
        characterId: Long, rewardKind: String, sourceKey: String,
        attemptToken: String, nowEpochMs: Long,
    ): Boolean {
        validate(characterId, rewardKind, sourceKey)
        validateAttempt(attemptToken, nowEpochMs)
        return suspendTransaction {
            val pending = CharacterRewardLedger.selectAll()
                .where { key(characterId, rewardKind, sourceKey) }
                .forUpdate()
                .singleOrNull() ?: return@suspendTransaction false
            if (pending[CharacterRewardLedger.attemptToken] != attemptToken ||
                pending[CharacterRewardLedger.settledAtEpochMs] != null
            ) return@suspendTransaction false

            CharacterRewardLedger.update({
                key(characterId, rewardKind, sourceKey) and
                    (CharacterRewardLedger.attemptToken eq attemptToken) and
                    CharacterRewardLedger.settledAtEpochMs.isNull()
            }) {
                it[CharacterRewardLedger.settledAtEpochMs] = nowEpochMs
            } == 1
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
            CharacterRewardLedger.selectAll().where {
                (CharacterRewardLedger.characterId eq characterId) and
                    CharacterRewardLedger.settledAtEpochMs.isNull()
            }.map { row ->
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
