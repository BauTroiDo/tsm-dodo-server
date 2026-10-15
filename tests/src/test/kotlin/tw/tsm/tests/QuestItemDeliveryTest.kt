package tw.tsm.tests

import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.domain.data.EveResult
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.domain.valueobjects.ThingData
import tw.tsm.gamelogic.systems.*

class QuestItemDeliveryTest {
    private val response = mockk<ResponseSender>(relaxed = true)
    private val inventory = mockk<InventorySystem>(relaxed = true)
    private val skills = mockk<SkillSystem>(relaxed = true)
    private val pets = mockk<PetSystem>(relaxed = true)
    private val scenes = mapOf(12001 to tw.tsm.domain.data.SceneEveData(
        mapOf(1 to tw.tsm.domain.data.EveNpcPlacement(1, 14131, emptyList())),
        emptyMap(), emptyMap(), emptyMap()))
    private val npcDefs = mapOf(14131 to mockk<tw.tsm.domain.data.NpcDef>(relaxed = true),
        24 to mockk<tw.tsm.domain.data.NpcDef>(relaxed = true))
    private val money = mockk<MoneySystem>(relaxed = true)
    private val quests = mockk<QuestSystem>(relaxed = true)
    private val session = mockk<GameSession>(relaxed = true) {
        every { roleId } returns 31
        every { characterName } returns "QuestTester"
        every { mapId } returns 12001
    }
    private val executor = EveResultExecutor(
        response, quests, inventory, pets, skills,
        money, npcDefs, emptyMap(), emptyMap(), scenes,
        mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
        mockk(relaxed = true),
    )
    private fun item(qty: Int, style: Int = 1, type: Int = 0) =
        EveResult(0, 1, type, 1, 29124, style, qty, 0)

    @Test fun grantSynchronizesEveryChangedStack() = runTest {
        // The deployed DVT item preflight validates even one item change.
        every { inventory.canApplyQuestItemChanges(31L, listOf(29124 to 3)) } returns true
        coEvery { inventory.addItemsToBagExact(31L, 29124, 3) } returns listOf(
            0 to ThingData(itemId = 29124, quantity = 9999),
            7 to ThingData(itemId = 29124, quantity = 2),
        )
        assertTrue(executor.executeResultClass(session, item(3)))
        verify(exactly = 2) { response.sendSetBagItem(session, any(), any(), true) }
        coVerify(exactly = 0) { inventory.addItemToBag(any(), any(), any()) }
    }

    @Test fun failedGrantDoesNotReportItemsAndReturnsFailure() = runTest {
        coEvery { inventory.addItemsToBagExact(31L, 29124, 3) } returns null
        assertFalse(executor.executeResultClass(session, item(3)))
        verify(exactly = 0) { response.sendSetBagItem(any(), any(), any(), any()) }
        verify(exactly = 1) { response.sendSystemMessage(session, any()) }
    }

    @Test fun submissionConsumesAcrossStacksAndSynchronizesEach() = runTest {
        // A mocked bag has no storage backing; authorize the checked debit
        // explicitly while leaving other tests' fail-closed defaults intact.
        every { inventory.canApplyQuestItemChanges(31L, listOf(29124 to -3)) } returns true
        every { inventory.getBag(31L) } returns mapOf(
            0 to ThingData(itemId = 29124, quantity = 2),
            1 to ThingData(itemId = 29125, quantity = 20),
            7 to ThingData(itemId = 29124, quantity = 2),
        )
        coEvery { inventory.removeBagItemsExact(31L, listOf(0 to 2, 7 to 1)) } returns true
        assertTrue(executor.executeResultClass(session, item(3, 2)))
        verify { response.sendReduceBagItem(session, 0, 2) }
        verify { response.sendReduceBagItem(session, 7, 1) }
        coVerify(exactly = 0) { inventory.removeFromBag(any(), any(), any()) }
    }

    @Test fun missingSubmissionDoesNotConsumeAnything() = runTest {
        every { inventory.getBag(31L) } returns mapOf(0 to ThingData(itemId = 29124, quantity = 2))
        assertFalse(executor.executeResultClass(session, item(3, 2)))
        coVerify(exactly = 0) { inventory.removeBagItemsExact(any(), any()) }
        verify(exactly = 0) { response.sendReduceBagItem(any(), any(), any()) }
    }

    @Test fun failedSubmissionDoesNotSendClientReduction() = runTest {
        every { inventory.getBag(31L) } returns mapOf(0 to ThingData(itemId = 29124, quantity = 3))
        coEvery { inventory.removeBagItemsExact(any(), any()) } returns false
        assertFalse(executor.executeResultClass(session, item(3, 2)))
        verify(exactly = 0) { response.sendReduceBagItem(any(), any(), any()) }
    }

    @Test fun failedItemMutationAbortsBothServerResultTypesWithoutAdvancingQuest() = runTest {
        for (type in listOf(0, 7)) {
            val stateMachine = mockk<EveEventStateMachine>(relaxed = true)
            val flow = EveFlowController(
                response, stateMachine, mockk(relaxed = true), mockk(relaxed = true), executor,
                mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
                mockk(relaxed = true), mockk(relaxed = true), emptyMap(), mockk(relaxed = true),
            )
            coEvery { inventory.addItemsToBagExact(any(), any(), any()) } returns null
            flow.startEvent(session, item(3, type = type))
            verify(exactly = 1) { stateMachine.clear(31L) }
            verify(exactly = 0) { stateMachine.advance(any()) }
            coVerify(exactly = 0) { quests.completeMission(any(), any()) }
        }
    }
    @Test fun unsupportedRewardBlocksExchangeBeforeAnyItemsAreConsumed() = runTest {
        for (type in listOf(0, 7)) {
            val consume = item(3, style = 2, type = type)
            val reward = EveResult(0, 2, type, 13, 0, 0, 52, 0)
            val complete = EveResult(0, 3, type, 2, 10846, 3, 0, 0)
            val stateMachine = mockk<EveEventStateMachine>(relaxed = true)
            every { stateMachine.getSession(31L) } returns EventSession(
                mapId = 10989, eveNo = 4, npcClickId = 1,
                results = listOf(consume, reward, complete), currentIndex = 1,
            )
            val flow = EveFlowController(
                response, stateMachine, mockk(relaxed = true), mockk(relaxed = true), executor,
                mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
                mockk(relaxed = true), mockk(relaxed = true), emptyMap(), mockk(relaxed = true),
            )
            flow.startEvent(session, consume)
            coVerify(exactly = 0) { inventory.removeBagItemsExact(any(), any()) }
            coVerify(exactly = 0) { inventory.addItemsToBagExact(any(), any(), any()) }
            coVerify(exactly = 0) { quests.completeMission(any(), any()) }
            verify(exactly = 1) { stateMachine.clear(31L) }
            verify(exactly = 0) { stateMachine.advance(any()) }
        }
    }

    @Test fun packedMissionResultCannotWriteHugeStepToMissionTwo() = runTest {
        val packed = EveResult(0, 1, 0, 2, 2, 1, 912152, 0)
        assertFalse(executor.executeResultClass(session, packed))
        coVerify(exactly = 0) { quests.addMissionStep(any(), any(), any()) }
    }

    @Test fun unknownItemOperationAndUnconfiguredRewardsReturnFailure() = runTest {
        assertFalse(executor.executeResultClass(session, item(200, style = 3)))
        for (cls in listOf(11, 13, 15)) {
            assertFalse(executor.executeResultClass(session, EveResult(0, 1, 0, cls, 0, 0, 52, 0)))
        }
        coVerify(exactly = 0) { inventory.addItemsToBagExact(any(), any(), any()) }
        coVerify(exactly = 0) { inventory.removeBagItemsExact(any(), any()) }
    }

    @Test fun failedMoneyExchangeDoesNotAdvanceOrCompleteMission() = runTest {
        val stateMachine = mockk<EveEventStateMachine>(relaxed = true)
        val flow = EveFlowController(
            response, stateMachine, mockk(relaxed = true), mockk(relaxed = true), executor,
            mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
            mockk(relaxed = true), mockk(relaxed = true), emptyMap(), mockk(relaxed = true),
        )
        every { money.subtractGold(31L, 100) } returns false
        flow.startEvent(session, EveResult(0, 1, 0, 5, 0, 2, 100, 0))
        verify(exactly = 0) { response.sendSubtractMoney(any(), any()) }
        verify(exactly = 0) { stateMachine.advance(any()) }
        verify(exactly = 1) { stateMachine.clear(31L) }
        coVerify(exactly = 0) { quests.completeMission(any(), any()) }
    }

    @Test fun successfulMoneyResultSynchronizesAndReturnsSuccess() = runTest {
        every { money.addGold(31L, 100) } returns true
        assertTrue(executor.executeResultClass(session, EveResult(0, 1, 0, 5, 0, 1, 100, 0)))
        verify(exactly = 1) { response.sendAddMoney(session, 100) }
    }

    @Test fun overflowingFutureProgressBlocksItemSubmissionBeforeConsumption() = runTest {
        val consume = item(3, style = 2)
        val add = EveResult(0, 2, 0, 2, 10025, 1, 1, 0)
        coEvery { quests.getMissions(31L) } returns listOf(tw.tsm.domain.interfaces.repositories.MissionRecord(10025, 255))
        val stateMachine = mockk<EveEventStateMachine>(relaxed = true)
        every { stateMachine.getSession(31L) } returns EventSession(12001, 1, 1,
            results = listOf(consume, add), currentIndex = 1)
        val flow = EveFlowController(response, stateMachine, mockk(relaxed = true), mockk(relaxed = true), executor,
            mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
            mockk(relaxed = true), mockk(relaxed = true), emptyMap(), mockk(relaxed = true))
        flow.startEvent(session, consume)
        coVerify(exactly = 0) { inventory.removeBagItemsExact(any(), any()) }
        coVerify(exactly = 0) { quests.addMissionStep(any(), any(), any()) }
        verify(exactly = 1) { stateMachine.clear(31L) }
    }

    @Test fun pendingCurrentResultIsCountedOnceAtByteBoundary() = runTest {
        val add = EveResult(0, 1, 0, 2, 10025, 1, 1, 0)
        coEvery { quests.getMissions(31L) } returns listOf(tw.tsm.domain.interfaces.repositories.MissionRecord(10025, 254))
        coEvery { quests.addMissionStep(31L, 10025, 1) } returns 254
        val stateMachine = mockk<EveEventStateMachine>(relaxed = true)
        every { stateMachine.getSession(31L) } returns EventSession(12001, 1, 1,
            results = listOf(add), currentIndex = 1)
        every { stateMachine.advance(31L) } returns null
        val flow = EveFlowController(response, stateMachine, mockk(relaxed = true), mockk(relaxed = true), executor,
            mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
            mockk(relaxed = true), mockk(relaxed = true), emptyMap(), mockk(relaxed = true))
        flow.startEvent(session, add)
        coVerify(exactly = 1) { quests.addMissionStep(31L, 10025, 1) }
        verify(exactly = 1) { response.sendAddMissionStep(session, 10025, 1) }
    }

    @Test fun fullFollowSlotsBlockItemSubmissionBeforePetReward() = runTest {
        val consume = item(3, style = 2)
        val reward = EveResult(0, 2, 0, 8, 14131, 1, 0, 0)
        every { pets.getUnlockedFollowCapacity(31L) } returns 0
        val stateMachine = mockk<EveEventStateMachine>(relaxed = true)
        every { stateMachine.getSession(31L) } returns EventSession(12001, 1, 1,
            results = listOf(consume, reward), currentIndex = 1)
        val flow = EveFlowController(response, stateMachine, mockk(relaxed = true), mockk(relaxed = true), executor,
            mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
            mockk(relaxed = true), mockk(relaxed = true), emptyMap(), mockk(relaxed = true))
        flow.startEvent(session, consume)
        coVerify(exactly = 0) { inventory.removeBagItemsExact(any(), any()) }
        coVerify(exactly = 0) { quests.completeMission(any(), any()) }
        verify(exactly = 1) { stateMachine.clear(31L) }
    }

    @Test fun failedPetAllocationReturnsFailureEvenAfterCapacityCheckPasses() = runTest {
        every { pets.getUnlockedFollowCapacity(31L) } returns 4
        coEvery { pets.addFollowNpc(any(), any(), any(), any()) } returns null
        assertFalse(executor.executeResultClass(session, EveResult(0, 1, 0, 8, 14131, 1, 0, 0)))
        verify(exactly = 0) { response.sendFollowNpcAdd(any(), any(), any(), any(), any(), any()) }
    }

    @Test fun missingSkillCacheDoesNotReportSuccess() = runTest {
        val skill = EveResult(0, 1, 0, 4, 14001, 1, 1, 0)
        every { skills.getCache(31L) } returns null
        assertFalse(executor.executeResultClass(session, skill))
    }

    @Test fun existingEscortDoesNotDuplicateOrFailWhenFollowSlotsAreFull() = runTest {
        val existing = mockk<tw.tsm.domain.valueobjects.FollowNpcData>(relaxed = true)
        every { existing.npcId } returns 14131
        every { pets.getFollowPets(31L) } returns mapOf(0 to existing)
        every { pets.getUnlockedFollowCapacity(31L) } returns 1
        assertTrue(executor.executeResultClass(session, EveResult(0, 1, 0, 3, 1, 1, 0, 0)))
        coVerify(exactly = 0) { pets.addFollowNpc(any(), any(), any(), any()) }
    }

    @Test fun exchangeThatCannotFitAllRewardsStopsBeforeSubmission() = runTest {
        val consume = item(3, style = 2)
        val reward = EveResult(0, 2, 0, 1, 29125, 1, 1, 0)
        every { inventory.canApplyQuestItemChanges(31L, any()) } returns false
        val stateMachine = mockk<EveEventStateMachine>(relaxed = true)
        every { stateMachine.getSession(31L) } returns EventSession(12001, 1, 1,
            results = listOf(consume, reward), currentIndex = 1)
        val flow = EveFlowController(response, stateMachine, mockk(relaxed = true), mockk(relaxed = true), executor,
            mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
            mockk(relaxed = true), mockk(relaxed = true), emptyMap(), mockk(relaxed = true))
        flow.startEvent(session, consume)
        coVerify(exactly = 0) { inventory.removeBagItemsExact(any(), any()) }
        coVerify(exactly = 0) { inventory.addItemsToBagExact(any(), any(), any()) }
        verify(exactly = 1) { stateMachine.clear(31L) }
    }

    @Test fun npcAttributeThresholdIsNotTreatedAsAnAutoCaptureNpcId() = runTest {
        val condition = tw.tsm.domain.data.EveCondition(1, 9, 14131, 1, 2, 24, 0, 1, 1, emptyList())
        executor.autoCaptureFromEvent(session, 31, tw.tsm.domain.data.NpcEventData(1, emptyList(), listOf(condition)))
        coVerify(exactly = 0) { pets.addFollowNpc(any(), any(), any(), any()) }
    }

    @Test fun tutorialPresenceConditionStillUsesItsNpcIdForAutoCapture() = runTest {
        val condition = tw.tsm.domain.data.EveCondition(1, 9, 0, 1, 6, 14131, 0, 1, 1, emptyList())
        coEvery { pets.addFollowNpc(any(), any(), any(), any()) } returns null
        executor.autoCaptureFromEvent(session, 31, tw.tsm.domain.data.NpcEventData(1, emptyList(), listOf(condition)))
        coVerify(exactly = 1) { pets.addFollowNpc(31L, 14131, any(), 1) }
    }

    @Test fun playerOperationGoldRewardDoesNotSendMoneyWhenCreditFails() = runTest {
        every { money.addGold(31L, 100) } returns false
        assertFalse(executor.executeResultClass(session, EveResult(0, 1, 7, 7, 0, 6, 100, 0)))
        verify(exactly = 0) { response.sendAddMoney(any(), any()) }
    }

    @Test fun unavailablePointPurchaseDoesNotReportSuccess() = runTest {
        assertFalse(executor.executeResultClass(session, EveResult(0, 1, 0, 7, 0, 3, 131171, 10)))
        coVerify(exactly = 0) { inventory.addItemsToBagExact(any(), any(), any()) }
    }

}
