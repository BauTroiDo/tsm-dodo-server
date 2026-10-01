package tw.tsm.tests

import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.domain.constants.DispatchStartResult
import tw.tsm.domain.data.DispatchBonusDef
import tw.tsm.domain.data.DispatchDailyUsage
import tw.tsm.domain.data.DispatchDef
import tw.tsm.domain.data.DispatchSaveData
import tw.tsm.domain.data.UISellDef
import tw.tsm.domain.data.UISellFormulaDef
import tw.tsm.domain.interfaces.repositories.CharacterRepository
import tw.tsm.domain.interfaces.repositories.DispatchRepository
import tw.tsm.domain.interfaces.repositories.InventoryRepository
import tw.tsm.domain.interfaces.repositories.RoleCountRepository
import tw.tsm.domain.interfaces.repositories.UiSellCountRepository
import tw.tsm.domain.valueobjects.FollowNpcData
import tw.tsm.gamelogic.systems.*

class DispatchPricingTest {
    private class Repo : DispatchRepository {
        val rows = mutableListOf<DispatchSaveData>()
        var daily: DispatchDailyUsage? = null

        override suspend fun findByCharacterId(characterId: Long) = rows.toList()
        override suspend fun insert(characterId: Long, data: DispatchSaveData) {
            rows.removeAll { it.slotIndex == data.slotIndex }
            rows += data
        }
        override suspend fun deleteBySlot(characterId: Long, slotIndex: Int) {
            rows.removeAll { it.slotIndex == slotIndex }
        }
        override suspend fun getDailyUsage(characterId: Long) = daily
        override suspend fun saveDailyUsage(characterId: Long, usage: DispatchDailyUsage) {
            daily = usage
        }

        fun clearActive() {
            rows.removeAll { it.slotIndex in 1..DispatchSystem.MAX_SLIDER_COUNT }
        }
    }

    private data class Fixture(
        val repo: Repo,
        val system: DispatchSystem,
        val money: MoneySystem,
        val points: PointSystem,
        val roleCounts: RoleCountSystem,
    )

    private suspend fun fixture(
        gold: Int = 50_000,
        point: Int = 500,
    ): Fixture {
        val inventoryRepo = mockk<InventoryRepository>(relaxed = true)
        coEvery { inventoryRepo.loadMoney(31L) } returns (gold to 0)
        val money = MoneySystem(inventoryRepo)
        money.loadMoney(31L)

        val characterRepo = mockk<CharacterRepository>(relaxed = true)
        coEvery { characterRepo.loadPoints(31L) } returns (point to 0)
        val points = PointSystem(characterRepo)
        points.loadPoints(31L)

        val roleRepo = mockk<RoleCountRepository>(relaxed = true)
        coEvery { roleRepo.loadAll(31L) } returns emptyMap()
        val roleCounts = RoleCountSystem(roleRepo)
        roleCounts.loadCounts(31L)

        val pets = mockk<PetSystem>()
        val pet = FollowNpcData(
            slotIndex = 1,
            npcId = 12020,
            name = "",
            level = 1,
            canGrow = 1,
        )
        every { pets.getInnNpc(31L, any()) } returns pet

        val repo = Repo()
        val defs = mapOf(
            1 to (1..5).associateWith { kind ->
                listOf(DispatchDef(0, kind, 2, 70_000))
            }
        )
        val system = DispatchSystem(
            repo,
            pets,
            defs,
            emptyMap<Int, DispatchBonusDef>(),
            emptyMap(),
            emptyMap(),
            mockk(relaxed = true),
            money,
            points,
            roleCounts,
        )
        return Fixture(repo, system, money, points, roleCounts)
    }

    @Test
    fun firstFourFreeNextFourCostGoldThenFiftyPoint() = runTest {
        val f = fixture()
        val day = 20261001

        repeat(4) { i ->
            val (result, data) = f.system.startDispatch(
                31L, 1, 45000.0 + i, dayKey = day
            )
            assertEquals(DispatchStartResult.SUCCESS, result)
            assertNotNull(data)
            if (i == 0) {
                assertEquals(
                    4.0 / 24.0,
                    data!!.endTime - data.startTime,
                    1e-9,
                )
            }
            f.repo.clearActive()
        }
        assertEquals(50_000, f.money.getGold(31L))
        assertEquals(500, f.points.getRealPoint(31L))
        assertEquals(4, f.repo.daily!!.freeUsed)
        assertEquals(0, f.repo.daily!!.paidUsed)

        repeat(4) { i ->
            val result = f.system.startDispatch(
                31L, 1, 45100.0 + i, dayKey = day
            ).first
            assertEquals(DispatchStartResult.SUCCESS, result)
            f.repo.clearActive()
        }
        assertEquals(30_000, f.money.getGold(31L))
        assertEquals(500, f.points.getRealPoint(31L))
        assertEquals(4, f.repo.daily!!.paidUsed)

        assertEquals(
            DispatchStartResult.SUCCESS,
            f.system.startDispatch(31L, 1, 45200.0, dayKey = day).first,
        )
        assertEquals(30_000, f.money.getGold(31L))
        assertEquals(450, f.points.getRealPoint(31L))
        assertEquals(5, f.repo.daily!!.paidUsed)

        assertEquals(4, f.roleCounts.getValue(31L, RoleCountSystem.DISPATCH))
        assertEquals(4, f.roleCounts.getMaxValue(31L, RoleCountSystem.DISPATCH))
        assertEquals(5, f.roleCounts.getValue(31L, RoleCountSystem.BUY_DISPATCH))
    }

    @Test
    fun dailyResetReturnsClientQuotaToZeroOfFour() = runTest {
        val f = fixture()
        f.repo.daily = DispatchDailyUsage(20261001, 4, 7)

        val usage = f.system.syncDailyUsage(31L, 20261002)

        assertEquals(DispatchDailyUsage(20261002, 0, 0), usage)
        assertEquals(0, f.roleCounts.getValue(31L, RoleCountSystem.DISPATCH))
        assertEquals(4, f.roleCounts.getMaxValue(31L, RoleCountSystem.DISPATCH))
        assertEquals(0, f.roleCounts.getValue(31L, RoleCountSystem.BUY_DISPATCH))
        assertEquals(DispatchSystem.BUY_DISPATCH_MAX, f.roleCounts.getMaxValue(31L, RoleCountSystem.BUY_DISPATCH))
    }

    @Test
    fun insufficientPaidCurrencyDoesNotStartOrIncreaseUsage() = runTest {
        val f = fixture(gold = 0, point = 0)
        val day = 20261001
        f.repo.daily = DispatchDailyUsage(day, 4, 0)

        assertEquals(
            DispatchStartResult.COUNT_FULL,
            f.system.startDispatch(31L, 1, 45000.0, dayKey = day).first,
        )
        assertEquals(DispatchDailyUsage(day, 4, 0), f.repo.daily)
        assertTrue(f.repo.rows.none { it.slotIndex > 0 })

        f.repo.daily = DispatchDailyUsage(day, 4, 4)
        assertEquals(
            DispatchStartResult.COUNT_FULL,
            f.system.startDispatch(31L, 1, 45001.0, dayKey = day).first,
        )
        assertEquals(DispatchDailyUsage(day, 4, 4), f.repo.daily)
        assertTrue(f.repo.rows.none { it.slotIndex > 0 })
    }

    @Test
    fun quickCompleteCostsFiftyPointAndCannotDoubleChargeSameSlot() = runTest {
        val f = fixture(point = 500)
        val now = 45_000.0
        val started = f.system.startDispatch(
            31L, 1, now, dayKey = 20261001
        ).second
        assertNotNull(started)

        val first = f.system.quickCompleteDispatch(
            31L, started!!.slotIndex, now + 0.01, 50
        )
        assertEquals(DispatchSystem.QUICK_COMPLETE_SUCCESS, first.first)
        assertEquals(now + 0.01, first.second!!.endTime, 1e-9)
        assertEquals(450, f.points.getRealPoint(31L))

        val second = f.system.quickCompleteDispatch(
            31L, started.slotIndex, now + 0.01, 50
        )
        assertEquals(DispatchSystem.QUICK_COMPLETE_INVALID, second.first)
        assertEquals(450, f.points.getRealPoint(31L))
    }

    @Test
    fun uiSellNineUsesDvtFiftyPointFormula() = runTest {
        val f = fixture()
        val uiSell = UISellSystem(
            mapOf(9 to UISellDef(0, 20390, 0, 0)),
            mapOf(9 to UISellFormulaDef(1, 50, List(11) { 0 })),
            mockk<UiSellCountRepository>(relaxed = true),
            mockk(relaxed = true),
            f.roleCounts,
        )

        assertEquals(
            UiSellQuote.Money(UISellSystem.CURRENCY_GOLD, 50),
            uiSell.query(31L, UISellSystem.SELL_DISPATCH_QUICK),
        )
    }

    @Test
    fun uiSellSixShowsGoldThenPointPriceWithoutDeducting() = runTest {
        val f = fixture()
        f.system.syncDailyUsage(31L, 20261001)
        val uiSell = UISellSystem(
            mapOf(6 to UISellDef(0, 20153, 0, 0)),
            emptyMap(),
            mockk<UiSellCountRepository>(relaxed = true),
            mockk(relaxed = true),
            f.roleCounts,
        )

        assertEquals(
            UiSellQuote.Money(UISellSystem.CURRENCY_GAME_GOLD, 5_000),
            uiSell.query(31L, 6),
        )

        f.roleCounts.setValue(
            31L,
            RoleCountSystem.BUY_DISPATCH,
            4,
            DispatchSystem.BUY_DISPATCH_MAX,
        )
        assertEquals(
            UiSellQuote.Money(UISellSystem.CURRENCY_GOLD, 50),
            uiSell.query(31L, 6),
        )
        assertEquals(50_000, f.money.getGold(31L))
        assertEquals(500, f.points.getRealPoint(31L))
    }
}
