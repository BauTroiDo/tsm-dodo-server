package tw.tsm.tests

import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.app.handlers.DispatchHandler
import tw.tsm.domain.constants.DispatchCompleteResult
import tw.tsm.domain.data.DispatchDailyUsage
import tw.tsm.domain.data.DispatchSaveData
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.repositories.DispatchRepository
import tw.tsm.gamelogic.systems.DispatchSystem

class DispatchSafetyTest {
    @Test fun unavailableRewardPreservesCompletedClaimAcrossSystemReload() = runTest {
        val record = DispatchSaveData(1, 1.0, 2.0, 1, 3)
        val repo = mockk<DispatchRepository>(relaxed = true)
        coEvery { repo.findByCharacterId(31L) } returns listOf(record)
        repeat(2) {
            val system = dispatchSystemForTest(repo, mockk(), emptyMap(), emptyMap())
            assertEquals(DispatchCompleteResult.ITEM_DISABLED, system.completeDispatch(31L, 1, 3.0).first)
            assertEquals(listOf(record), system.queryDispatches(31L))
        }
        coVerify(exactly = 0) { repo.deleteBySlot(any(), any()) }
    }
    @Test fun prematureAndMissingClaimsRemainRejected() = runTest {
        val repo = mockk<DispatchRepository>()
        coEvery { repo.findByCharacterId(31L) } returns listOf(DispatchSaveData(1, 1.0, 2.0, 1, 3))
        val system = dispatchSystemForTest(repo, mockk(), emptyMap(), emptyMap())
        assertEquals(DispatchCompleteResult.NOT_FINISHED, system.completeDispatch(31L, 1, 1.5).first)
        assertEquals(DispatchCompleteResult.INVALID_KIND, system.completeDispatch(31L, 2, 3.0).first)
    }
    private fun session(packets: MutableList<ByteArray>) = mockk<GameSession>(relaxed = true) {
        every { state } returns SessionState.IN_GAME
        every { roleId } returns 31
        every { send(any()) } answers { packets.add(firstArg()); Unit }
    }
    @Test fun emptyQueryExplicitlyClearsEveryClientSlotBeforeOpeningUi() = runTest {
        val packets = mutableListOf<ByteArray>()
        val system = mockk<DispatchSystem>()
        coEvery { system.syncDailyUsage(31L, any()) } returns DispatchDailyUsage(20261001, 0, 0)
        coEvery { system.queryDispatches(31L) } returns emptyList()
        coEvery { system.queryOffer(31L) } returns DispatchSaveData(0, 0.0, 0.0, 1, 0, 1, 2)
        DispatchHandler(system).handle(session(packets), 1, byteArrayOf())
        val snapshot = packets.first()
        assertEquals(88, snapshot.size)
        assertEquals(4, snapshot[3].toInt())
        for (slot in 1..4) {
            val offset = 4 + (slot - 1) * 21
            assertEquals(slot, snapshot[offset].toInt())
            assertTrue(snapshot.sliceArray(offset+1..offset+20).all { it == 0.toByte() })
        }
        assertEquals(1, packets.last()[1].toInt())
    }
    @Test fun malformedRequestsCannotReachSystem() = runTest {
        val system = mockk<DispatchSystem>()
        val s = session(mutableListOf())
        val handler = DispatchHandler(system)
        handler.handle(s, 1, byteArrayOf(1))
        for (kind in listOf(2, 5)) {
            handler.handle(s, kind, byteArrayOf())
            handler.handle(s, kind, byteArrayOf(1, 2))
        }
        confirmVerified(system)
    }
}
