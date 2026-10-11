package tw.tsm.tests

import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.domain.interfaces.repositories.RewardLedgerRepository
import tw.tsm.gamelogic.systems.BattleRewardLedgerCoordinator
import java.util.UUID

class BattleRewardLedgerCoordinatorTest {
    @Test fun eachPlayerHasOwnClaimForOneBattle() = runTest {
        val repository = mockk<RewardLedgerRepository>()
        coEvery { repository.reserve(any(), any(), any(), any(), any()) } returns true
        coEvery { repository.settle(any(), any(), any(), any(), any()) } returns true
        val coordinator = BattleRewardLedgerCoordinator(repository, { 1000L })
        val uuid = UUID.randomUUID().toString()
        val first = coordinator.reserve(31L, uuid)!!
        val second = coordinator.reserve(32L, uuid)!!
        assertEquals(first.sourceKey, second.sourceKey)
        assertNotEquals(first.token, second.token)
        assertTrue(coordinator.settle(first))
        assertTrue(coordinator.settle(second))
        coVerify(exactly = 1) { repository.reserve(31L, "BATTLE_GENERIC", any(), any(), 1000L) }
        coVerify(exactly = 1) { repository.reserve(32L, "BATTLE_GENERIC", any(), any(), 1000L) }
    }

    @Test fun rejectedOrUnavailableDatabaseDoesNotGrantClaim() = runTest {
        val repository = mockk<RewardLedgerRepository>()
        coEvery { repository.reserve(any(), any(), any(), any(), any()) } returns false
        val coordinator = BattleRewardLedgerCoordinator(repository)
        assertNull(coordinator.reserve(31L, UUID.randomUUID().toString()))
        coEvery { repository.reserve(any(), any(), any(), any(), any()) } throws
            IllegalStateException("unavailable")
        assertNull(coordinator.reserve(31L, UUID.randomUUID().toString()))
        coVerify(exactly = 0) { repository.settle(any(), any(), any(), any(), any()) }
    }

    @Test fun lostSettlementAckIsNotConsideredCommitted() = runTest {
        val repository = mockk<RewardLedgerRepository>()
        coEvery { repository.reserve(any(), any(), any(), any(), any()) } returns true
        coEvery { repository.settle(any(), any(), any(), any(), any()) } throws
            IllegalStateException("ack lost")
        val coordinator = BattleRewardLedgerCoordinator(repository)
        val claim = coordinator.reserve(31L, UUID.randomUUID().toString())!!
        assertFalse(coordinator.settle(claim))
    }

    @Test fun invalidBattleIdCannotBeUsedAsPayoutKey() = runTest {
        val repository = mockk<RewardLedgerRepository>()
        val coordinator = BattleRewardLedgerCoordinator(repository)
        assertNull(coordinator.reserve(31L, "42"))
        assertNull(coordinator.reserve(0L, UUID.randomUUID().toString()))
        coVerify(exactly = 0) { repository.reserve(any(), any(), any(), any(), any()) }
    }
}
