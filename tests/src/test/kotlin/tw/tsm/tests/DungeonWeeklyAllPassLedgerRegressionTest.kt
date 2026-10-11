package tw.tsm.tests

import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.domain.data.DecMissionResult
import tw.tsm.domain.data.DungeonDef
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.domain.interfaces.repositories.DungeonPeriodResetResult
import tw.tsm.domain.interfaces.repositories.MissionRecord
import tw.tsm.domain.interfaces.repositories.RewardLedgerRepository
import tw.tsm.domain.valueobjects.ThingData
import tw.tsm.gamelogic.systems.*

/** Purely mocked DB and bag. This never grants a real player's reward. */
class DungeonWeeklyAllPassLedgerRegressionTest {
    private val weekDefs = (0 until DungeonSystem.WEEK_PAGE_SIZE).associate { offset ->
        val id = 6800 + offset
        id to DungeonDef(
            id = id, nameId = 1L, kind = DungeonSystem.KIND_WEEKLY,
            minLv = 1, maxLv = 200, maxPlayer = 1,
            missionFlag = 0, dailyFlag = 7800 + offset, dailyCount = 1,
            skipFlag = 0, timeMinutes = 10, sceneId = 12001,
            costPoint = if (offset == 0) 8800 else 0, lootTable = 0, extraLoot = 0,
            infoTextId = 0L, needPar = emptyList(),
            allPassPrize = if (offset == 0) 29124 else 0,
        )
    }

    private data class Fixture(
        val game: GameSession,
        val quest: QuestSystem,
        val inventory: InventorySystem,
        val claims: RewardLedgerRepository,
        val sender: ResponseSender,
        val dungeon: DungeonSystem,
    )

    private fun fixture(
        bagHasSpace: Boolean = true,
        reserveAllowed: Boolean = true,
        settlementAckLost: Boolean = false,
        weekKey: Int = 20261005,
    ): Fixture {
        val game = mockk<GameSession>(relaxed = true) {
            every { roleId } returns 31
            every { playerId } returns 31L
        }
        val quest = mockk<QuestSystem>(relaxed = true)
        coEvery { quest.refreshDungeonDaily(31L) } returns emptyList()
        coEvery { quest.refreshDungeonWeekly(31L) } returns DungeonPeriodResetResult()
        coEvery { quest.getMissions(31L) } returns weekDefs.values.map {
            MissionRecord(it.dailyFlag, 1)
        }
        coEvery { quest.hasMissionFlag(31L, 8800) } returns false
        every { quest.completionBitId(8800) } returns 9900
        every { quest.dungeonWeekKey() } returns weekKey
        coEvery { quest.completeMission(31L, 8800) } returns
            DecMissionResult(completed = true, bitId = 9900)

        val inventory = mockk<InventorySystem>(relaxed = true)
        every { inventory.canAddItemToBag(31L, 29124, 1) } returns bagHasSpace
        coEvery { inventory.addItemsToBagExact(31L, 29124, 1) } returns
            listOf(5 to mockk<ThingData>(relaxed = true))

        val claims = mockk<RewardLedgerRepository>(relaxed = true)
        coEvery {
            claims.reserve(31L, "DUNGEON_ALL_PASS", "w:${weekKey}:0", any(), any())
        } returns reserveAllowed
        if (settlementAckLost) {
            coEvery { claims.settle(31L, "DUNGEON_ALL_PASS", any(), any(), any()) } throws
                IllegalStateException("simulated commit acknowledgement lost")
        } else {
            coEvery {
                claims.settle(31L, "DUNGEON_ALL_PASS", "w:${weekKey}:0", any(), any())
            } returns true
        }

        val sender = mockk<ResponseSender>(relaxed = true)
        val dungeon = DungeonSystem(
            defs = weekDefs, questSystem = quest,
            partySystem = mockk(relaxed = true),
            specialTeleport = mockk(relaxed = true),
            responseSender = sender, inventorySystem = inventory,
            clockMillis = { 1_000L }, rewardLedgerRepository = claims,
        )
        return Fixture(game, quest, inventory, claims, sender, dungeon)
    }

    @Test fun allPassClaimsByWeekAndPageBeforeItemAndCompletionMark() = runTest {
        val f = fixture()
        assertTrue(f.dungeon.claimWeeklyAllPass(f.game, 0))
        coVerifyOrder {
            f.claims.reserve(31L, "DUNGEON_ALL_PASS", "w:20261005:0", any(), any())
            f.inventory.addItemsToBagExact(31L, 29124, 1)
            f.quest.completeMission(31L, 8800)
            f.claims.settle(31L, "DUNGEON_ALL_PASS", "w:20261005:0", any(), any())
            f.sender.sendSetBagItem(f.game, 5, any(), true)
        }
    }

    @Test fun pendingWeekClaimCannotPayOrCompleteMark() = runTest {
        val f = fixture(reserveAllowed = false)
        assertFalse(f.dungeon.claimWeeklyAllPass(f.game, 0))
        coVerify(exactly = 0) { f.inventory.addItemsToBagExact(any(), any(), any()) }
        coVerify(exactly = 0) { f.quest.completeMission(any(), any()) }
        coVerify(exactly = 0) { f.claims.settle(any(), any(), any(), any(), any()) }
    }

    @Test fun fullBagDoesNotCreateIrreversiblePendingClaim() = runTest {
        val f = fixture(bagHasSpace = false)
        assertFalse(f.dungeon.claimWeeklyAllPass(f.game, 0))
        coVerify(exactly = 0) { f.claims.reserve(any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { f.inventory.addItemsToBagExact(any(), any(), any()) }
    }

    @Test fun lostSettlementAckDoesNotConfirmSuccessToClientOrRetryPayout() = runTest {
        val f = fixture(settlementAckLost = true)
        assertFalse(f.dungeon.claimWeeklyAllPass(f.game, 0))
        coVerify(exactly = 1) { f.inventory.addItemsToBagExact(31L, 29124, 1) }
        coVerify(exactly = 1) { f.quest.completeMission(31L, 8800) }
        verify(exactly = 0) { f.sender.sendSetBagItem(any(), any(), any(), any()) }
        verify(exactly = 0) { f.sender.sendSetMissionFlags(any(), any()) }
    }

    @Test fun newCalendarWeekHasDifferentAllPassSourceKey() = runTest {
        val f = fixture(weekKey = 20261012)
        assertTrue(f.dungeon.claimWeeklyAllPass(f.game, 0))
        coVerify(exactly = 1) {
            f.claims.reserve(31L, "DUNGEON_ALL_PASS", "w:20261012:0", any(), any())
        }
    }
}
