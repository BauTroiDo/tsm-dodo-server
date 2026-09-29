package tw.tsm.tests

import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.app.handlers.MallHandler
import tw.tsm.app.response.MallResponseSender
import tw.tsm.data.loaders.BlissBagDatLoader
import tw.tsm.data.loaders.GoodsSaleDatLoader
import tw.tsm.domain.data.DoroReward
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.domain.interfaces.SessionSender
import tw.tsm.domain.interfaces.repositories.CharacterCurrencyRepository
import tw.tsm.domain.valueobjects.ThingData
import tw.tsm.gamelogic.systems.DoroSystem
import tw.tsm.gamelogic.systems.InventorySystem
import tw.tsm.gamelogic.systems.MoneySystem
import tw.tsm.gamelogic.systems.PlayerCurrencySystem
import tw.tsm.gamelogic.systems.PointSystem
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path

class DvtDoroTest {
    private fun existing(vararg parts: String): Path {
        val direct = Path.of(parts.first(), *parts.drop(1).toTypedArray())
        if (Files.exists(direct)) return direct
        val parent = Path.of("..", *parts)
        check(Files.exists(parent)) { "Missing fixture: " + parts.joinToString("/") }
        return parent
    }

    private val goods by lazy {
        GoodsSaleDatLoader.load(
            Files.readAllBytes(existing("File.dat", "Data-ios", "IOS_GS_C.dat"))
        )
    }

    private val bliss by lazy {
        BlissBagDatLoader.load(
            Files.readAllBytes(existing("File.dat", "Data-ios", "IOS_BlissBag_C.dat"))
        )
    }

    @Test
    fun `Mobile DVT IOS_GS exposes all 32 Doro packages across seven tabs`() {
        val doro = goods.filter { it.storeKind == 5 }
        assertEquals(32, doro.size)
        assertEquals(setOf(1, 2, 3, 4, 5, 6, 7), doro.map { it.subKind }.toSet())
        assertTrue(doro.all { it.open && it.bitFunction == 3 })
    }

    @Test
    fun `DVT Hồn Doro packages keep exact price currency and draw count`() {
        data class Expected(val currency: Int, val draws: Int, val price: Int)
        val expected = mapOf(
            34623 to Expected(1, 1, 100),
            34624 to Expected(1, 10, 900),
            32992 to Expected(10, 1, 1),
            32993 to Expected(10, 10, 10),
            34167 to Expected(11, 1, 1),
            34168 to Expected(11, 10, 10),
        )

        for ((itemId, e) in expected) {
            val row = goods.single { it.storeKind == 5 && it.itemId == itemId }
            assertEquals(e.currency, row.currencyKind, "currency item=$itemId")
            assertEquals(e.draws, row.value, "draws item=$itemId")
            assertEquals(e.price, row.saleCredit, "price item=$itemId")
            assertEquals(1, row.subKind, "Hồn category item=$itemId")
            assertTrue(row.pageItemIndex > 0)
        }
    }

    @Test
    fun `DVT Limited Soul pool uses exact BlissBag 1 10 29 60 percent boundaries`() {
        val row = goods.single { it.storeKind == 5 && it.itemId == 34623 }
        val expected = listOf(
            1 to 34625,
            100 to 34625,
            101 to 34073,
            1100 to 34073,
            1101 to 32995,
            4000 to 32995,
            4001 to 32994,
            10000 to 32994,
        )

        for ((roll, itemId) in expected) {
            val system = DoroSystem(goods, bliss) { roll }
            val rewards = system.draw(row)
            assertNotNull(rewards)
            assertEquals(1, rewards!!.size)
            assertEquals(itemId, rewards.single().itemId, "roll=$roll")
            assertEquals(1, rewards.single().count)
        }
        assertEquals(10000, DoroSystem(goods, bliss).poolWeight(34623))
    }

    @Test
    fun `DVT ten draw package emits ten independent egg results`() {
        val row = goods.single { it.storeKind == 5 && it.itemId == 34624 }
        var nextRoll = 0
        val rolls = listOf(1, 101, 1101, 4001, 1, 101, 1101, 4001, 4001, 4001)
        val system = DoroSystem(goods, bliss) { rolls[nextRoll++] }

        val rewards = system.draw(row)
        assertNotNull(rewards)
        assertEquals(10, rewards!!.size)
        assertEquals(
            listOf(34625, 34073, 32995, 32994, 34625, 34073, 32995, 32994, 32994, 32994),
            rewards.map { it.itemId }
        )
    }

    @Test
    fun `Doro request lookup rejects client price and page index tampering`() {
        val row = goods.single { it.storeKind == 5 && it.itemId == 34623 }
        val system = DoroSystem(goods, bliss)

        assertNotNull(
            system.findPackage(
                row.storeKind, row.mainKind, row.subKind, row.pageItemIndex, row.itemId, row.saleCredit
            )
        )
        assertNull(
            system.findPackage(
                row.storeKind, row.mainKind, row.subKind, row.pageItemIndex, row.itemId, row.saleCredit - 1
            )
        )
        assertNull(
            system.findPackage(
                row.storeKind, row.mainKind, row.subKind, row.pageItemIndex + 1, row.itemId, row.saleCredit
            )
        )
    }

    @Test
    fun `S23-150 S66-3 and S35-50 match DVT wire exactly`() {
        val packets = mutableListOf<ByteArray>()
        val session = mockk<SessionSender>(relaxed = true) {
            every { send(capture(packets)) } just Runs
        }
        val sender = MallResponseSender()

        sender.sendDoroResult(
            session,
            listOf(
                DoroReward(32994, 1),
                DoroReward(34073, 2),
            )
        )
        sender.sendMallPurchaseResult(session, 34624, 1)
        sender.sendPlayerCurrency(session, 10, 123456)

        assertEquals(3, packets.size)

        ByteBuffer.wrap(packets[0]).order(ByteOrder.LITTLE_ENDIAN).apply {
            assertEquals(23, get().toInt() and 0xFF)
            assertEquals(150, get().toInt() and 0xFF)
            assertEquals(0, get().toInt() and 0xFF)
            assertEquals(2, short.toInt() and 0xFFFF)
            assertEquals(32994, short.toInt() and 0xFFFF)
            assertEquals(1, int)
            assertEquals(34073, short.toInt() and 0xFFFF)
            assertEquals(2, int)
            assertFalse(hasRemaining())
        }

        assertArrayEquals(
            byteArrayOf(66, 3, 0, 0x40, 0x87.toByte(), 1),
            packets[1]
        )

        ByteBuffer.wrap(packets[2]).order(ByteOrder.LITTLE_ENDIAN).apply {
            assertEquals(35, get().toInt() and 0xFF)
            assertEquals(50, get().toInt() and 0xFF)
            assertEquals(0, get().toInt() and 0xFF)
            assertEquals(10, get().toInt() and 0xFF)
            assertEquals(123456, int)
            assertFalse(hasRemaining())
        }
    }

    @Test
    fun `special DVT currency persists every mutation and blocks underflow`() = runTest {
        val repo = mockk<CharacterCurrencyRepository>()
        coEvery { repo.loadAll(42L) } returns mapOf(10 to 5, 11 to 2)
        coEvery { repo.setValue(any(), any(), any()) } just Runs

        val system = PlayerCurrencySystem(repo)
        assertEquals(mapOf(10 to 5, 11 to 2), system.load(42L))

        assertTrue(system.subtract(42L, 10, 3))
        assertEquals(2, system.get(42L, 10))
        coVerify(exactly = 1) { repo.setValue(42L, 10, 2) }

        assertFalse(system.subtract(42L, 11, 3))
        assertEquals(2, system.get(42L, 11))
        coVerify(exactly = 0) { repo.setValue(42L, 11, any()) }

        assertTrue(system.add(42L, 11, 4))
        assertEquals(6, system.get(42L, 11))
        coVerify(exactly = 1) { repo.setValue(42L, 11, 6) }
    }

    @Test
    fun `C66-1 Limited Soul one draw deducts 100 Point and returns Doro before success`() = runTest {
        val row = goods.single { it.storeKind == 5 && it.itemId == 34623 }
        val doro = DoroSystem(goods, bliss) { 1 } // top 1% => item 34625

        val inventory = mockk<InventorySystem>()
        val points = mockk<PointSystem>()
        val money = mockk<MoneySystem>(relaxed = true)
        val currencies = mockk<PlayerCurrencySystem>(relaxed = true)
        val response = mockk<ResponseSender>(relaxed = true)
        val session = mockk<GameSession>(relaxed = true)

        every { session.state } returns SessionState.IN_GAME
        every { session.roleId } returns 42
        every { session.characterName } returns "Tester"

        every { inventory.canAddRewardsToBag(42L, listOf(34625 to 1)) } returns true
        val thing = ThingData(itemId = 34625, quantity = 1)
        coEvery { inventory.addRewardsToBagExact(42L, listOf(34625 to 1)) } returns listOf(7 to thing)

        every { points.subtractRealPoint(42L, 100) } returns true
        coEvery { points.savePoints(42L) } just Runs
        every { points.getPoints(42L) } returns (900 to 0)

        val events = mutableListOf<String>()
        every { response.sendSetBagItem(session, 7, thing, showMsg = false) } answers {
            events += "bag"
        }
        every { response.sendPointBalance(session, 900, 0) } answers {
            events += "point"
        }
        every { response.sendDoroResult(session, listOf(DoroReward(34625, 1))) } answers {
            events += "doro"
        }
        every { response.sendMallPurchaseResult(session, 34623, 1) } answers {
            events += "success"
        }

        val handler = MallHandler(doro, inventory, points, money, currencies, response)
        val request = ByteBuffer.allocate(11).order(ByteOrder.LITTLE_ENDIAN)
            .put(row.storeKind.toByte())
            .put(row.mainKind.toByte())
            .put(row.subKind.toByte())
            .put(row.pageItemIndex.toByte())
            .putShort(row.itemId.toShort())
            .putShort(row.saleCredit.toShort())
            .putShort(1)
            .put(0)
            .array()

        handler.handle(session, 1, request)

        verify(exactly = 1) { points.subtractRealPoint(42L, 100) }
        coVerify(exactly = 1) { points.savePoints(42L) }
        coVerify(exactly = 1) { inventory.addRewardsToBagExact(42L, listOf(34625 to 1)) }
        assertEquals(listOf("bag", "point", "doro", "success"), events)
    }
}
