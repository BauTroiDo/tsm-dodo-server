package tw.tsm.tests

import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.domain.data.DungeonComboDef
import tw.tsm.domain.data.DungeonComboReward
import tw.tsm.domain.data.DungeonDef
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.domain.interfaces.repositories.DungeonPeriodResetResult
import tw.tsm.domain.interfaces.repositories.DungeonRunRecord
import tw.tsm.domain.interfaces.repositories.DungeonRunRepository
import tw.tsm.domain.interfaces.repositories.RewardLedgerRepository
import tw.tsm.domain.valueobjects.ThingData
import tw.tsm.gamelogic.systems.*

class DungeonWeeklyRewardLedgerRegressionTest {
    private val dungeonDef = DungeonDef(
        id = 6401, nameId = 1L, kind = DungeonSystem.KIND_WEEKLY,
        minLv = 1, maxLv = 200, maxPlayer = 1,
        missionFlag = 0, dailyFlag = 7401, dailyCount = 1,
        skipFlag = 0, timeMinutes = 10, sceneId = 12001,
        costPoint = 0, lootTable = 9401, extraLoot = 0,
        infoTextId = 0L, needPar = emptyList(), allPassPrize = 0,
    )
    private val rewards = listOf(DungeonComboDef(
        id = 9401, kind = 2, kindValue = 100L,
        items = listOf(
            DungeonComboReward(29124, 1L),
            DungeonComboReward(0, 0L),
            DungeonComboReward(0, 0L),
            DungeonComboReward(0, 0L),
        ),
    ))

    private data class Fixture(
        val game: GameSession, val quest: QuestSystem,
        val inventory: InventorySystem, val ledger: RewardLedgerRepository,
        val dungeon: DungeonSystem, val sender: ResponseSender
    )
    private fun fixture(reserved: Boolean = true, lostAck: Boolean = false): Fixture {
        val game = mockk<GameSession>(relaxed = true) {
            every { roleId } returns 31
            every { playerId } returns 31L
            every { mapId } returns 10001
            every { x } returns 10
            every { y } returns 11
            every { instanceId } returns 0
            every { turn } returns 0
        }
        val quest = mockk<QuestSystem>(relaxed = true)
        coEvery { quest.refreshDungeonDaily(31L) } returns emptyList()
        coEvery { quest.refreshDungeonWeekly(31L) } returns DungeonPeriodResetResult()
        coEvery { quest.getMissions(31L) } returns emptyList()
        coEvery { quest.setMissionStep(31L, 7401, 1) } just Runs

        val inventory = mockk<InventorySystem>(relaxed = true)
        every { inventory.canAddRewardsToBag(31L, listOf(29124 to 1)) } returns true
        coEvery { inventory.addRewardsToBagExact(31L, listOf(29124 to 1)) } returns
            listOf(5 to mockk<ThingData>(relaxed = true))

        var stored: DungeonRunRecord? = null
        val runs = mockk<DungeonRunRepository>(relaxed = true)
        coEvery { runs.save(any()) } coAnswers { stored = firstArg() }
        coEvery { runs.load(31L) } coAnswers { stored }

        val ledger = mockk<RewardLedgerRepository>(relaxed = true)
        coEvery { ledger.reserve(31L, "DUNGEON_WEEKLY", any(), any(), any()) } returns reserved
        if (lostAck) {
            coEvery { ledger.settle(31L, "DUNGEON_WEEKLY", any(), any(), any()) } throws
                IllegalStateException("simulated lost settlement ACK")
        } else {
            coEvery { ledger.settle(31L, "DUNGEON_WEEKLY", any(), any(), any()) } returns true
        }
        val sender = mockk<ResponseSender>(relaxed = true)
        val dungeon = DungeonSystem(
            defs = mapOf(6401 to dungeonDef), questSystem = quest,
            partySystem = mockk(relaxed = true),
            specialTeleport = mockk(relaxed = true),
            responseSender = sender,
            comboDefs = mapOf(9401 to rewards), inventorySystem = inventory,
            clockMillis = { 1000L }, dungeonRunRepository = runs,
            rewardLedgerRepository = ledger,
        )
        return Fixture(game, quest, inventory, ledger, dungeon, sender)
    }

    @Test fun weeklyWinReservesBeforeItemAndFlagWritesAndSettlesBeforeClientPackets() = runTest {
        val f = fixture()
        f.dungeon.markSingleStarted(f.game, 6401, 46000.0)
        val result = f.dungeon.settleWeeklyBattle(f.game, 6401, true)
        assertEquals(0, result?.result)
        assertEquals(listOf(29124 to 1), result?.rewards)
        coVerifyOrder {
            f.ledger.reserve(31L, "DUNGEON_WEEKLY", any(), any(), any())
            f.inventory.addRewardsToBagExact(31L, listOf(29124 to 1))
            f.quest.setMissionStep(31L, 7401, 1)
            f.ledger.settle(31L, "DUNGEON_WEEKLY", any(), any(), any())
            f.sender.sendSetBagItem(f.game, 5, any(), true)
        }
        coVerify(exactly = 1) { f.ledger.reserve(31L, "DUNGEON_WEEKLY", any(), any(), any()) }
    }

    @Test fun duplicatePendingWeeklyClaimFailsBeforeItemsAndMissionFlag() = runTest {
        val f = fixture(reserved = false)
        f.dungeon.markSingleStarted(f.game, 6401, 46000.0)
        assertEquals(1, f.dungeon.settleWeeklyBattle(f.game, 6401, true)?.result)
        coVerify(exactly = 0) { f.inventory.addRewardsToBagExact(any(), any()) }
        coVerify(exactly = 0) { f.quest.setMissionStep(any(), any(), any()) }
        coVerify(exactly = 0) { f.ledger.settle(any(), any(), any(), any(), any()) }
        assertFalse(f.dungeon.getActiveRun(31L)?.settled == true)
    }

    @Test fun fullBagDoesNotCreatePendingWeeklyClaim() = runTest {
        val f = fixture()
        every { f.inventory.canAddRewardsToBag(31L, listOf(29124 to 1)) } returns false
        f.dungeon.markSingleStarted(f.game, 6401, 46000.0)
        assertEquals(1, f.dungeon.settleWeeklyBattle(f.game, 6401, true)?.result)
        coVerify(exactly = 0) { f.ledger.reserve(any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { f.quest.setMissionStep(any(), any(), any()) }
    }

    @Test fun lostAckAfterAwardLeavesPendingWithoutSecondPayout() = runTest {
        val f = fixture(lostAck = true)
        f.dungeon.markSingleStarted(f.game, 6401, 46000.0)
        assertEquals(1, f.dungeon.settleWeeklyBattle(f.game, 6401, true)?.result)
        assertNull(f.dungeon.settleWeeklyBattle(f.game, 6401, true))
        coVerify(exactly = 1) { f.inventory.addRewardsToBagExact(any(), any()) }
        coVerify(exactly = 1) { f.quest.setMissionStep(31L, 7401, 1) }
        verify(exactly = 0) { f.sender.sendSetBagItem(any(), any(), any(), any()) }
    }
}
