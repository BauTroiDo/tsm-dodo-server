package tw.tsm.tests

import io.mockk.*
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.app.handlers.DvtChildFollowerLiveSync
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.repositories.ChildProfileState
import tw.tsm.domain.interfaces.repositories.ChildRepository
import tw.tsm.domain.valueobjects.ChildDataSnapshot
import tw.tsm.domain.valueobjects.FollowNpcData
import tw.tsm.gamelogic.systems.PetSystem

/** DVT child link must resync on pet retrieval without fabricating child ownership. */
class DvtChildFollowerLiveSyncTest {
    private fun child(kind: Int): ChildDataSnapshot {
        val bytes = ByteArray(93)
        bytes[0] = kind.toByte()
        bytes[1] = 1
        bytes[3] = 1
        bytes[72] = 0x90.toByte()
        bytes[73] = 1
        return ChildDataSnapshot.decode(bytes)
    }

    private data class Harness(
        val session: GameSession,
        val pets: PetSystem,
        val repository: ChildRepository,
        val frames: MutableList<ByteArray>,
    )

    private fun harness(
        childState: ChildProfileState?,
        follow: Map<Int, FollowNpcData>,
        cart: Map<Int, FollowNpcData> = emptyMap(),
        inn: Map<Int, FollowNpcData> = emptyMap(),
        capacity: Int = 9,
    ): Harness {
        val session = mockk<GameSession>(relaxed = true)
        val frames = mutableListOf<ByteArray>()
        every { session.send(capture(frames)) } just Runs
        val pets = mockk<PetSystem>()
        every { pets.getCache(42L) } returns PetSystem.PetCache(
            follow = follow.toMutableMap(),
            cart = cart.toMutableMap(),
            inn = inn.toMutableMap(),
        )
        every { pets.getFollowCapacity(42L) } returns capacity
        val repository = mockk<ChildRepository>()
        coEvery { repository.loadState(42L) } returns childState
        return Harness(session, pets, repository, frames)
    }

    @Test fun returnedBoundAdultRestoresOriginalS069009AfterS015Appeared() = runBlocking {
        val uuid = UUID.randomUUID().toString()
        val returned = FollowNpcData(slotIndex = 4, npcId = 15049, instanceId = uuid)
        val state = ChildProfileState(child(3), uuid)
        val h = harness(state, mapOf(4 to returned))
        DvtChildFollowerLiveSync.afterCarriedPetAppeared(
            h.session, 42L, returned, h.pets, h.repository)
        assertEquals(1, h.frames.size)
        assertArrayEquals(byteArrayOf(69, 9, 0, 4, 93, 0),
            h.frames.single().copyOfRange(0, 6))
        assertArrayEquals(state.snapshot.toBytes(), h.frames.single().copyOfRange(6, 99))
    }

    @Test fun noSpontaneousPacketForNormalGeneralOrNoChildRecord() = runBlocking {
        val uuid = UUID.randomUUID().toString()
        val other = FollowNpcData(slotIndex = 2, npcId = 15049, instanceId = UUID.randomUUID().toString())
        val h = harness(ChildProfileState(child(3), uuid), mapOf(2 to other))
        DvtChildFollowerLiveSync.afterCarriedPetAppeared(
            h.session, 42L, other, h.pets, h.repository)
        assertTrue(h.frames.isEmpty(), "Same NPC template must not be treated as child")

        val noChild = harness(null, mapOf(2 to other))
        DvtChildFollowerLiveSync.afterCarriedPetAppeared(
            noChild.session, 42L, other, noChild.pets, noChild.repository)
        assertTrue(noChild.frames.isEmpty())
        val noRepository = harness(null, mapOf(2 to other))
        DvtChildFollowerLiveSync.afterCarriedPetAppeared(
            noRepository.session, 42L, other, noRepository.pets, null)
        assertTrue(noRepository.frames.isEmpty())
        coVerify(exactly = 0) { noRepository.repository.loadState(any()) }
    }

    @Test fun rejectDeadJuvenileOrDuplicateAndInvalidOwnedSlots() = runBlocking {
        val uid = UUID.randomUUID().toString()
        val follower = FollowNpcData(slotIndex = 5, npcId = 15049, instanceId = uid)
        for (kind in listOf(0, 1, 2)) {
            val h = harness(ChildProfileState(child(kind), uid), mapOf(5 to follower))
            DvtChildFollowerLiveSync.afterCarriedPetAppeared(
                h.session, 42L, follower, h.pets, h.repository)
            assertTrue(h.frames.isEmpty(), "Invalid stage must not link S069009: kind=" + kind)
        }
        val duplicate = harness(
            ChildProfileState(child(3), uid), mapOf(5 to follower),
            cart = mapOf(1 to follower.copy(storageType = 2, slotIndex = 1))
        )
        DvtChildFollowerLiveSync.afterCarriedPetAppeared(
            duplicate.session, 42L, follower, duplicate.pets, duplicate.repository)
        assertTrue(duplicate.frames.isEmpty(), "Duplicate UUID across storages invalid")

        val outOfRange = harness(ChildProfileState(child(3), uid), mapOf(5 to follower), capacity = 4)
        DvtChildFollowerLiveSync.afterCarriedPetAppeared(
            outOfRange.session, 42L, follower, outOfRange.pets, outOfRange.repository)
        assertTrue(outOfRange.frames.isEmpty())
    }

    @Test fun allCarriedRetrievalHandlersAreWiredAfterNpcAppear() {
        val root = listOf(Path.of("app/src/main/kotlin/tw/tsm/app/handlers"),
            Path.of("../app/src/main/kotlin/tw/tsm/app/handlers"))
            .first { Files.isDirectory(it) }
        for (name in listOf("FollowNpcHandler.kt", "InnHandler.kt", "WarriorRepositoryHandler.kt")) {
            val source = Files.readString(root.resolve(name))
            assertTrue(source.contains("DvtChildFollowerLiveSync.afterCarriedPetAppeared("), name)
            assertTrue(source.indexOf("responseSender.sendFollowNpcFullData(session") <
                source.lastIndexOf("DvtChildFollowerLiveSync.afterCarriedPetAppeared("), name)
        }
        val inn = Files.readString(root.resolve("InnHandler.kt"))
        assertTrue(inn.contains("responseSender.sendFollowNpcRemove(session, session.playerId, followSlot, false)"))
    }
}
