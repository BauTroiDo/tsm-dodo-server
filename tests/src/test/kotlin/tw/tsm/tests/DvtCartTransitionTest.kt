package tw.tsm.tests

import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.app.handlers.FollowNpcHandler
import tw.tsm.app.handlers.FollowNpcDeployHandler
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.domain.interfaces.repositories.CharacterRepository
import tw.tsm.domain.interfaces.repositories.PetRepository
import tw.tsm.domain.valueobjects.FollowNpcData
import tw.tsm.domain.valueobjects.ThingData
import tw.tsm.gamelogic.systems.PetSystem
import tw.tsm.gamelogic.systems.PlayerStateManager
import tw.tsm.gamelogic.systems.StatSystem

class DvtCartTransitionTest {
    private val repo = mockk<PetRepository>(relaxed = true)
    private val stat = mockk<StatSystem>(relaxed = true) {
        every { calculateNeedTotalExp(any(), any()) } returns 0L
        every { calculateMaxHp(any(), any(), any(), any(), any(), any(), any()) } returns 50_000L
        every { calculateMaxSp(any(), any(), any(), any(), any(), any(), any()) } returns 5_000
    }
    private val pets = PetSystem(repo, stat)
    private val response = mockk<ResponseSender>(relaxed = true)
    private val session = mockk<GameSession>(relaxed = true) {
        every { roleId } returns 42
        every { playerId } returns 900L
        every { state } returns SessionState.IN_GAME
    }
    private val states = PlayerStateManager().also {
        it.initPlayer(42, 100, 50, 100, 50, 1, 1, 1, 1, 1, 1, 1, 1, turn = 2)
    }
    private val handler = FollowNpcHandler(pets, response, states)
    private val cart = FollowNpcData(2, 30001, storageType = 2, level = 80, hp = 12345,
        skillPoint = 17, skill1Lv = 8, name = "saved", equips = mapOf(3 to ThingData(itemId = 456)))
    private suspend fun load(vararg entries: FollowNpcData) {
        coEvery { repo.findByCharacterId(42) } returns entries.toList()
        pets.load(42)
    }
    @Test fun `retrieve sends saved full pet data instead of resetting to defaults`() = runTest {
        load(cart)
        handler.handle(session, 11, byteArrayOf(2))
        val expected = cart.copy(slotIndex = 1, storageType = 1)
        assertEquals(expected, pets.getFollowPets(42)[1])
        verifyOrder {
            response.sendCartRemove(session, 2)
            response.sendFollowNpcFullData(session, listOf(expected))
        }
        verify(exactly = 0) { response.sendFollowNpcAdd(any(), any(), any(), any(), any(), any()) }
    }
    @Test fun `swap replaces only affected role and preserves both pet records`() = runTest {
        val follow = FollowNpcData(3, 30002, skillPoint = 29, skill2Lv = 7)
        load(cart, follow)
        handler.handle(session, 12, byteArrayOf(3, 2))
        val expected = cart.copy(slotIndex = 3, storageType = 1)
        assertEquals(expected, pets.getFollowPets(42)[3])
        assertEquals(follow.copy(slotIndex = 2, storageType = 2), pets.getCartPets(42)[2])
        verifyOrder {
            response.sendFollowNpcRemove(session, 900L, 3, false)
            response.sendFollowNpcFullData(session, listOf(expected))
            response.sendCartData(session, listOf(follow.copy(slotIndex = 2, storageType = 2)))
        }
    }
    @Test fun `truncated trailing and invalid slot packets preserve pets`() = runTest {
        load(cart, FollowNpcData(1, 30002))
        for ((sub, bytes) in listOf(11 to byteArrayOf(), 11 to byteArrayOf(2, 1),
            12 to byteArrayOf(1), 12 to byteArrayOf(1, 2, 3), 2 to byteArrayOf(0), 10 to byteArrayOf(5))) {
            handler.handle(session, sub, bytes)
        }
        assertEquals(cart, pets.getCartPets(42)[2])
        assertFalse(pets.getCache(42)!!.dirty)
        verify { response wasNot Called }
    }
    @Test fun `pet management before IN_GAME has no mutation`() = runTest {
        load(cart)
        every { session.state } returns SessionState.LOGGED_IN
        handler.handle(session, 11, byteArrayOf(2))
        assertFalse(pets.getCache(42)!!.dirty)
        verify { response wasNot Called }
    }
    @Test fun `deploy rejects truncated or trailing bytes and accepts word npc id`() = runTest {
        load(FollowNpcData(1, 30001))
        val characters = mockk<CharacterRepository>(relaxed = true)
        val deploy = FollowNpcDeployHandler(pets, response, characters)
        for (bytes in listOf(byteArrayOf(), byteArrayOf(0x31), byteArrayOf(0x31, 0x75, 1)))
            deploy.handle(session, 1, bytes)
        assertEquals(0, pets.getCache(42)!!.fightNpcId)
        deploy.handle(session, 1, byteArrayOf(0x31, 0x75))
        assertEquals(30001, pets.getCache(42)!!.fightNpcId)
        deploy.handle(session, 2, byteArrayOf(1))
        assertEquals(30001, pets.getCache(42)!!.fightNpcId)
        deploy.handle(session, 2, byteArrayOf())
        assertEquals(0, pets.getCache(42)!!.fightNpcId)
        coVerifySequence {
            characters.updateFightNpcId(42, 30001)
            characters.updateFightNpcId(42, 0)
        }
    }
    @Test fun `cart capacity follows rebirth tier`() = runTest {
        val follow = FollowNpcData(1, 30002)
        load(follow, cart.copy(slotIndex = 1), cart)
        states.getState(42)!!.turn = 0
        handler.handle(session, 10, byteArrayOf(1))
        assertEquals(follow, pets.getFollowPets(42)[1])
        states.getState(42)!!.turn = 1
        handler.handle(session, 10, byteArrayOf(1))
        assertEquals(follow, pets.getFollowPets(42)[1]) // two slots already full
        states.getState(42)!!.turn = 2
        handler.handle(session, 10, byteArrayOf(1))
        assertNull(pets.getFollowPets(42)[1])
        assertEquals(follow.copy(slotIndex = 3, storageType = 2), pets.getCartPets(42)[3])
    }
}
