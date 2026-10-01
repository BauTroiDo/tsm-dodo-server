package tw.tsm.tests

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tw.tsm.domain.data.AstrolabeDef
import tw.tsm.domain.data.AstrolabeLevelDef
import tw.tsm.domain.interfaces.repositories.AstrolabeRepository
import tw.tsm.domain.interfaces.repositories.InventoryRepository
import tw.tsm.domain.valueobjects.AstrolabeData
import tw.tsm.gamelogic.systems.AstrolabeSystem
import tw.tsm.gamelogic.systems.MoneySystem
import tw.tsm.gamelogic.systems.PlayerStateManager
import tw.tsm.gamelogic.systems.StatSystem

class AstrolabeD7RegressionTest {
    private class MemoryAstrolabeRepository(initial: AstrolabeData? = null) : AstrolabeRepository {
        var stored: AstrolabeData? = initial?.copySafe()
        var saveCount = 0
        override suspend fun findByCharacterId(characterId: Long): AstrolabeData? = stored?.copySafe()
        override suspend fun save(characterId: Long, data: AstrolabeData) {
            saveCount++
            stored = data.copySafe()
        }
    }

    private fun defs(): Map<Int, AstrolabeDef> = (1..7).associateWith { kind ->
        AstrolabeDef(
            kind = kind,
            levels = (1..10).map { lv -> AstrolabeLevelDef(value = kind * 10 + lv, needPoint = lv * 3) }
        )
    }

    private suspend fun makeSystem(repo: MemoryAstrolabeRepository, gold: Int = 1_000_000): Triple<AstrolabeSystem, MoneySystem, InventoryRepository> {
        val inventory = mockk<InventoryRepository>(relaxed = true)
        coEvery { inventory.loadMoney(1L) } returns Pair(gold, 0)
        val money = MoneySystem(inventory)
        money.loadMoney(1L)
        val system = AstrolabeSystem(
            repository = repo,
            defs = defs(),
            moneySystem = money,
            playerStateManager = PlayerStateManager(),
            statSystem = mockk<StatSystem>(relaxed = true)
        )
        return Triple(system, money, inventory)
    }

    @Test
    fun findNextStarFollowsDvtRoundRobinOrder() = runBlocking {
        val repo = MemoryAstrolabeRepository()
        val (system) = makeSystem(repo)
        assertEquals(1, system.findNextStar(intArrayOf(0,0,0,0,0,0,0)))
        assertEquals(2, system.findNextStar(intArrayOf(1,0,0,0,0,0,0)))
        assertEquals(7, system.findNextStar(intArrayOf(1,1,1,1,1,1,0)))
        assertEquals(1, system.findNextStar(intArrayOf(1,1,1,1,1,1,1)))
        assertEquals(4, system.findNextStar(intArrayOf(3,3,3,2,2,2,2)))
    }

    @Test
    fun upgradeSpendsPointAndSquareGoldThenPersists() = runBlocking {
        val repo = MemoryAstrolabeRepository(AstrolabeData(100, IntArray(7)))
        val (system, money, inventory) = makeSystem(repo, gold = 100_000)
        system.load(1L)
        val result = system.upgrade(1L, 1)
        assertTrue(result.success)
        assertEquals(3, result.pointCost)
        assertEquals(10_000, result.goldCost)
        assertEquals(97, result.state.natalPoint)
        assertArrayEquals(intArrayOf(1,0,0,0,0,0,0), result.state.starLevels)
        assertEquals(90_000, money.getGold(1L))
        assertEquals(97, repo.stored!!.natalPoint)
        assertArrayEquals(result.state.starLevels, repo.stored!!.starLevels)
        coVerify(exactly = 1) { inventory.saveMoney(1L, 90_000, 0) }
    }

    @Test
    fun invalidUpgradeNeverMutatesOrPersists() = runBlocking {
        val repo = MemoryAstrolabeRepository(AstrolabeData(2, IntArray(7)))
        val (system, money) = makeSystem(repo, gold = 9_999)
        system.load(1L)
        val savesBefore = repo.saveCount
        val wrong = system.upgrade(1L, 2)
        val poor = system.upgrade(1L, 1)
        assertFalse(wrong.success)
        assertFalse(poor.success)
        assertEquals(savesBefore, repo.saveCount)
        assertEquals(2, system.snapshot(1L).natalPoint)
        assertArrayEquals(IntArray(7), system.snapshot(1L).starLevels)
        assertEquals(9_999, money.getGold(1L))
    }

    @Test
    fun bonusSumsLevelsAndKeepsStarSevenSeparate() = runBlocking {
        val repo = MemoryAstrolabeRepository()
        val (system) = makeSystem(repo)
        val bonus = system.bonuses(intArrayOf(2,1,1,1,1,1,2))
        assertEquals(23, bonus.hpx)
        assertEquals(21, bonus.def)
        assertEquals(31, bonus.intelligence)
        assertEquals(41, bonus.spx)
        assertEquals(51, bonus.agi)
        assertEquals(61, bonus.atk)
        assertEquals(143, bonus.criticle)
    }

    @Test
    fun natalPointCeilingMatchesDvt65535Contract() = runBlocking {
        val repo = MemoryAstrolabeRepository(AstrolabeData(65_530, IntArray(7)))
        val (system) = makeSystem(repo)
        system.load(1L)
        assertEquals(2, system.maxUsableItemQuantity(1L, 2))
        assertEquals(0, system.maxUsableItemQuantity(1L, 6))
        val ok = system.addNatalPoint(1L, 5)
        assertTrue(ok.success)
        assertEquals(65_535, ok.state.natalPoint)
        val overflow = system.addNatalPoint(1L, 1)
        assertFalse(overflow.success)
        assertEquals(65_535, system.snapshot(1L).natalPoint)
    }

    @Test
    fun restoreCompensatesPersistedNatalPointMutation() = runBlocking {
        val original = AstrolabeData(100, intArrayOf(1,1,1,1,1,1,1))
        val repo = MemoryAstrolabeRepository(original)
        val (system) = makeSystem(repo)
        system.load(1L)

        val before = system.snapshot(1L)
        val added = system.addNatalPoint(1L, 25)
        assertTrue(added.success)
        assertEquals(125, system.snapshot(1L).natalPoint)

        system.restore(1L, before)

        assertEquals(100, system.snapshot(1L).natalPoint)
        assertArrayEquals(before.starLevels, system.snapshot(1L).starLevels)
        assertEquals(100, repo.stored!!.natalPoint)
        assertArrayEquals(before.starLevels, repo.stored!!.starLevels)
    }
}
