package tw.tsm.tests

import io.mockk.*
import kotlinx.coroutines.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.domain.constants.EThings
import tw.tsm.domain.interfaces.repositories.InventoryRepository
import tw.tsm.domain.interfaces.repositories.TradeAccountSnapshot
import tw.tsm.domain.valueobjects.ThingData
import tw.tsm.gamelogic.systems.InventorySystem
import tw.tsm.gamelogic.systems.MoneySystem
import tw.tsm.gamelogic.systems.RoleCountSystem
import tw.tsm.gamelogic.systems.TradeCacheSettlementCoordinator

/** Real online BAG/MONEY services; only SQL repository is mocked. No C025 wire. */
class DvtTradeCacheSettlementCoordinatorTest {
    private val a=993101L
    private val b=993102L
    private val bagA=mapOf(3 to ThingData(itemId=501,quantity=1))
    private val bagB=mapOf(7 to ThingData(itemId=502,quantity=2,damage=11))
    private val repo=mockk<InventoryRepository>(relaxed=true)
    private val counts=mockk<RoleCountSystem>(relaxed=true)
    private val inventory=InventorySystem(repo,emptyMap(),counts)
    private val money=MoneySystem(repo)
    private val coordinator=TradeCacheSettlementCoordinator(repo,inventory,money)
    private val savedBags=mutableListOf<Pair<Long,Map<Int,ThingData>>>()
    private val savedMoney=mutableListOf<Triple<Long,Int,Int>>()

    private suspend fun setup() {
        coEvery { repo.loadItems(any(),any()) } coAnswers {
            if (secondArg<Int>() != EThings.BAG) emptyMap()
            else if (firstArg<Long>() == a) bagA else bagB
        }
        coEvery { repo.loadMoney(a) } returns (1000 to 9)
        coEvery { repo.loadMoney(b) } returns (2000 to 19)
        coEvery { repo.saveAllItems(any(), EThings.BAG, any()) } coAnswers {
            savedBags.add(firstArg<Long>() to thirdArg<Map<Int,ThingData>>().toMap())
        }
        coEvery { repo.saveMoney(any(),any(),any()) } coAnswers {
            savedMoney.add(Triple(firstArg<Long>(),secondArg<Int>(),thirdArg<Int>()))
        }
        inventory.loadCharacterData(a)
        inventory.loadCharacterData(b)
        money.loadMoney(a)
        money.loadMoney(b)
    }

    private fun plans():Pair<TradeAccountSnapshot,TradeAccountSnapshot> =
        TradeAccountSnapshot(a,bagA,mapOf(3 to bagB.getValue(7)),1000,1200,9) to
            TradeAccountSnapshot(b,bagB,mapOf(7 to bagA.getValue(3)),2000,1800,19)

    @Test
    fun successPublishesBothCachesBeforeEitherAutosave()=runBlocking {
        setup()
        val (first,second)=plans()
        val entered=CompletableDeferred<Unit>()
        val release=CompletableDeferred<Unit>()
        coEvery { repo.commitTwoCharacterTradeAtomic(first,second) } coAnswers {
            entered.complete(Unit)
            release.await()
            true
        }
        val job=async { coordinator.stageCommit(first,second) }
        entered.await()
        val autosaveA=async { inventory.save(a); money.saveMoney(a) }
        val autosaveB=async { inventory.save(b); money.saveMoney(b) }
        repeat(12) { yield() }
        assertTrue(savedBags.isEmpty())
        assertTrue(savedMoney.isEmpty())
        release.complete(Unit)
        assertTrue(job.await())
        autosaveA.await()
        autosaveB.await()
        assertEquals(first.afterBag,inventory.getLoadedBagSnapshot(a))
        assertEquals(second.afterBag,inventory.getLoadedBagSnapshot(b))
        assertEquals(1200,money.getGold(a))
        assertEquals(1800,money.getGold(b))
        assertEquals(mapOf(a to first.afterBag,b to second.afterBag),savedBags.toMap())
        assertTrue(savedMoney.contains(Triple(a,1200,9)))
        assertTrue(savedMoney.contains(Triple(b,1800,19)))
        coVerify(exactly=1) { repo.commitTwoCharacterTradeAtomic(first,second) }
    }

    @Test
    fun sqlFalseLeavesBothPlayerBagsAndCurrencyExactlyAsBefore()=runBlocking {
        setup()
        val (first,second)=plans()
        coEvery { repo.commitTwoCharacterTradeAtomic(first,second) } returns false
        assertFalse(coordinator.stageCommit(first,second))
        assertEquals(bagA,inventory.getLoadedBagSnapshot(a))
        assertEquals(bagB,inventory.getLoadedBagSnapshot(b))
        assertEquals(1000,money.getGold(a))
        assertEquals(2000,money.getGold(b))
    }

    @Test
    fun sqlFailureLeavesCachesUnchangedAndDoesNotSendSuccess()=runBlocking {
        setup()
        val (first,second)=plans()
        coEvery { repo.commitTwoCharacterTradeAtomic(first,second) } throws IllegalStateException("rollback")
        assertThrows(IllegalStateException::class.java) {
            runBlocking { coordinator.stageCommit(first,second) }
        }
        assertEquals(bagA,inventory.getLoadedBagSnapshot(a))
        assertEquals(bagB,inventory.getLoadedBagSnapshot(b))
        assertEquals(1000,money.getGold(a))
        assertEquals(2000,money.getGold(b))
    }

    @Test
    fun cancellationDuringSqlCannotSkipEitherCachePublication()=runBlocking {
        setup()
        val (first,second)=plans()
        val entered=CompletableDeferred<Unit>()
        val release=CompletableDeferred<Unit>()
        coEvery { repo.commitTwoCharacterTradeAtomic(first,second) } coAnswers {
            entered.complete(Unit)
            release.await()
            true
        }
        val job=async { coordinator.stageCommit(first,second) }
        entered.await()
        job.cancel()
        release.complete(Unit)
        job.join()
        assertEquals(first.afterBag,inventory.getLoadedBagSnapshot(a))
        assertEquals(second.afterBag,inventory.getLoadedBagSnapshot(b))
        assertEquals(1200,money.getGold(a))
        assertEquals(1800,money.getGold(b))
    }

    @Test
    fun logoutOfSecondPlayerCannotUnloadCacheDuringTwoPartySqlCommit()=runBlocking {
        setup()
        val (first,second)=plans()
        val entered=CompletableDeferred<Unit>()
        val release=CompletableDeferred<Unit>()
        coEvery { repo.commitTwoCharacterTradeAtomic(first,second) } coAnswers {
            entered.complete(Unit)
            release.await()
            true
        }
        val job=async { coordinator.stageCommit(first,second) }
        entered.await()
        val logout=async { inventory.saveAndUnload(b); money.saveAndUnload(b) }
        repeat(12) { yield() }
        assertFalse(logout.isCompleted)
        release.complete(Unit)
        assertTrue(job.await())
        logout.await()
        assertTrue(savedBags.contains(b to second.afterBag))
        assertTrue(savedMoney.contains(Triple(b,1800,19)))
        assertNull(inventory.getLoadedBagSnapshot(b))
        assertNull(money.getLoadedMoney(b))
    }

    @Test
    fun staleOrUnloadedCacheBlocksSqlDebitBeforeItStarts()=runBlocking {
        setup()
        val (first,second)=plans()
        assertFalse(coordinator.stageCommit(first.copy(beforeGold=999),second))
        assertFalse(coordinator.stageCommit(first.copy(beforeBag=emptyMap()),second))
        assertFalse(coordinator.stageCommit(first,second.copy(characterId=a)))
        inventory.saveAndUnload(b)
        assertFalse(coordinator.stageCommit(first,second))
        coVerify(exactly=0) { repo.commitTwoCharacterTradeAtomic(any(),any()) }
    }
}
