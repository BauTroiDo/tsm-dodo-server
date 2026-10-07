package tw.tsm.tests

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
}
