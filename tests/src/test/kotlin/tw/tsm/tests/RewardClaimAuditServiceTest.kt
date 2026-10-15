package tw.tsm.tests

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.app.gm.RewardClaimAuditService
import tw.tsm.domain.interfaces.repositories.EveEffectClaimRepository
import tw.tsm.domain.interfaces.repositories.RewardLedgerEntry
import tw.tsm.domain.interfaces.repositories.RewardLedgerRepository
import tw.tsm.domain.interfaces.repositories.UnresolvedEveEffectClaim

/** No live DB or client packets. Read-only admin report regression. */
class RewardClaimAuditServiceTest {
    private val eve = mockk<EveEffectClaimRepository>()
    private val rewards = mockk<RewardLedgerRepository>()
    private val report = RewardClaimAuditService(eve, rewards)

    @Test fun noPendingClaimsDoesNotImplyNeverPaid() = runTest {
        coEvery { eve.unresolvedForCharacter(31L) } returns emptyList()
        coEvery { rewards.unresolvedForCharacter(31L) } returns emptyList()
        val lines = report.inspect(31L)
        assertTrue(lines.first().contains("Eve=0"))
        assertTrue(lines.last().contains("Không kết luận"))
    }

    @Test fun pendingEveAndBattleClaimsShowReadableSummaryWithoutFullToken() = runTest {
        coEvery { eve.unresolvedForCharacter(31L) } returns listOf(
            UnresolvedEveEffectClaim(31L, 10851, 1, "01234567-some-private-run-token", 1_760_000_000_000L)
        )
        coEvery { rewards.unresolvedForCharacter(31L) } returns listOf(
            RewardLedgerEntry(
                31L, "BATTLE_GENERIC", "b:67ab23a5-0d52-4e33-8156-a90c8a078b6e:GENERIC",
                "abcdef01-full-secret-token", 1_760_000_001_000L, null
            )
        )
        val lines = report.inspect(31L)
        assertTrue(lines.first().contains("Eve=1"))
        assertTrue(lines.first().contains("thưởng=1"))
        assertTrue(lines.any { it.contains("map=10851") && it.contains("eve=1") })
        assertTrue(lines.any { it.contains("BATTLE_GENERIC") })
        assertFalse(lines.joinToString().contains("some-private-run-token"))
        assertFalse(lines.joinToString().contains("full-secret-token"))
        assertTrue(lines.last().contains("KHÔNG tự cấp lại"))
        assertTrue(lines.all { it.length <= 130 }, "Keep GM chat packets short")
    }

    @Test fun truncatesLargeClaimCollectionsWithoutHidingCounts() = runTest {
        coEvery { eve.unresolvedForCharacter(31L) } returns
            (1..12).map { UnresolvedEveEffectClaim(31L, 10100 + it, it, "eve-$it", 1_760_000_000_000L) }
        coEvery { rewards.unresolvedForCharacter(31L) } returns emptyList()
        val lines = report.inspect(31L)
        assertTrue(lines.first().contains("Eve=12"))
        assertEquals(8, lines.count { it.startsWith("EVE map") })
        assertTrue(lines.any { it.contains("SQL audit") })
    }

    @Test fun rejectsInvalidCharacterIdBeforeQuery() = runTest {
        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking { report.inspect(0L) }
        }
    }
}
