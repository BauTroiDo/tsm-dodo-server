package tw.tsm.tests

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tw.tsm.app.handlers.WarriorRepositoryHandler
import tw.tsm.app.response.InnResponseSender
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.domain.valueobjects.FollowNpcData
import tw.tsm.gamelogic.systems.PetSystem

class DvtWarriorRepositoryRefreshTest {
    private val petSystem = mockk<PetSystem>()
    private val response = mockk<ResponseSender>(relaxed = true)
    private val innResponse = mockk<InnResponseSender>(relaxed = true)
    private val session = mockk<GameSession>(relaxed = true) {
        every { state } returns SessionState.IN_GAME
        every { roleId } returns 42
        every { playerId } returns 4242L
    }
    private val handler = WarriorRepositoryHandler(petSystem, response, innResponse)

    @Test
    fun `store refreshes DVT warrior repository immediately after success`() = runTest {
        val followPet = mockk<FollowNpcData> {
            every { npcId } returns 18001
        }
        val stored = listOf(mockk<FollowNpcData>())

        every { petSystem.getFollowNpc(42, 2) } returns followPet
        every { petSystem.storeToWarehouse(42, 2) } returns true
        every { petSystem.getWarehousePets(42) } returns stored

        handler.handle(session, 2, byteArrayOf(2, 0x51, 0x46))

        verifyOrder {
            innResponse.sendWarehouseStoreResult(session, 1)
            response.sendFollowNpcRemove(session, 4242L, 2, false)
            innResponse.sendWarehouseData(session, stored)
        }
    }

    @Test
    fun `retrieve refreshes DVT warrior repository immediately after success`() = runTest {
        val retrieved = mockk<FollowNpcData>()
        val remaining = listOf(mockk<FollowNpcData>())

        every { petSystem.retrieveFromWarehouse(42, 18001) } returns 3
        every { petSystem.getFollowNpc(42, 3) } returns retrieved
        every { petSystem.getWarehousePets(42) } returns remaining

        handler.handle(session, 3, byteArrayOf(0, 0x51, 0x46))

        verifyOrder {
            innResponse.sendWarehouseRetrieveResult(session, 1)
            response.sendFollowNpcFullData(session, listOf(retrieved))
            innResponse.sendWarehouseData(session, remaining)
        }
    }

    @Test
    fun `failed store does not send a stale repository refresh`() = runTest {
        val followPet = mockk<FollowNpcData> {
            every { npcId } returns 18001
        }

        every { petSystem.getFollowNpc(42, 2) } returns followPet
        every { petSystem.storeToWarehouse(42, 2) } returns false

        handler.handle(session, 2, byteArrayOf(2, 0x51, 0x46))

        verify(exactly = 1) { innResponse.sendWarehouseStoreResult(session, 2) }
        verify(exactly = 0) { innResponse.sendWarehouseData(any(), any()) }
    }
}
