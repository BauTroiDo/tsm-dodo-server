package tw.tsm.tests

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tw.tsm.app.handlers.UISellHandler
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.domain.interfaces.repositories.CharacterRepository
import tw.tsm.domain.interfaces.repositories.SkillRepository
import tw.tsm.gamelogic.systems.ExpSlotSystem
import tw.tsm.gamelogic.systems.FlyPointSystem
import tw.tsm.gamelogic.systems.InventorySystem
import tw.tsm.gamelogic.systems.PartySystem
import tw.tsm.gamelogic.systems.PlayerStateManager
import tw.tsm.gamelogic.systems.PointSystem
import tw.tsm.gamelogic.systems.RoleCountSystem
import tw.tsm.gamelogic.systems.SkillSystem
import tw.tsm.gamelogic.systems.SlotMachineSystem
import tw.tsm.gamelogic.systems.UISellSystem
import tw.tsm.gamelogic.systems.WorldManager

class ExpSlotUISellTest {
    private fun session(): GameSession = mockk(relaxed = true) {
        every { state } returns SessionState.IN_GAME
        every { roleId } returns 42
        every { characterName } returns "Tester"
    }

    private fun handler(response: ResponseSender, expSlot: ExpSlotSystem) = UISellHandler(
        responseSender = response,
        uiSellSystem = mockk<UISellSystem>(relaxed = true),
        pointSystem = mockk<PointSystem>(relaxed = true),
        inventorySystem = mockk<InventorySystem>(relaxed = true),
        flyPointSystem = mockk<FlyPointSystem>(relaxed = true),
        worldManager = mockk<WorldManager>(relaxed = true),
        characterRepository = mockk<CharacterRepository>(relaxed = true),
        partySystem = mockk<PartySystem>(relaxed = true),
        roleCountSystem = mockk<RoleCountSystem>(relaxed = true),
        skillRepository = mockk<SkillRepository>(relaxed = true),
        playerStateManager = mockk<PlayerStateManager>(relaxed = true),
        skillSystem = mockk<SkillSystem>(relaxed = true),
        slotMachineSystem = mockk<SlotMachineSystem>(relaxed = true),
        expSlotSystem = expSlot,
    )

    private fun queryData(sellId: Int): ByteArray = byteArrayOf(
        (sellId and 0xFF).toByte(), ((sellId ushr 8) and 0xFF).toByte(),
        1, 0,       // fIndex
        123, 0,     // npcId
    )

    @Test
    fun normalOneAndTenQueryExactCoinCounts() = runTest {
        val response = mockk<ResponseSender>(relaxed = true)
        val exp = mockk<ExpSlotSystem>()
        every { exp.validateTarget(42L, 37, listOf(1, 123)) } returns true
        every { exp.validateTarget(42L, 38, listOf(1, 123)) } returns true
        every { exp.requirement(37) } returns (ExpSlotSystem.NORMAL_COIN_ITEM_ID to 1)
        every { exp.requirement(38) } returns (ExpSlotSystem.NORMAL_COIN_ITEM_ID to 10)
        val handler = handler(response, exp)
        val session = session()

        handler.handle(session, 1, queryData(37))
        handler.handle(session, 1, queryData(38))

        verify(exactly = 1) {
            response.sendUiSellItemQuery(session, 37, ExpSlotSystem.NORMAL_COIN_ITEM_ID, 1)
        }
        verify(exactly = 1) {
            response.sendUiSellItemQuery(session, 38, ExpSlotSystem.NORMAL_COIN_ITEM_ID, 10)
        }
    }

    @Test
    fun turn2OneAndTenQueryExactGoldCoinCounts() = runTest {
        val response = mockk<ResponseSender>(relaxed = true)
        val exp = mockk<ExpSlotSystem>()
        every { exp.validateTarget(42L, 65, listOf(1, 123)) } returns true
        every { exp.validateTarget(42L, 66, listOf(1, 123)) } returns true
        every { exp.requirement(65) } returns (ExpSlotSystem.TURN2_COIN_ITEM_ID to 1)
        every { exp.requirement(66) } returns (ExpSlotSystem.TURN2_COIN_ITEM_ID to 10)
        val handler = handler(response, exp)
        val session = session()

        handler.handle(session, 1, queryData(65))
        handler.handle(session, 1, queryData(66))

        verify(exactly = 1) {
            response.sendUiSellItemQuery(session, 65, ExpSlotSystem.TURN2_COIN_ITEM_ID, 1)
        }
        verify(exactly = 1) {
            response.sendUiSellItemQuery(session, 66, ExpSlotSystem.TURN2_COIN_ITEM_ID, 10)
        }
    }
    @Test
    fun failedAtomicSpinNeverSendsDebitOrSuccess() = runTest {
        val response = mockk<ResponseSender>(relaxed = true)
        val exp = mockk<ExpSlotSystem>()
        every { exp.validateTarget(42, 37, listOf(1,123)) } returns true
        every { exp.requirement(37) } returns (31207 to 1)
        coEvery { exp.spin(42, 37, listOf(1,123), 7) } returns null
        val session = session()
        handler(response, exp).handle(session, 2, byteArrayOf(1,37,0,7,0,1,0,123,0))
        coVerify(exactly = 1) { exp.spin(42, 37, listOf(1,123), 7) }
        verify(exactly = 0) { response.sendReduceBagItem(any(), any(), any()); response.sendUiSellSuccess(any(), any()) }
    }

    @Test
    fun successfulAtomicSpinSendsExactAwardWireAfterDebit() = runTest {
        val response = mockk<ResponseSender>(relaxed = true)
        val exp = mockk<ExpSlotSystem>()
        every { exp.validateTarget(42, 37, listOf(1,123)) } returns true
        every { exp.requirement(37) } returns (31207 to 1)
        val pet = tw.tsm.domain.valueobjects.FollowNpcData(1,123,level=20,exp=1000)
        coEvery { exp.spin(42, 37, listOf(1,123), 7) } returns
            tw.tsm.gamelogic.systems.ExpSlotSpinResult(
                listOf(tw.tsm.gamelogic.systems.ExpSlotAward(0,1000,listOf(1,2,3))),
                tw.tsm.gamelogic.systems.PetSystem.PetExpGain(1,pet,pet.copy(exp=2000)),0,0)
        val session = session()
        handler(response, exp).handle(session, 2, byteArrayOf(1,37,0,7,0,1,0,123,0))
        verify(exactly=1) { response.sendReduceBagItem(session,7,1) }
        verify(exactly=1) { response.sendUiSellSuccess(session,37) }
        verify(exactly=1) { session.send(match { it.contentEquals(byteArrayOf(76,2,0,1,0,-24,3,0,0,1,2,3)) }) }
        verify(exactly=1) { session.send(match { it.contentEquals(byteArrayOf(76,5,0,0,0,0,0)) }) }
    }

    @Test
    fun oddTrailingPayloadAndWrongPaymentKindCannotSpin() = runTest {
        val response = mockk<ResponseSender>(relaxed = true)
        val exp = mockk<ExpSlotSystem>(relaxed = true)
        every { exp.validateTarget(42, 37, listOf(1,123)) } returns true
        every { exp.requirement(37) } returns (31207 to 1)
        val h = handler(response, exp)
        val session = session()
        h.handle(session,1,queryData(37)+byteArrayOf(99))
        h.handle(session,2,byteArrayOf(1,37,0,7,0,1,0,123,0,99))
        h.handle(session,2,byteArrayOf(2,37,0,1,0,123,0))
        coVerify(exactly=0) { exp.spin(any(),any(),any(),any()) }
        verify(exactly=0) { response.sendReduceBagItem(any(),any(),any()) }
    }

}
