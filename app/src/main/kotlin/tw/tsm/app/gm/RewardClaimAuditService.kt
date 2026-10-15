package tw.tsm.app.gm

import tw.tsm.domain.interfaces.repositories.EveEffectClaimRepository
import tw.tsm.domain.interfaces.repositories.RewardLedgerRepository
import java.time.Instant

/**
 * GM read-only reconciliation report. It does NOT settle, clear, replay,
 * grant items, or infer that a pending claim means the reward was paid.
 *
 * Keep each line short: DVT system-chat packets have a limited byte length.
 * The full source key and token remain available in MySQL read-only audit SQL.
 */
class RewardClaimAuditService(
    private val eve: EveEffectClaimRepository,
    private val rewards: RewardLedgerRepository,
) {
    suspend fun inspect(characterId: Long): List<String> {
        require(characterId > 0) { "ID nhân vật phải > 0" }
        val eveClaims = eve.unresolvedForCharacter(characterId)
        val rewardClaims = rewards.unresolvedForCharacter(characterId)
        val result = mutableListOf(
            "Claim nhân vật $characterId: Eve=${eveClaims.size}, thưởng=${rewardClaims.size}"
        )
        for (claim in eveClaims.take(MAX_ROWS_PER_KIND)) {
            result += "EVE map=${claim.mapId} eve=${claim.eveNo} " +
                "token=${claim.runToken.take(8)}... " +
                "t=${formatUtc(claim.reservedAtEpochMs)}"
        }
        for (claim in rewardClaims.take(MAX_ROWS_PER_KIND)) {
            result += "${claim.rewardKind.take(28)} ${claim.sourceKey.take(32)} " +
                "token=${claim.attemptToken.take(8)}... " +
                "t=${formatUtc(claim.reservedAtEpochMs)}"
        }
        if (eveClaims.size > MAX_ROWS_PER_KIND || rewardClaims.size > MAX_ROWS_PER_KIND) {
            result += "Còn claim khác: xem SQL audit (chỉ đọc)."
        }
        if (eveClaims.isNotEmpty() || rewardClaims.isNotEmpty()) {
            result += "PENDING chưa chứng minh đã nhận quà. KHÔNG tự cấp lại/xóa."
        } else {
            result += "Không có claim PENDING. Không kết luận chưa từng nhận thưởng."
        }
        return result
    }

    private fun formatUtc(epochMs: Long): String =
        runCatching { Instant.ofEpochMilli(epochMs).toString().take(16) }.getOrElse { "?" }

    private companion object {
        const val MAX_ROWS_PER_KIND = 8
    }
}
