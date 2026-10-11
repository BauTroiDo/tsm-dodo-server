package tw.tsm.persistence.repositories

import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.plus
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.upsert
import tw.tsm.persistence.tables.CharacterCompletedEvents
import tw.tsm.persistence.tables.CharacterEveEffectClaims

/** A claim requiring manual investigation if the process exits before settlement. */
data class UnresolvedEveEffectClaim(
    val charId: Long,
    val mapId: Int,
    val eveNo: Int,
    val runToken: String,
    val reservedAtEpochMs: Long,
)

/**
 * Storage primitive for an eventual durable Eve side-effect fence.
 *
 * Not wired into gameplay yet. A caller MUST reserve before any item, gold,
 * mission, pet or other non-idempotent effect. A reservation that survives a
 * crash is never reclaimed merely because it is old.
 */
class EveEffectClaimRepositoryImpl {
    private fun validate(charId: Long, mapId: Int, eveNo: Int, runToken: String) {
        require(charId > 0 && mapId > 0 && eveNo > 0)
        require(runToken.length in 1..64)
    }

    /**
     * Claim a per-player Eve slot. Repeated calls by the SAME run are allowed
     * (multi-result chain), but a different run cannot steal a pending claim.
     * The unique DB primary key resolves cross-process and concurrent races.
     */
    suspend fun reserve(
        charId: Long,
        mapId: Int,
        eveNo: Int,
        runToken: String,
        nowEpochMs: Long,
    ): Boolean {
        validate(charId, mapId, eveNo, runToken)
        require(nowEpochMs > 0)
        return suspendTransaction {
            CharacterEveEffectClaims.insertIgnore {
                it[CharacterEveEffectClaims.charId] = charId
                it[CharacterEveEffectClaims.mapId] = mapId
                it[CharacterEveEffectClaims.eveNo] = eveNo
                it[CharacterEveEffectClaims.runToken] = runToken
                it[CharacterEveEffectClaims.reservedAtEpochMs] = nowEpochMs
            }
            CharacterEveEffectClaims.selectAll()
                .where {
                    (CharacterEveEffectClaims.charId eq charId) and
                    (CharacterEveEffectClaims.mapId eq mapId) and
                    (CharacterEveEffectClaims.eveNo eq eveNo)
                }
                .single()[CharacterEveEffectClaims.runToken] == runToken
        }
    }

    /** List claims for audit/reconciliation; never auto-clear on reconnect. */
    suspend fun unresolvedForCharacter(charId: Long): List<UnresolvedEveEffectClaim> =
        suspendTransaction {
            CharacterEveEffectClaims.selectAll()
                .where { CharacterEveEffectClaims.charId eq charId }
                .map { row ->
                    UnresolvedEveEffectClaim(
                        charId = row[CharacterEveEffectClaims.charId],
                        mapId = row[CharacterEveEffectClaims.mapId],
                        eveNo = row[CharacterEveEffectClaims.eveNo],
                        runToken = row[CharacterEveEffectClaims.runToken],
                        reservedAtEpochMs = row[CharacterEveEffectClaims.reservedAtEpochMs],
                    )
                }
        }

    /**
     * Commit EveCount and release the exact reservation atomically.
     * Wrong/missing token -> no effects and null, never a second count.
     *
     * This must ONLY be called after every gameplay effect was acknowledged;
     * it cannot make pre-existing independent reward DB mutations atomic.
     */
    suspend fun settle(
        charId: Long,
        mapId: Int,
        eveNo: Int,
        runToken: String,
    ): Int? {
        validate(charId, mapId, eveNo, runToken)
        return suspendTransaction {
            val claim = CharacterEveEffectClaims.selectAll()
                .where {
                    (CharacterEveEffectClaims.charId eq charId) and
                    (CharacterEveEffectClaims.mapId eq mapId) and
                    (CharacterEveEffectClaims.eveNo eq eveNo)
                }
                .forUpdate()
                .singleOrNull() ?: return@suspendTransaction null
            if (claim[CharacterEveEffectClaims.runToken] != runToken) {
                return@suspendTransaction null
            }

            CharacterCompletedEvents.upsert(
                onUpdate = {
                    it[CharacterCompletedEvents.completionCount] =
                        CharacterCompletedEvents.completionCount + 1
                }
            ) {
                it[CharacterCompletedEvents.charId] = charId
                it[CharacterCompletedEvents.mapId] = mapId
                it[CharacterCompletedEvents.eveNo] = eveNo
                it[CharacterCompletedEvents.completionCount] = 1
            }
            val committedCount = CharacterCompletedEvents.selectAll()
                .where {
                    (CharacterCompletedEvents.charId eq charId) and
                    (CharacterCompletedEvents.mapId eq mapId) and
                    (CharacterCompletedEvents.eveNo eq eveNo)
                }
                .single()[CharacterCompletedEvents.completionCount]
            val deleted = CharacterEveEffectClaims.deleteWhere {
                (CharacterEveEffectClaims.charId eq charId) and
                (CharacterEveEffectClaims.mapId eq mapId) and
                (CharacterEveEffectClaims.eveNo eq eveNo) and
                (CharacterEveEffectClaims.runToken eq runToken)
            }
            check(deleted == 1) { "Eve effect claim changed during settlement" }
            committedCount
        }
    }
}
