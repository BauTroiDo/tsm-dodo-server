package tw.tsm.tests

import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.data.loaders.GoodsSaleDatLoader
import tw.tsm.domain.interfaces.repositories.MallQuotaRepository
import tw.tsm.domain.interfaces.repositories.MallQuotaRecord
import tw.tsm.gamelogic.systems.MallQuotaSystem
import tw.tsm.gamelogic.systems.RoleCountSystem
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate

/** Exhaustive GS_C catalog census and real server-side limit/reset semantics. */
class MallGs206CatalogQuotaRegressionTest {
    private val offers by lazy {
        GoodsSaleDatLoader.load(Files.readAllBytes(
            Path.of("..", "File.dat", "Data-ios", "IOS_GS_C.dat")
        ))
    }

    @Test
    fun `all 206 PC sale records are classifiable without treating IAP as free goods`() {
        assertEquals(206, offers.size)
        assertEquals(mapOf(1 to 138, 2 to 32, 4 to 4, 5 to 32),
            offers.groupingBy { it.storeKind }.eachCount())
        assertTrue(offers.filter { it.storeKind == 2 || it.storeKind == 4 }
            .all { it.currencyKind == 0 })
        assertTrue(offers.filter { it.storeKind == 1 }
            .all { it.saleKind == 0 || it.saleKind == 1 })
        assertEquals(18, offers.count { it.storeKind == 1 && it.saleKind == 1 })
        assertTrue(offers.filter { it.storeKind == 1 && it.saleKind == 1 }
            .all { it.flag > 0 && it.dayCount > 0 && it.timeKind == 0 &&
                it.buyLimitFlag == 0 && it.specialEvent == 0L })
        assertEquals(2, offers.count { it.storeKind == 5 && it.saleKind == 1 })
        assertEquals(36, offers.count { it.storeKind in setOf(2, 4) })
    }

    @Test
    fun `DVT sale kinds use daily weekly monthly and lifetime boundaries`() {
        val repo = mockk<MallQuotaRepository>()
        val counts = mockk<RoleCountSystem>()
        val system = MallQuotaSystem(repo, counts, offers) { LocalDate.of(2026, 10, 10) }
        assertEquals(20261010, system.period(1))
        assertEquals(202610, system.period(3))
        assertEquals(-1, system.period(4))
        assertEquals(system.period(2, LocalDate.of(2026, 10, 5)),
            system.period(2, LocalDate.of(2026, 10, 11)))
        assertNotEquals(system.period(2, LocalDate.of(2026, 10, 11)),
            system.period(2, LocalDate.of(2026, 10, 12)))
    }

    @Test
    fun `limited shop request is persisted before balance deduction and cannot exceed quota`() = runBlocking {
        val offer = offers.single { it.storeKind == 1 && it.itemId == 34194 && it.saleKind == 1 }
        assertEquals(10, offer.dayCount)
        val repo = mockk<MallQuotaRepository>()
        val counts = mockk<RoleCountSystem>()
        coEvery { repo.load(41L, offer.flag) } returns null
        every { counts.getValue(41L, offer.flag) } returns 0
        val rows = mutableListOf<MallQuotaRecord>()
        coEvery { repo.save(41L, capture(rows)) } just Runs
        val system = MallQuotaSystem(repo, counts, offers) { LocalDate.of(2026, 10, 10) }
        val reservation = system.reserve(41L, offer, 10)
        assertNotNull(reservation)
        assertEquals(MallQuotaRecord(offer.flag, 20261010, 10), rows.single())
        assertNull(system.reserve(41L, offer, 11))
        coEvery { repo.remove(41L, offer.flag) } just Runs
        system.cancel(reservation!!)
        coVerify { repo.remove(41L, offer.flag) }
    }

    @Test
    fun `next-day login resets expired client counter before S85 snapshot`() = runBlocking {
        val offer = offers.single { it.storeKind == 1 && it.itemId == 34194 && it.saleKind == 1 }
        val repo = mockk<MallQuotaRepository>()
        val counts = mockk<RoleCountSystem>()
        coEvery { repo.load(41L, any()) } answers {
            val flag = secondArg<Int>()
            if (flag == offer.flag) MallQuotaRecord(flag, 20261009, 10) else null
        }
        every { counts.getValue(41L, offer.flag) } returns 10
        every { counts.getMaxValue(41L, offer.flag) } returns 0
        every { counts.setValue(41L, offer.flag, 0, 0) } just Runs
        val system = MallQuotaSystem(repo, counts, offers) { LocalDate.of(2026, 10, 10) }
        system.refreshOnLogin(41L)
        verify(exactly = 1) { counts.setValue(41L, offer.flag, 0, 0) }
    }

    @Test
    fun `permanent quota never expires even across calendar boundaries`() = runBlocking {
        val offer = offers.first { it.saleKind == 4 }
        val repo = mockk<MallQuotaRepository>()
        val counts = mockk<RoleCountSystem>()
        coEvery { repo.load(41L, offer.flag) } returns MallQuotaRecord(offer.flag, -1, offer.dayCount)
        val system = MallQuotaSystem(repo, counts, offers) { LocalDate.of(2030, 1, 1) }
        assertNull(system.reserve(41L, offer, 1))
        coVerify(exactly = 0) { repo.save(any(), any()) }
    }

    @Test
    fun `quota commit synchronizes RoleCount and persisted client snapshot`() = runBlocking {
        val offer = offers.first { it.storeKind == 1 && it.saleKind == 1 }
        val repo = mockk<MallQuotaRepository>()
        val counts = mockk<RoleCountSystem>()
        coEvery { repo.load(41L, offer.flag) } returns null
        every { counts.getValue(41L, offer.flag) } returns 0
        every { counts.getMaxValue(41L, offer.flag) } returns 0
        every { counts.setValue(41L, offer.flag, 1, 0) } just Runs
        coEvery { repo.save(any(), any()) } just Runs
        coEvery { counts.saveCounts(41L) } just Runs
        val system = MallQuotaSystem(repo, counts, offers) { LocalDate.of(2026, 10, 10) }
        val reservation = system.reserve(41L, offer, 1)
        assertNotNull(reservation)
        system.complete(reservation!!)
        verify(exactly = 1) { counts.setValue(41L, offer.flag, 1, 0) }
        coVerify(exactly = 1) { counts.saveCounts(41L) }
    }
}
