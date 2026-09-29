package tw.tsm.tests

import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tw.tsm.app.handlers.UISellHandler
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.domain.interfaces.repositories.CharacterRepository
import tw.tsm.domain.interfaces.repositories.SkillRepository
import tw.tsm.domain.data.SlotMachineStateData
import tw.tsm.domain.valueobjects.ThingData
import tw.tsm.gamelogic.systems.*

class DvtSlotMachineUISellTest {
    private fun handler(
        response: ResponseSender,
        slot: SlotMachineSystem,
        inventory: InventorySystem = mockk(relaxed = true),
        points: PointSystem = mockk(relaxed = true),
    ) = UISellHandler(
        responseSender = response,
        uiSellSystem = mockk(relaxed = true),
        pointSystem = points,
        inventorySystem = inventory,
        flyPointSystem = mockk(relaxed = true),
        worldManager = mockk(relaxed = true),
        characterRepository = mockk<CharacterRepository>(relaxed = true),
        partySystem = mockk(relaxed = true),
        roleCountSystem = mockk(relaxed = true),
        skillRepository = mockk<SkillRepository>(relaxed = true),
        playerStateManager = mockk(relaxed = true),
        skillSystem = mockk(relaxed = true),
        slotMachineSystem = slot,
    )

    private fun session(): GameSession = mockk(relaxed = true) {
        every { state } returns SessionState.IN_GAME
        every { roleId } returns 42
        every { characterName } returns "Tester"
    }

    @Test
    fun `sell11 Point query is exactly 9 Point per coin`() = runTest {
        val response = mockk<ResponseSender>(relaxed = true)
        val slot = mockk<SlotMachineSystem>()
        coEvery { slot.getCoin(42L) } returns 10
        val handler = handler(response, slot)

        // C84-1: sellId=11, args=[0,3] => buy 3 coins by Point.
        handler.handle(
            session(),
            1,
            byteArrayOf(
                11, 0,
                0, 0,
                3, 0,
            )
        )

        verify(exactly = 1) {
            response.sendUiSellMoneyQuery(any(), 11, UISellSystem.CURRENCY_GOLD, 27L)
        }
    }

    @Test
    fun `sell11 item query requests exact 31171 amount`() = runTest {
        val response = mockk<ResponseSender>(relaxed = true)
        val slot = mockk<SlotMachineSystem>()
        coEvery { slot.getCoin(42L) } returns 100
        val handler = handler(response, slot)

        // C84-1: sellId=11, args=[5] => consume item31171 x5.
        handler.handle(session(), 1, byteArrayOf(11, 0, 5, 0))

        verify(exactly = 1) {
            response.sendUiSellItemQuery(any(), 11, UISellSystem.SLOT_COIN_ITEM_ID, 5)
        }
    }

    @Test
    fun `sell11 multi-stack item packet consumes exact UInt16 slots and deposits coins`() = runTest {
        val response = mockk<ResponseSender>(relaxed = true)
        val slot = mockk<SlotMachineSystem>()
        val inventory = mockk<InventorySystem>()
        val session = session()

        every { inventory.getBagItem(42L, 300) } returns ThingData(itemId = 31171, quantity = 2)
        every { inventory.getBagItem(42L, 301) } returns ThingData(itemId = 31171, quantity = 3)
        coEvery {
            inventory.removeBagItemsExact(42L, listOf(300 to 2, 301 to 3))
        } returns true

        coEvery { slot.getCoin(42L) } returns 10
        coEvery {
            slot.depositCoinWithPayment(42L, 5, any())
        } coAnswers {
            val payment = thirdArg<suspend () -> Boolean>()
            check(payment())
            SlotMachineStateData(coin = 15)
        }

        val handler = handler(response, slot, inventory)

        // C84-2 sell11 item route:
        // useKind=1, sellId=11, stackCount=2,
        // [slot300 qty2], [slot301 qty3], args=[5].
        handler.handle(
            session,
            2,
            byteArrayOf(
                1,
                11, 0,
                2,
                0x2C, 0x01, 2, 0,
                0x2D, 0x01, 3, 0,
                5, 0,
            )
        )

        coVerify(exactly = 1) {
            inventory.removeBagItemsExact(42L, listOf(300 to 2, 301 to 3))
        }
        verify(exactly = 1) { response.sendReduceBagItem(session, 300, 2) }
        verify(exactly = 1) { response.sendReduceBagItem(session, 301, 3) }
        verify(exactly = 1) { response.sendSlotMachineCoin(session, 15) }
        verify(exactly = 1) { response.sendUiSellSuccess(session, 11) }
    }

    @Test
    fun `sell11 Point use deducts and persists real Point before crediting coins`() = runTest {
        val response = mockk<ResponseSender>(relaxed = true)
        val slot = mockk<SlotMachineSystem>()
        val points = mockk<PointSystem>()
        val session = session()

        coEvery { slot.getCoin(42L) } returns 0
        every { points.subtractRealPoint(42L, 27) } returns true
        coEvery { points.savePoints(42L) } just Runs
        every { points.getPoints(42L) } returns (973 to 0)

        coEvery {
            slot.depositCoinWithPayment(42L, 3, any())
        } coAnswers {
            val payment = thirdArg<suspend () -> Boolean>()
            check(payment())
            SlotMachineStateData(coin = 3)
        }

        val handler = handler(response, slot, points = points)

        // C84-2 money route: useKind=2, sellId=11, args=[0,3].
        handler.handle(
            session,
            2,
            byteArrayOf(
                2,
                11, 0,
                0, 0,
                3, 0,
            )
        )

        verify(exactly = 1) { points.subtractRealPoint(42L, 27) }
        coVerify(exactly = 1) { points.savePoints(42L) }
        verify(exactly = 1) { response.sendPointBalance(session, 973, 0) }
        verify(exactly = 1) { response.sendSlotMachineCoin(session, 3) }
        verify(exactly = 1) { response.sendUiSellSuccess(session, 11) }
    }
}
