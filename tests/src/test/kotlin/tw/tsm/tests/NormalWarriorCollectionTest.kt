package tw.tsm.tests

import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.app.handlers.CollectionHandler
import tw.tsm.data.loaders.CollectWarriorDatLoader
import tw.tsm.domain.data.*
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.domain.interfaces.repositories.BitFlagRepository
import tw.tsm.domain.interfaces.repositories.CollectionBookRepository
import tw.tsm.gamelogic.systems.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path

class NormalWarriorCollectionTest {
    private val flags = mutableSetOf<Int>()
    private val repo = mockk<BitFlagRepository>(relaxed = true)
    private val inventory = mockk<InventorySystem>(relaxed = true)
    private val response = mockk<ResponseSender>(relaxed = true)
    private val warriors = mapOf(
        10001 to CollectWarriorDef(10001, 1601, 1602, 0L, listOf(65352 to 5), 0),
        10002 to CollectWarriorDef(10002, 1603, 1604, 0L, listOf(65352 to 3), 0),
    )
    init {
        coEvery { repo.getSetFlagIds(31L) } answers { flags.toSet() }
        coEvery { repo.setFlag(31L, any()) } answers { flags.add(secondArg()); Unit }
        coEvery { inventory.addItemBundleToBagExact(31L, any()) } returns emptyList()
    }
    private fun system() = CollectionSystem(
        BitFlagSystem(repo, emptyMap()), warriors, emptyMap(), emptyList(),
        emptyMap(), emptyMap(), emptyMap(), emptyMap(), inventory,
        mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
        mockk<CollectionBookRepository>(relaxed = true), response,
    )
    private fun session() = mockk<GameSession>(relaxed = true) {
        every { state } returns SessionState.IN_GAME
        every { roleId } returns 31
    }

    @Test fun overlappingSingleAndBulkClaimsGrantEachRewardOnce() = runTest {
        flags += listOf(1601, 1603)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        coEvery { inventory.addItemBundleToBagExact(31L, any()) } coAnswers {
            entered.complete(Unit)
            release.await()
            emptyList()
        }
        val s = system()
        val bulk = async { s.claimWarriorRewards(31L, listOf(10001, 10002)) }
        entered.await()
        val single = async { s.claimWarriorRewards(31L, listOf(10001)) }
        yield()
        release.complete(Unit)
        assertTrue(bulk.await().success)
        assertFalse(single.await().success)
        assertEquals(setOf(1601, 1602, 1603, 1604), flags)
        coVerify(exactly = 1) { inventory.addItemBundleToBagExact(31L, any()) }
    }

    @Test fun fullBagDoesNotConsumeRewardAndRetryAfterRelogSucceeds() = runTest {
        flags += 1601
        coEvery { inventory.addItemBundleToBagExact(31L, any()) } returns null
        val s = system()
        assertTrue(s.claimWarriorRewards(31L, listOf(10001)).bagFull)
        assertEquals(setOf(1601), flags)
        s.unload(31L)
        val relog = system()
        coEvery { inventory.addItemBundleToBagExact(31L, any()) } returns emptyList()
        assertTrue(relog.claimWarriorRewards(31L, listOf(10001)).success)
        assertFalse(system().claimWarriorRewards(31L, listOf(10001)).success)
        coVerify(exactly = 1) { repo.setFlag(31L, 1602) }
    }

    @Test fun invalidUnownedDuplicateAndPartlyClaimedBundlesAreRejectedBeforeInventory() = runTest {
        val s = system()
        assertFalse(s.claimWarriorRewards(31L, emptyList()).success)
        assertFalse(s.claimWarriorRewards(31L, listOf(65535)).success)
        assertFalse(s.claimWarriorRewards(31L, listOf(10001)).success)
        flags += listOf(1601, 1603, 1604)
        assertFalse(s.claimWarriorRewards(31L, listOf(10001, 10001)).success)
        assertFalse(s.claimWarriorRewards(31L, listOf(10001, 10002)).success)
        coVerify(exactly = 0) { inventory.addItemBundleToBagExact(any(), any()) }
        coVerify(exactly = 0) { repo.setFlag(any(), any()) }
    }

    @Test fun permanentOwnershipSurvivesUnloadAndBackfillDoesNotSetRewardOrShine() = runTest {
        val s = system()
        assertEquals(1601, s.markWarriorOwned(31L, 10001))
        assertNull(s.markWarriorOwned(31L, 10001))
        s.unload(31L)
        val relog = system()
        assertEquals(listOf(1603), relog.syncOwnedWarriors(31L, listOf(10001, 10002, 10002, 65535)))
        assertEquals(setOf(1601, 1603), flags)
        assertEquals(0, relog.refreshNftShine(31L))
    }

    @Test fun packetOneAndManyMatchDvtSuccessBagFullAndMalformedResults() = runTest {
        flags += listOf(1601, 1603)
        val sess = session()
        val packets = mutableListOf<ByteArray>()
        every { sess.send(capture(packets)) } just Runs
        val handler = CollectionHandler(system(), response, mockk(relaxed = true))
        handler.handle(sess, 1, byteArrayOf(0x11, 0x27)) // 10001 LE
        assertArrayEquals(byteArrayOf(95, 1, 0, 1), packets.last())
        coVerify { inventory.addItemBundleToBagExact(31L, listOf(65352 to 5)) }
        coEvery { inventory.addItemBundleToBagExact(31L, any()) } returns null
        handler.handle(sess, 1, byteArrayOf(0x12, 0x27))
        assertArrayEquals(byteArrayOf(95, 1, 0, 0, 4), packets.last())
        handler.handle(sess, 11, byteArrayOf(1, 0x12, 0x27))
        assertArrayEquals(byteArrayOf(95, 11, 0, 2, 4), packets.last())
        handler.handle(sess, 11, byteArrayOf(2, 0x12, 0x27))
        assertArrayEquals(byteArrayOf(95, 11, 0, 0), packets.last())
        handler.handle(sess, 1, byteArrayOf(0x12))
        assertArrayEquals(byteArrayOf(95, 1, 0, 0, 0), packets.last())
        coEvery { inventory.addItemBundleToBagExact(31L, any()) } returns emptyList()
        handler.handle(sess, 11, byteArrayOf(1, 0x12, 0x27))
        assertArrayEquals(byteArrayOf(95, 11, 0, 1), packets.last())
        assertEquals(setOf(1601, 1602, 1603, 1604), flags)
    }

    @Test fun allDvtNormalSortEntriesResolveToUniquePermanentFlagsAndZeroShine() {
        val root = generateSequence(Path.of("").toAbsolutePath()) { it.parent }
            .first { Files.exists(it.resolve("File.dat/Data-ios/IOS_CollectWarrior_C.dat")) }
        val defs = CollectWarriorDatLoader.load(Files.readAllBytes(root.resolve("File.dat/Data-ios/IOS_CollectWarrior_C.dat")))
        val sortBytes = Files.readAllBytes(root.resolve("File.dat/Data-ios/IOS_CollectWarriorSort_C.dat"))
        val bb = ByteBuffer.wrap(sortBytes).order(ByteOrder.LITTLE_ENDIAN)
        val normal = mutableListOf<Int>()
        repeat(bb.int) {
            bb.short
            val ids = List(3) { bb.short.toInt() and 65535 }
            val nft = bb.get().toInt() and 255
            if (nft == 0) normal += ids.filter { it > 0 }
        }
        assertFalse(bb.hasRemaining())
        assertEquals(1296, normal.size)
        assertEquals(normal.size, normal.toSet().size)
        val flagIds = mutableSetOf<Int>()
        for (id in normal) {
            val row = requireNotNull(defs[id])
            assertTrue(row.getFlag > 0 && row.rewardFlag > 0)
            assertTrue(flagIds.add(row.getFlag))
            assertTrue(flagIds.add(row.rewardFlag))
            assertEquals(0, row.shineValue)
            assertTrue(row.awards.any { it.first > 0 && it.second > 0 })
        }
    }
}