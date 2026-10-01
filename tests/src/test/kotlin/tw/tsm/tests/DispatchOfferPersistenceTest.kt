package tw.tsm.tests

import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.domain.data.*
import tw.tsm.domain.interfaces.repositories.DispatchRepository
import tw.tsm.gamelogic.systems.*

class DispatchOfferPersistenceTest {
    private class Repo : DispatchRepository {
        val rows = mutableListOf<DispatchSaveData>()
        var daily: DispatchDailyUsage? = null
        override suspend fun findByCharacterId(characterId: Long) = rows.toList()
        override suspend fun insert(characterId: Long, data: DispatchSaveData) { rows.add(data) }
        override suspend fun deleteBySlot(characterId: Long, slotIndex: Int) { rows.removeAll { it.slotIndex == slotIndex } }
        override suspend fun getDailyUsage(characterId: Long) = daily
        override suspend fun saveDailyUsage(characterId: Long, usage: DispatchDailyUsage) { daily = usage }
    }
    private val defs = mapOf(1 to (1..5).associateWith { listOf(DispatchDef(0, it, 2, 70000)) })
    private fun system(repo: Repo): DispatchSystem {
        val pets = mockk<PetSystem>()
        every { pets.getInnNpc(31, any()) } returns mockk(relaxed = true)
        return dispatchSystemForTest(repo, pets, defs, mapOf(1 to DispatchBonusDef(1, 1, 4, 1, 3500, 20658), 2 to DispatchBonusDef(2, 2, 2, 3, 15000, 20675)))
    }
    @Test fun queryAndRelogRetainValidKindAndExactlyTheSameEffects() = runTest {
        val repo = Repo(); val s = system(repo); val offer = s.queryOffer(31)
        assertTrue(offer.dispatchKind in 1..5)
        assertEquals(offer, s.queryOffer(31))
        assertEquals(offer, system(repo).queryOffer(31))
        assertEquals(1, repo.rows.size)
        assertTrue(s.queryDispatches(31).isEmpty())
    }
    @Test fun startUsesDisplayedOfferAndRotatesToUnusedKind() = runTest {
        val repo = Repo(); val s = system(repo); val offer = s.queryOffer(31)
        val dispatch = s.startDispatch(31, 3, 45000.0).second!!
        assertEquals(offer.dispatchKind, dispatch.dispatchKind)
        assertEquals(offer.effectIndex1, dispatch.effectIndex1)
        assertEquals(offer.effectIndex2, dispatch.effectIndex2)
        assertNotEquals(dispatch.dispatchKind, s.queryOffer(31).dispatchKind)
        assertEquals(listOf(dispatch), s.queryDispatches(31))
    }
    @Test fun allFiveKindsAreReachableThroughFourSlots() = runTest {
        val repo = Repo(); val s = system(repo)
        val kinds = (1..4).map { s.startDispatch(31, it, 45000.0).second!!.dispatchKind }
        assertEquals((1..5).toSet(), (kinds + s.queryOffer(31).dispatchKind).toSet())
    }
}
