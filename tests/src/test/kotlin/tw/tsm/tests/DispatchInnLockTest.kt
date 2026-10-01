package tw.tsm.tests

import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.domain.data.DispatchSaveData
import tw.tsm.domain.interfaces.repositories.DispatchRepository
import tw.tsm.gamelogic.systems.DispatchSystem
import tw.tsm.app.response.InnResponseSender
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.valueobjects.FollowNpcData

class DispatchInnLockTest {
    @Test fun activeAndFinishedUnclaimedDispatchesPreventInnMutationAfterRelog() = runTest {
        val repo = mockk<DispatchRepository>()
        coEvery { repo.findByCharacterId(31L) } returns listOf(DispatchSaveData(1, 1.0, 2.0, 1, 3))
        repeat(2) {
            val system = dispatchSystemForTest(repo, mockk(), emptyMap(), emptyMap())
            var changed = false
            assertNull(system.withAvailableInnPet(31, 3) { changed = true; 1 })
            assertFalse(changed)
            assertEquals(9, system.withAvailableInnPet(31, 4) { 9 })
        }
    }
    @Test fun metadataOfferDoesNotLockAnyInnWarrior() = runTest {
        val repo = mockk<DispatchRepository>()
        coEvery { repo.findByCharacterId(31L) } returns listOf(DispatchSaveData(0, 0.0, 0.0, 1, 0))
        assertEquals(true, dispatchSystemForTest(repo, mockk(), emptyMap(), emptyMap()).withAvailableInnPet(31, 3) { true })
    }
    @Test fun clientInnStatusUsesDispatchMembershipNotSurplus() {
        val packets = mutableListOf<ByteArray>()
        val session = mockk<GameSession>(relaxed = true)
        every { session.send(any()) } answers { packets.add(firstArg()); Unit }
        val sender = InnResponseSender()
        val pet = FollowNpcData(slotIndex = 3, npcId = 12020, name = "", surplus = 7)
        sender.sendInnNpcData(session, mapOf(3 to pet), setOf(3))
        assertEquals(1, packets.last().last().toInt())
        sender.sendInnNpcData(session, mapOf(3 to pet))
        assertEquals(0, packets.last().last().toInt())
    }
}
