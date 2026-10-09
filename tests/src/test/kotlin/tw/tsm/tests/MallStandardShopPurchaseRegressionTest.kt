package tw.tsm.tests

import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.app.handlers.MallHandler
import tw.tsm.data.loaders.GoodsSaleDatLoader
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.gamelogic.systems.DoroSystem
import tw.tsm.gamelogic.systems.InventorySystem
import tw.tsm.gamelogic.systems.MoneySystem
import tw.tsm.gamelogic.systems.PlayerCurrencySystem
import tw.tsm.gamelogic.systems.PointSystem
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path

/** PC DVT Tiệm C66-1: ordinary store 1 must not be refused as non-Doro. */
class MallStandardShopPurchaseRegressionTest {
    private val offers by lazy {
        GoodsSaleDatLoader.load(
            Files.readAllBytes(Path.of("..", "File.dat", "Data-ios", "IOS_GS_C.dat"))
        )
    }

    private fun packet(
        store: Int = 1,
        item: Int = 34695,
        price: Int = 19,
        count: Int = 1,
        index: Int = 2,
    ): ByteArray = ByteBuffer.allocate(11).order(ByteOrder.LITTLE_ENDIAN)
        .put(store.toByte()).put(1).put(3).put(index.toByte())
        .putShort(item.toShort()).putShort(price.toShort())
        .putShort(count.toShort()).put(0).array()

    private class Fixture(val defs: List<tw.tsm.domain.data.GoodsSaleDef>) {
        val session = mockk<GameSession>(relaxed = true)
        val sender = mockk<ResponseSender>(relaxed = true)
        val inventory = mockk<InventorySystem>(relaxed = true)
        val points = mockk<PointSystem>(relaxed = true)
        val money = mockk<MoneySystem>(relaxed = true)
        val currency = mockk<PlayerCurrencySystem>(relaxed = true)
        val doro = mockk<DoroSystem>(relaxed = true)
        val handler = MallHandler(doro, inventory, points, money, currency, sender, defs)
        init {
            every { session.state } returns SessionState.IN_GAME
            every { session.roleId } returns 41
            every { inventory.canAddRewardsToBag(41L, listOf(34695 to 1)) } returns true
            every { points.getPoints(41L) } returns (100 to 0)
            every { points.subtractRealPoint(41L, 19) } returns true
            coEvery { points.savePoints(41L) } just Runs
            coEvery { inventory.addRewardsToBagExact(41L, listOf(34695 to 1)) } returns emptyList()
        }
    }

    @Test
    fun `PC GS has standard elf bags priced nineteen points`() {
        val offer = offers.first { it.storeKind == 1 && it.itemId == 34695 && it.saleCredit == 19 }
        assertEquals(1, offer.currencyKind)
        assertEquals(1, offer.mainKind)
        assertEquals(3, offer.subKind)
        assertEquals(2, offer.pageItemIndex)
        assertEquals(1, offer.value)
        assertTrue(offer.open)
        assertEquals(0, offer.saleKind)
    }

    @Test
    fun `buying an elf gift bag charges points and grants exact item`() = runBlocking {
        val f = Fixture(offers)
        f.handler.handle(f.session, 1, packet())
        verify(exactly = 1) { f.points.subtractRealPoint(41L, 19) }
        coVerify(exactly = 1) { f.points.savePoints(41L) }
        coVerify(exactly = 1) { f.inventory.addRewardsToBagExact(41L, listOf(34695 to 1)) }
        verify(exactly = 1) { f.sender.sendMallPurchaseResult(f.session, 34695, 1) }
    }

    @Test
    fun `forged lower price cannot purchase or charge balance`() = runBlocking {
        val f = Fixture(offers)
        f.handler.handle(f.session, 1, packet(price = 1))
        verify(exactly = 0) { f.points.subtractRealPoint(any(), any()) }
        coVerify(exactly = 0) { f.inventory.addRewardsToBagExact(any(), any()) }
        verify(exactly = 1) { f.sender.sendMallPurchaseResult(f.session, 34695, 2) }
    }

    @Test
    fun `forged page index cannot purchase or charge balance`() = runBlocking {
        val f = Fixture(offers)
        f.handler.handle(f.session, 1, packet(index = 1))
        verify(exactly = 0) { f.points.subtractRealPoint(any(), any()) }
        verify(exactly = 1) { f.sender.sendMallPurchaseResult(f.session, 34695, 2) }
    }

    @Test
    fun `insufficient points grants nothing and returns failure`() = runBlocking {
        val f = Fixture(offers)
        every { f.points.subtractRealPoint(41L, 19) } returns false
        f.handler.handle(f.session, 1, packet())
        coVerify(exactly = 0) { f.inventory.addRewardsToBagExact(any(), any()) }
        verify(exactly = 1) { f.sender.sendMallPurchaseResult(f.session, 34695, 2) }
    }

    @Test
    fun `unimplemented limited goods never bypass counters`() = runBlocking {
        val limited = offers.first { it.storeKind == 1 && it.saleKind != 0 && it.open }
        val f = Fixture(offers)
        val req = ByteBuffer.allocate(11).order(ByteOrder.LITTLE_ENDIAN)
            .put(1).put(limited.mainKind.toByte()).put(limited.subKind.toByte())
            .put(limited.pageItemIndex.toByte())
            .putShort(limited.itemId.toShort()).putShort(limited.saleCredit.toShort())
            .putShort(1).put(0).array()
        f.handler.handle(f.session, 1, req)
        verify(exactly = 0) { f.points.subtractRealPoint(any(), any()) }
        verify(exactly = 1) { f.sender.sendMallPurchaseResult(f.session, limited.itemId, 2) }
    }
}
