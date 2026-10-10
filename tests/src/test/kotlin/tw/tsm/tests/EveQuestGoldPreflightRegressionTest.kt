package tw.tsm.tests

import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.domain.data.EveResult
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.gamelogic.systems.*

class EveQuestGoldPreflightRegressionTest {
    private fun result(cls: Int, style: Int, amount: Int, no: Int=1, type: Int=0): EveResult =
        EveResult(0, no, type, cls, 0, style, amount, 0)

    @Test fun rejectsMoneySubtractionWithInsufficientBalance() {
        val chain=listOf(result(2,1,1),result(5,2,100,no=2))
        assertEquals(EveQuestGoldPreflightPolicy.Failure.INSUFFICIENT_GOLD,
            EveQuestGoldPreflightPolicy.validate(chain, 99)?.failure)
    }

    @Test fun projectsOrderedMoneyDeltasAcrossOneSelectedChain() {
        val chain=listOf(result(5,1,100),result(5,2,80,no=2),result(5,2,20,no=3))
        assertNull(EveQuestGoldPreflightPolicy.validate(chain,0))
        assertEquals(EveQuestGoldPreflightPolicy.Failure.INSUFFICIENT_GOLD,
            EveQuestGoldPreflightPolicy.validate(
                listOf(result(5,2,80),result(5,1,100,no=2)),0
            )?.failure)
    }

    @Test fun rejectsMoneyLimitBeforeGivingAnyMissionOrItemReward() {
        val chain=listOf(result(2,1,1),result(5,1,100,no=2))
        assertEquals(EveQuestGoldPreflightPolicy.Failure.GOLD_LIMIT,
            EveQuestGoldPreflightPolicy.validate(chain,999_999_950)?.failure)
    }

    @Test fun checksClassSevenGoldRewardsUsingSameMoneyLimit() {
        assertEquals(EveQuestGoldPreflightPolicy.Failure.GOLD_LIMIT,
            EveQuestGoldPreflightPolicy.validate(
                listOf(result(7,6,50)),999_999_970
            )?.failure)
        assertNull(EveQuestGoldPreflightPolicy.validate(
            listOf(result(7,6,50)),999_999_900
        ))
    }

    @Test fun rejectsUnsupportedOrMalformedMoneyOperations() {
        assertEquals(EveQuestGoldPreflightPolicy.Failure.INVALID_OPERATION,
            EveQuestGoldPreflightPolicy.validate(listOf(result(5,3,10)),100)?.failure)
        assertEquals(EveQuestGoldPreflightPolicy.Failure.INVALID_OPERATION,
            EveQuestGoldPreflightPolicy.validate(listOf(result(5,2,0)),100)?.failure)
    }

    @Test fun defersUnselectedMoneyEffectsBeyondChoiceOrBattle() {
        for (barrier in listOf(3,6)) {
            val chain=listOf(result(5,1,10),
                result(0,0,0,no=2,type=barrier),
                result(5,2,1000,no=3))
            assertNull(EveQuestGoldPreflightPolicy.validate(chain,10))
        }
    }

    @Test fun preventsItemRemovalAndQuestProgressBeforeLaterMoneyFailure() = runTest {
        val response=mockk<ResponseSender>(relaxed=true)
        val inventory=mockk<InventorySystem>(relaxed=true)
        val quests=mockk<QuestSystem>(relaxed=true)
        val money=mockk<MoneySystem>(relaxed=true)
        val session=mockk<GameSession>(relaxed=true) {
            every { roleId } returns 31
            every { characterName } returns "QuestTester"
            every { mapId } returns 12001
        }
        every { money.getGold(31L) } returns 50
        coEvery { quests.getMissions(31L) } returns emptyList()
        val executor=EveResultExecutor(
            response,quests,inventory,mockk(relaxed=true),mockk(relaxed=true),
            money,emptyMap(),emptyMap(),emptyMap(),emptyMap(),
            mockk(relaxed=true),mockk(relaxed=true),mockk(relaxed=true),
            mockk(relaxed=true),
        )
        val submit=EveResult(0,1,0,1,29124,2,1,0)
        val addMission=EveResult(0,2,0,2,12280,1,1,0)
        val charge=EveResult(0,3,0,5,0,2,100,0)
        val stateMachine=mockk<EveEventStateMachine>(relaxed=true)
        every { stateMachine.getSession(31L) } returns EventSession(
            12001,1,1,results=listOf(submit,addMission,charge),currentIndex=1,
        )
        val flow=EveFlowController(
            response,stateMachine,mockk(relaxed=true),mockk(relaxed=true),
            executor,mockk(relaxed=true),mockk(relaxed=true),mockk(relaxed=true),
            mockk(relaxed=true),mockk(relaxed=true),emptyMap(),mockk(relaxed=true),
        )
        flow.startEvent(session,submit)
        verify(exactly=1) { stateMachine.clear(31L) }
        verify(exactly=0) { stateMachine.advance(any()) }
        verify(exactly=0) { money.subtractGold(any(),any()) }
        coVerify(exactly=0) { inventory.removeBagItemsExact(any(),any()) }
        coVerify(exactly=0) { quests.addMissionStep(any(),any(),any()) }
    }
}
