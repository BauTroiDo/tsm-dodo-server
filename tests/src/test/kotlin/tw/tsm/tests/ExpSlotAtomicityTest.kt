package tw.tsm.tests

import io.mockk.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.domain.data.FNpcSlotDef
import tw.tsm.domain.interfaces.repositories.ExpSlotRepository
import tw.tsm.domain.valueobjects.FollowNpcData
import tw.tsm.domain.valueobjects.ThingData
import tw.tsm.gamelogic.systems.*

class ExpSlotAtomicityTest {
    private class Fixture(baseExp: Int = 1000, quantity: Int = 10, poolValue: Long = 0) {
        val pets = mockk<PetSystem>()
        val inventory = mockk<InventorySystem>()
        val repo = mockk<ExpSlotRepository>()
        var pet = FollowNpcData(1, 123, level = 20, exp = 1000)
        var coin: ThingData? = ThingData(itemId = 31207, quantity = quantity)
        var pool = poolValue
        var commits = 0
        var fail = false
        var grant = true
        val system = ExpSlotSystem(mapOf(1 to FNpcSlotDef(1, baseExp, listOf(1,2,3))),
            repo, pets, emptyMap(), inventory) { 0 }
        init {
            every { pets.getFollowPets(42) } answers { mapOf(1 to pet) }
            every { pets.getCache(42) } returns null
            every { inventory.getBagItem(42, 7) } answers { coin }
            every { pets.planExpSlotGain(42, 1, any()) } answers {
                if (!grant) null else PetSystem.PetExpGain(1, pet, pet.copy(exp = pet.exp + thirdArg<Long>()))
            }
            every { pets.publishExpSlotGain(42, any()) } answers { pet = secondArg<PetSystem.PetExpGain>().after }
            every { inventory.setBagItemSync(42, 7, any()) } answers { coin = thirdArg<ThingData?>() }
            coEvery { repo.loadJackpotExp() } answers { pool }
            coEvery { repo.settleSpin(42, 7, any(), any(), any(), any(), any(), any()) } coAnswers {
                assertEquals(coin, thirdArg<ThingData>())
                assertEquals(pet, arg<FollowNpcData>(4))
                if (fail) error("simulated transaction rollback")
                commits++
                if (arg<Long?>(6) != null) pool = arg<Long>(7)
            }
        }
    }

    @Test fun `transaction failure publishes no debit exp or jackpot`() = runTest {
        val f = Fixture(5_000_000, 10, 12345)
        f.fail = true
        assertNull(f.system.spin(42, 37, listOf(1,123), 7))
        assertEquals(10, f.coin!!.quantity)
        assertEquals(1000L, f.pet.exp)
        assertEquals(12345L, f.pool)
        verify(exactly = 0) { f.inventory.setBagItemSync(any(), any(), any()); f.pets.publishExpSlotGain(any(), any()) }
    }

    @Test fun `no gain wrong coin or insufficient count consumes nothing`() = runTest {
        val f = Fixture(quantity = 9)
        assertNull(f.system.spin(42, 38, listOf(1,123), 7))
        f.coin = f.coin!!.copy(itemId = 29877)
        assertNull(f.system.spin(42, 37, listOf(1,123), 7))
        f.coin = f.coin!!.copy(itemId = 31207)
        f.grant = false
        assertNull(f.system.spin(42, 37, listOf(1,123), 7))
        assertEquals(0, f.commits)
        assertEquals(9, f.coin!!.quantity)
    }

    @Test fun `concurrent ten spins cannot reuse one coin stack`() = runTest {
        val f = Fixture(quantity = 10)
        val results = List(2) { async { f.system.spin(42, 38, listOf(1,123), 7) } }.awaitAll()
        assertEquals(1, results.count { it != null })
        assertEquals(1, f.commits)
        assertNull(f.coin)
        assertEquals(11000L, f.pet.exp)
        assertEquals(10, results.filterNotNull().single().awards.size)
    }

    @Test fun `ten jackpots consume pool once and preserve int32 overflow remainder`() = runTest {
        val f = Fixture(5_000_000, 10, Int.MAX_VALUE.toLong() + 100)
        val result = f.system.spin(42, 38, listOf(1,123), 7)!!
        val extra = Int.MAX_VALUE.toLong() - 5_000_000
        assertEquals(extra, result.jackpotExtra)
        assertEquals(5_000_100L, result.jackpotAfter)
        assertEquals(Int.MAX_VALUE, result.awards.first().exp)
        assertTrue(result.awards.drop(1).all { it.exp == 5_000_000 })
        assertEquals(result.jackpotAfter, f.pool)
        assertEquals(1, f.commits)
    }
}
