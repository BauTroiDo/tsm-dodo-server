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
import tw.tsm.domain.interfaces.repositories.DungeonRunRecord
import tw.tsm.domain.interfaces.repositories.DungeonRunRepository
import tw.tsm.domain.interfaces.repositories.RewardLedgerRepository
import tw.tsm.domain.valueobjects.ThingData
import tw.tsm.gamelogic.systems.*

/**
 * Test the REAL DungeonSystem code path using an inert inventory and repository.
 * All DB interactions are mocked: never send actual player rewards or use live DB.
 */
class DungeonNormalRewardLedgerIntegrationTest {
    private val dungeonDef = DungeonDef(
        id = 6001, nameId = 1L, kind = DungeonSystem.KIND_SINGLE,
        minLv = 1, maxLv = 200, maxPlayer = 1,
        missionFlag = 101, dailyFlag = 201, dailyCount = 3,
        skipFlag = 0, timeMinutes = 10, sceneId = 12001,
        costPoint = 0, lootTable = 900, extraLoot = 0,
        infoTextId = 0L, needPar = emptyList(), allPassPrize = 0,
    )
    private val rewardRows = listOf(
        DungeonComboDef(
            id = 900, kind = 2, kindValue = 100L,
            items = listOf(
                DungeonComboReward(29124, 1L), DungeonComboReward(0, 0L),
                DungeonComboReward(0, 0L), DungeonComboReward(0, 0L),
            ),
        )
    )

    private data class Fixture(
        val game: GameSession,
        val quest: QuestSystem,
        val inventory: InventorySystem,
        val ledger: RewardLedgerRepository,
        val runs: DungeonRunRepository,
        val sender: ResponseSender,
        val dungeon: DungeonSystem,
    )

    private fun fixture(
        reserve: Boolean = true,
        settle: Boolean = true,
        settlementFailure: Boolean = false,
        persisted: Boolean = true,
        originScene: Int = 10001,
    ): Fixture {
        val game = mockk<GameSession>(relaxed = true) {
            every { roleId } returns 31
            every { playerId } returns 31L
            every { mapId } returns originScene
            every { x } returns 10
            every { y } returns 11
            every { instanceId } returns 0
            every { turn } returns 0
        }
        val quest = mockk<QuestSystem>(relaxed = true)
        coEvery { quest.getMissions(31L) } returns emptyList()
        coEvery { quest.addMissionStep(31L, 201, 1) } returns 0

        val inventory = mockk<InventorySystem>(relaxed = true)
        every { inventory.canAddRewardsToBag(31L, listOf(29124 to 1)) } returns true
        coEvery { inventory.addRewardsToBagExact(31L, listOf(29124 to 1)) } returns listOf(5 to mockk<ThingData>(relaxed = true))

        val runs = mockk<DungeonRunRepository>(relaxed = true)
        val saved = DungeonRunRecord(
            characterId = 31L, dungeonId = 6001, startTime = 46000.0,
            startedAtMillis = 1000L, instanceId = 0,
            originMapId = originScene, originX = 10, originY = 11,
            originInstanceId = 0, settled = false,
        )
        coEvery { runs.load(31L) } returns if (persisted) saved else null

        val ledger = mockk<RewardLedgerRepository>(relaxed = true)
        coEvery {
            ledger.reserve(31L, "DUNGEON_NORMAL", any(), any(), any())
        } returns reserve
        if (settlementFailure) {
            coEvery {
                ledger.settle(31L, "DUNGEON_NORMAL", any(), any(), any())
            } throws IllegalStateException("MySQL ACK lost after inventory mutation")
        } else {
            coEvery {
                ledger.settle(31L, "DUNGEON_NORMAL", any(), any(), any())
            } returns settle
        }
        val sender = mockk<ResponseSender>(relaxed = true)
        val dungeon = DungeonSystem(
            defs = mapOf(6001 to dungeonDef),
            questSystem = quest,
            partySystem = mockk(relaxed = true),
            specialTeleport = mockk(relaxed = true),
            responseSender = sender,
            comboDefs = mapOf(900 to rewardRows),
            inventorySystem = inventory,
            clockMillis = { 1000L },
            dungeonRunRepository = runs,
            rewardLedgerRepository = ledger,
        )
        return Fixture(game, quest, inventory, ledger, runs, sender, dungeon)
    }

    @Test fun successfulPayoutReservesBeforeDailyCounterAndItemsThenSettles() = runTest {
        val f = fixture()
        f.dungeon.markSingleStarted(f.game, 6001, 46000.0)

        val completion = f.dungeon.settleAfterBattle(f.game, playerWin = true)

        assertEquals(0, completion?.result)
        assertEquals(listOf(29124 to 1), completion?.rewards)
        coVerifyOrder {
            f.ledger.reserve(31L, "DUNGEON_NORMAL", any(), any(), any())
            f.quest.addMissionStep(31L, 201, 1)
            f.inventory.addRewardsToBagExact(31L, listOf(29124 to 1))
            f.ledger.settle(31L, "DUNGEON_NORMAL", any(), any(), any())
            f.sender.sendSetBagItem(f.game, 5, any(), true)
        }
        assertTrue(f.dungeon.getActiveRun(31L)?.settled == true)
        coVerify(exactly = 1) { f.inventory.addRewardsToBagExact(any(), any()) }
        coVerify(exactly = 1) { f.ledger.settle(31L, "DUNGEON_NORMAL", any(), any(), any()) }
    }

    @Test fun fullBagIsRejectedBeforeInsertingAnyIrreversibleClaim() = runTest {
        val f = fixture()
        every { f.inventory.canAddRewardsToBag(31L, listOf(29124 to 1)) } returns false
        f.dungeon.markSingleStarted(f.game, 6001, 46000.0)

        assertEquals(1, f.dungeon.settleAfterBattle(f.game, playerWin = true)?.result)
        assertFalse(f.dungeon.getActiveRun(31L)?.settled == true)
        coVerify(exactly = 0) { f.ledger.reserve(any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { f.quest.addMissionStep(any(), any(), any()) }
        coVerify(exactly = 0) { f.inventory.addRewardsToBagExact(any(), any()) }
    }

    @Test fun pendingOrConflictingClaimBlocksDailyCounterAndItems() = runTest {
        val f = fixture(reserve = false)
        f.dungeon.markSingleStarted(f.game, 6001, 46000.0)

        assertEquals(1, f.dungeon.settleAfterBattle(f.game, playerWin = true)?.result)
        assertFalse(f.dungeon.getActiveRun(31L)?.settled == true)
        coVerify(exactly = 0) { f.quest.addMissionStep(any(), any(), any()) }
        coVerify(exactly = 0) { f.inventory.addRewardsToBagExact(any(), any()) }
        coVerify(exactly = 0) { f.ledger.settle(any(), any(), any(), any(), any()) }
    }

    @Test fun lostSettlementAcknowledgementNeverReplaysAwardAfterPacketRetry() = runTest {
        val f = fixture(settlementFailure = true)
        f.dungeon.markSingleStarted(f.game, 6001, 46000.0)

        assertEquals(1, f.dungeon.settleAfterBattle(f.game, playerWin = true)?.result)
        // This reward MAY already have been delivered; do not automatically retry.
        assertNull(f.dungeon.settleAfterBattle(f.game, playerWin = true))
        coVerify(exactly = 1) { f.inventory.addRewardsToBagExact(any(), any()) }
        coVerify(exactly = 1) { f.quest.addMissionStep(31L, 201, 1) }
        coVerify(exactly = 1) { f.ledger.settle(any(), any(), any(), any(), any()) }
        verify(exactly = 0) { f.sender.sendSetBagItem(any(), any(), any(), any()) }
    }

    @Test fun unrecoverableLegacyRunCannotCreateUnverifiablePayout() = runTest {
        val f = fixture(persisted = false, originScene = 0)
        f.dungeon.markSingleStarted(f.game, 6001, 46000.0)

        assertEquals(1, f.dungeon.settleAfterBattle(f.game, playerWin = true)?.result)
        coVerify(exactly = 0) { f.ledger.reserve(any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { f.inventory.addRewardsToBagExact(any(), any()) }
    }
}
