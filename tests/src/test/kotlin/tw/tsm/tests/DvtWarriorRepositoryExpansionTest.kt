package tw.tsm.tests

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tw.tsm.app.handlers.UISellHandler
import tw.tsm.domain.data.UISellDef
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.domain.interfaces.repositories.CharacterRepository
import tw.tsm.domain.interfaces.repositories.SkillRepository
import tw.tsm.domain.interfaces.repositories.UiSellCountRepository
import tw.tsm.gamelogic.systems.*

class DvtWarriorRepositoryExpansionTest {
    @Test
    fun `sell69 quotes 150 KNB without a Formula dat record`() = runTest {
        val roleCounts = mockk<RoleCountSystem>()
        every {
            roleCounts.getValue(42L, RoleCountSystem.WARRIOR_REPOSITORY)
        } returns 0

        val system = UISellSystem(
            uiSellDefs = mapOf(
                69 to UISellDef(
                    rolecountId = 0,
                    moneyStringId = 24901,
                    itemStringId = 0,
                    customStringId = 0
                )
            ),
            formulaDefs = emptyMap(),
            countRepo = mockk<UiSellCountRepository>(relaxed = true),
            inventorySystem = mockk(relaxed = true),
            roleCountSystem = roleCounts,
        )

        val quote = system.query(42L, 69)
        assertTrue(quote is UiSellQuote.Money)
        quote as UiSellQuote.Money
        assertEquals(UISellSystem.CURRENCY_GOLD, quote.currencyKind)
        assertEquals(150L, quote.cost)
        assertEquals(
            RoleCountSystem.WARRIOR_REPOSITORY,
            system.expansionRoleCountIndex(69)
        )
    }

    @Test
    fun `sell69 refuses expansion after all 145 extra slots are purchased`() = runTest {
        val roleCounts = mockk<RoleCountSystem>()
        every {
            roleCounts.getValue(42L, RoleCountSystem.WARRIOR_REPOSITORY)
        } returns RoleCountSystem.WARRIOR_REPOSITORY_MAX_EXPANSIONS

        val system = UISellSystem(
            uiSellDefs = mapOf(
                69 to UISellDef(
                    rolecountId = 0,
                    moneyStringId = 24901,
                    itemStringId = 0,
                    customStringId = 0
                )
            ),
            formulaDefs = emptyMap(),
            countRepo = mockk<UiSellCountRepository>(relaxed = true),
            inventorySystem = mockk(relaxed = true),
            roleCountSystem = roleCounts,
        )

        val quote = system.query(42L, 69)
        assertTrue(quote is UiSellQuote.Fail)
        assertEquals(UISellSystem.REASON_LIMIT_REACHED, (quote as UiSellQuote.Fail).reason)
    }

    @Test
    fun `sell69 purchase deducts 150 KNB and sends RoleCount822 update to six slots`() = runTest {
        val response = mockk<ResponseSender>(relaxed = true)
        val uiSell = mockk<UISellSystem>()
        val points = mockk<PointSystem>()
        val roleCounts = mockk<RoleCountSystem>(relaxed = true)
        val session = mockk<GameSession>(relaxed = true) {
            every { state } returns SessionState.IN_GAME
            every { roleId } returns 42
            every { characterName } returns "Tester"
        }

        coEvery { uiSell.query(42L, 69) } returns
            UiSellQuote.Money(UISellSystem.CURRENCY_GOLD, 150L)
        every { uiSell.expansionRoleCountIndex(69) } returns
            RoleCountSystem.WARRIOR_REPOSITORY
        every { points.subtractRealPoint(42L, 150) } returns true
        every { points.getPoints(42L) } returns (850 to 0)
        every {
            roleCounts.increment(42L, RoleCountSystem.WARRIOR_REPOSITORY)
        } returns 1

        val handler = UISellHandler(
            responseSender = response,
            uiSellSystem = uiSell,
            pointSystem = points,
            inventorySystem = mockk(relaxed = true),
            flyPointSystem = mockk(relaxed = true),
            worldManager = mockk(relaxed = true),
            characterRepository = mockk<CharacterRepository>(relaxed = true),
            partySystem = mockk(relaxed = true),
            roleCountSystem = roleCounts,
            skillRepository = mockk<SkillRepository>(relaxed = true),
            playerStateManager = mockk(relaxed = true),
            skillSystem = mockk(relaxed = true),
            slotMachineSystem = mockk(relaxed = true),
        )

        // C84-2: useKind=2 (money), sellId=69, no args.
        handler.handle(session, 2, byteArrayOf(2, 69, 0))

        verify(exactly = 1) { points.subtractRealPoint(42L, 150) }
        verify(exactly = 1) { response.sendPointBalance(session, 850, 0) }
        verify(exactly = 1) {
            roleCounts.setValue(
                42L,
                RoleCountSystem.WARRIOR_REPOSITORY,
                1,
                RoleCountSystem.WARRIOR_REPOSITORY_MAX_EXPANSIONS
            )
        }
        verify(exactly = 1) { response.sendUiSellSuccess(session, 69) }
        verify(exactly = 1) {
            response.sendRoleCountUpdate(
                session,
                mapOf(
                    RoleCountSystem.WARRIOR_REPOSITORY to
                        (1 to RoleCountSystem.WARRIOR_REPOSITORY_MAX_EXPANSIONS)
                )
            )
        }
    }
}
