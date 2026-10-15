package tw.tsm.tests

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.domain.interfaces.repositories.CharacterCurrencyRepository
import tw.tsm.domain.interfaces.repositories.MiningTsAwardCommit
import tw.tsm.gamelogic.systems.PlayerCurrencySystem

class DvtMiningTsCurrencyFenceTest {
    private class MemoryRepo : CharacterCurrencyRepository {
        val db=ConcurrentHashMap<Int,Int>()
        var rejectWrite = false
        override suspend fun loadAll(characterId:Long):Map<Int,Int> = db.toMap()
        override suspend fun setValue(characterId:Long,currencyKind:Int,value:Int) {
            delay(3) // reproduce suspended SQL window
            if (rejectWrite) error("Injected DB failure")
            db[currencyKind]=value
        }
    }

    @Test fun concurrentAddSubtractNeverLosesTsWalletUpdates() = runBlocking {
        val repo=MemoryRepo()
        repo.db[8]=100
        val sys=PlayerCurrencySystem(repo)
        sys.load(31)
        coroutineScope {
            (1..30).map { async(Dispatchers.Default) {
                assertTrue(sys.add(31,8,1))
                assertTrue(sys.subtract(31,8,1))
            } }.awaitAll()
        }
        assertEquals(100,sys.get(31,8))
        assertEquals(100,repo.db[8])
    }

    @Test fun failedSqlWriteNeverPublishesGhostTsBalance() = runBlocking {
        val repo=MemoryRepo()
        repo.db[8]=9
        val sys=PlayerCurrencySystem(repo)
        sys.load(31)
        repo.rejectWrite=true
        var failed=false
        try { sys.add(31,8,6) } catch (_: IllegalStateException) { failed=true }
        assertTrue(failed)
        assertEquals(9,sys.get(31,8))
        assertEquals(9,repo.db[8])
    }

    @Test fun atomicReceiptPublishesOnceAndUsesCurrentBalance() = runBlocking {
        val repo=MemoryRepo()
        repo.db[8]=10
        val sys=PlayerCurrencySystem(repo)
        sys.load(31)
        val before=sys.applyVerifiedMiningTsAward(31) { expected ->
            assertEquals(10,expected)
            if (repo.db[8]!=expected) null else {
                repo.db[8]=expected+3 // simulate one committed SQL tx
                MiningTsAwardCommit(true,expected+3,500)
            }
        }
        assertTrue(before!!.newlyApplied)
        assertEquals(13,sys.get(31,8))
        assertTrue(sys.add(31,8,2))
        val replay=sys.applyVerifiedMiningTsAward(31) { expected ->
            assertEquals(15,expected)
            MiningTsAwardCommit(false,13,500) // original persisted receipt
        }
        assertFalse(replay!!.newlyApplied)
        assertEquals(15,sys.get(31,8))
        assertEquals(15,repo.db[8])
    }

    @Test fun verifiedAwardSharesFenceWithNormalTsPurchases() = runBlocking {
        val repo=MemoryRepo()
        repo.db[8]=20
        val sys=PlayerCurrencySystem(repo)
        sys.load(31)
        coroutineScope {
            val a=async(Dispatchers.Default) {
                sys.applyVerifiedMiningTsAward(31) { expected ->
                    delay(8)
                    if (repo.db[8]!=expected) null else {
                        repo.db[8]=expected+5
                        MiningTsAwardCommit(true,expected+5,490)
                    }
                }
            }
            val b=async(Dispatchers.Default) { sys.subtract(31,8,4) }
            assertNotNull(a.await())
            assertTrue(b.await())
        }
        assertEquals(21,sys.get(31,8))
        assertEquals(21,repo.db[8])
    }
}
