package tw.tsm.tests

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tw.tsm.domain.constants.EItemKind
import tw.tsm.domain.data.ItemDef
import tw.tsm.domain.valueobjects.MythAttribute
import tw.tsm.domain.valueobjects.ThingData
import tw.tsm.gamelogic.systems.InventorySystem
import tw.tsm.gamelogic.systems.MythEnchantResult
import tw.tsm.gamelogic.systems.MythEnchantSystem
import tw.tsm.gamelogic.systems.PlayerCurrencySystem

class MythEnchantD7RegressionTest {
    private fun itemDef(
        kind: Int,
        threshold: Int,
        attr1Kind: Int = 0,
        minValue: Int = 0,
        maxValue: Int = 0
    ): ItemDef = mockk<ItemDef> {
        every { this@mockk.kind } returns kind
        every { this@mockk.threshold } returns threshold
        every { this@mockk.attr1Kind } returns attr1Kind
        every { this@mockk.attr1Value } returns minValue
        every { this@mockk.attr2Value } returns maxValue
    }

    private fun mythThing() = ThingData(
        itemId = 50001,
        quantity = 1,
        mythLevel = 5,
        myth = listOf(
            MythAttribute(101, 12, 1),
            MythAttribute(102, 10, 2),
            MythAttribute(105, 4, 0),
            MythAttribute(106, 4, 0)
        )
    )

    private fun runeThing(quantity: Int = 2) = ThingData(
        itemId = 55265,
        quantity = quantity
    )

    @Test
    fun enchantSuccessReplacesExtraConsumesRuneAndTenTs() = runBlocking {
        val inventory = mockk<InventorySystem>()
        val currency = mockk<PlayerCurrencySystem>()
        every { inventory.getBagItem(1L, 3) } returns mythThing()
        every { inventory.getBagItem(1L, 7) } returns runeThing(2)
        every { currency.get(1L, 8) } returnsMany listOf(100, 90)
        coEvery { currency.subtract(1L, 8, 10) } returns true

        val changes = slot<Map<Int, ThingData?>>()
        coEvery { inventory.setBagItemsAtomic(1L, capture(changes)) } returns Unit

        val system = MythEnchantSystem(
            inventorySystem = inventory,
            currencySystem = currency,
            itemDefs = mapOf(
                50001 to itemDef(EItemKind.MYTH, threshold = 6),
                55265 to itemDef(EItemKind.ENCHANT, threshold = 5, attr1Kind = 101, minValue = 4, maxValue = 6)
            ),
            rollInclusive = { _, high -> high }
        )

        val result = system.enchant(1L, 3, 7)

        assertTrue(result.success)
        assertEquals(1, result.code)
        assertEquals(90, result.tsBalance)
        assertEquals(6, result.updatedMyth!!.myth[0].extra)
        assertEquals(12, result.updatedMyth!!.myth[0].value)
        assertEquals(2, result.updatedMyth!!.myth[1].extra)
        assertEquals(1, result.runeRemaining!!.quantity)

        assertEquals(6, changes.captured[3]!!.myth[0].extra)
        assertEquals(1, changes.captured[7]!!.quantity)
        coVerify(exactly = 1) { currency.subtract(1L, 8, 10) }
        coVerify(exactly = 1) { inventory.setBagItemsAtomic(1L, any()) }
    }

    @Test
    fun singleRuneIsRemovedOnSuccess() = runBlocking {
        val inventory = mockk<InventorySystem>()
        val currency = mockk<PlayerCurrencySystem>()
        every { inventory.getBagItem(1L, 1) } returns mythThing()
        every { inventory.getBagItem(1L, 2) } returns runeThing(1)
        every { currency.get(1L, 8) } returnsMany listOf(10, 0)
        coEvery { currency.subtract(1L, 8, 10) } returns true
        val changes = slot<Map<Int, ThingData?>>()
        coEvery { inventory.setBagItemsAtomic(1L, capture(changes)) } returns Unit

        val system = MythEnchantSystem(
            inventory, currency,
            mapOf(
                50001 to itemDef(EItemKind.MYTH, 6),
                55265 to itemDef(EItemKind.ENCHANT, 5, 101, 4, 6)
            ),
            rollInclusive = { low, _ -> low }
        )

        val result = system.enchant(1L, 1, 2)
        assertTrue(result.success)
        assertNull(result.runeRemaining)
        assertNull(changes.captured[2])
        assertEquals(4, changes.captured[1]!!.myth[0].extra)
    }

    @Test
    fun validationCodesMatchDvtAndNeverSpend() = runBlocking {
        val inventory = mockk<InventorySystem>()
        val currency = mockk<PlayerCurrencySystem>()
        every { inventory.getBagItem(1L, 1) } returns mythThing()
        every { inventory.getBagItem(1L, 2) } returns runeThing()
        every { currency.get(1L, 8) } returns 100
        coEvery { currency.subtract(any(), any(), any()) } returns true

        fun system(runeDef: ItemDef) = MythEnchantSystem(
            inventory, currency,
            mapOf(50001 to itemDef(EItemKind.MYTH, 4), 55265 to runeDef),
            rollInclusive = { low, _ -> low }
        )

        val starTooHigh = system(itemDef(EItemKind.ENCHANT, 5, 101, 1, 2)).enchant(1L, 1, 2)
        assertEquals(MythEnchantResult.RESULT_STAR_TOO_HIGH, starTooHigh.code)

        val noMatch = system(itemDef(EItemKind.ENCHANT, 4, 103, 1, 2)).enchant(1L, 1, 2)
        assertEquals(MythEnchantResult.RESULT_NO_MATCHING_ATTRIBUTE, noMatch.code)

        val lowCurrency = mockk<PlayerCurrencySystem>()
        every { lowCurrency.get(1L, 8) } returns 9
        coEvery { lowCurrency.subtract(any(), any(), any()) } returns true
        val poor = MythEnchantSystem(
            inventory, lowCurrency,
            mapOf(
                50001 to itemDef(EItemKind.MYTH, 4),
                55265 to itemDef(EItemKind.ENCHANT, 4, 101, 1, 2)
            )
        ).enchant(1L, 1, 2)
        assertEquals(MythEnchantResult.RESULT_NOT_ENOUGH_TS, poor.code)

        coVerify(exactly = 0) { inventory.setBagItemsAtomic(any(), any()) }
        coVerify(exactly = 0) { currency.subtract(any(), any(), any()) }
        coVerify(exactly = 0) { lowCurrency.subtract(any(), any(), any()) }
    }

    @Test
    fun inventoryFailureRefundsTs() = runBlocking {
        val inventory = mockk<InventorySystem>()
        val currency = mockk<PlayerCurrencySystem>()
        every { inventory.getBagItem(1L, 1) } returns mythThing()
        every { inventory.getBagItem(1L, 2) } returns runeThing()
        every { currency.get(1L, 8) } returns 20
        coEvery { currency.subtract(1L, 8, 10) } returns true
        coEvery { currency.add(1L, 8, 10) } returns true
        coEvery { inventory.setBagItemsAtomic(1L, any()) } throws IllegalStateException("db fail")

        val system = MythEnchantSystem(
            inventory, currency,
            mapOf(
                50001 to itemDef(EItemKind.MYTH, 5),
                55265 to itemDef(EItemKind.ENCHANT, 5, 101, 1, 2)
            )
        )

        try {
            system.enchant(1L, 1, 2)
        } catch (_: IllegalStateException) {
            // expected
        }

        coVerify(exactly = 1) { currency.subtract(1L, 8, 10) }
        coVerify(exactly = 1) { currency.add(1L, 8, 10) }
    }
}
