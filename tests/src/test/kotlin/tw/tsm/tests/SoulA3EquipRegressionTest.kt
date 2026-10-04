package tw.tsm.tests

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tw.tsm.app.handlers.SoulHandler
import tw.tsm.app.response.SoulResponseSender
import tw.tsm.domain.data.NpcDef
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.domain.interfaces.SessionSender
import tw.tsm.domain.interfaces.repositories.SoulRepository
import tw.tsm.domain.valueobjects.FollowNpcData
import tw.tsm.domain.valueobjects.SoulThingData
import tw.tsm.gamelogic.systems.SoulEquipResult
import tw.tsm.gamelogic.systems.SoulEquipTargetValidator
import tw.tsm.gamelogic.systems.PetSystem
import tw.tsm.gamelogic.systems.SoulSystem
import tw.tsm.protocol.PacketWriter

class SoulA3EquipRegressionTest {
    private class CaptureSession : SessionSender {
        var packet: ByteArray? = null
        override fun send(data: ByteArray) { packet = data }
        override fun close() = Unit
    }

    private fun soul(
        sn: Long = 0x0102030405060708L,
        element: Int = 2,
        equipId: Int = 0,
        lockFlags: Int = 0,
    ) = SoulThingData(
        sn = sn,
        itemId = 59576,
        element = element,
        equipId = equipId,
        lockFlags = lockFlags,
    )

    @Test
    fun equipAndErrorPacketsMatchDvtWire() {
        val session = CaptureSession()
        val sender = SoulResponseSender()

        sender.sendEquipResult(session, 1, 12001, 0x0102030405060708L)
        assertArrayEquals(
            intArrayOf(120, 1, 0, 1, 225, 46, 8, 7, 6, 5, 4, 3, 2, 1)
                .map { it.toByte() }.toByteArray(),
            session.packet,
        )

        sender.sendError(session, SoulSystem.ERR_ELEMENT_MISMATCH)
        assertArrayEquals(
            intArrayOf(120, 255, 0, 10).map { it.toByte() }.toByteArray(),
            session.packet,
        )
    }

    @Test
    fun targetValidatorCrossChecksFollowSlotNpcAndEligibility() {
        val valid = FollowNpcData(slotIndex = 3, npcId = 12001, canGrow = 1)

        assertEquals(null, SoulEquipTargetValidator.validate(valid, 12001, 2))
        assertEquals(SoulSystem.ERR_NO_WARRIOR, SoulEquipTargetValidator.validate(null, 12001, 2))
        assertEquals(SoulSystem.ERR_WARRIOR_ID, SoulEquipTargetValidator.validate(valid, 12002, 2))
        assertEquals(
            SoulSystem.ERR_NO_WARRIOR,
            SoulEquipTargetValidator.validate(valid.copy(canGrow = 2), 12001, 2),
        )
        assertEquals(SoulSystem.ERR_NO_WARRIOR, SoulEquipTargetValidator.validate(valid, 12001, null))
    }

    @Test
    fun lockedSoulMayEquipButMiningAndWrongElementAreRejected() = runTest {
        val repo = mockk<SoulRepository>()
        val locked = soul(lockFlags = 1)
        coEvery { repo.findByCharacterId(77L) } returns listOf(locked)
        coEvery { repo.save(any(), any()) } returns Unit
        val system = SoulSystem(repo)
        system.load(77L)

        val equipped = system.equip(77L, 12001, locked.sn, npcElement = 2)
        assertTrue(equipped is SoulEquipResult.Ok)
        assertEquals(12001, system.find(77L, locked.sn)?.equipId)

        val miningRepo = mockk<SoulRepository>()
        val mining = soul(lockFlags = 2)
        coEvery { miningRepo.findByCharacterId(88L) } returns listOf(mining)
        val miningSystem = SoulSystem(miningRepo)
        miningSystem.load(88L)
        assertEquals(
            SoulEquipResult.Fail(SoulSystem.ERR_MINING),
            miningSystem.equip(88L, 12001, mining.sn, npcElement = 2),
        )
        coVerify(exactly = 0) { miningRepo.save(any(), any()) }

        val mismatchRepo = mockk<SoulRepository>()
        val mismatch = soul(element = 3)
        coEvery { mismatchRepo.findByCharacterId(99L) } returns listOf(mismatch)
        val mismatchSystem = SoulSystem(mismatchRepo)
        mismatchSystem.load(99L)
        assertEquals(
            SoulEquipResult.Fail(SoulSystem.ERR_ELEMENT_MISMATCH),
            mismatchSystem.equip(99L, 12001, mismatch.sn, npcElement = 2),
        )
        coVerify(exactly = 0) { mismatchRepo.save(any(), any()) }
    }

    @Test
    fun oneSoulPerNpcTakeoffAndPersistenceFailureAreAtomic() = runTest {
        val repo = mockk<SoulRepository>()
        val first = soul(sn = 101L, equipId = 12001)
        val second = soul(sn = 102L)
        coEvery { repo.findByCharacterId(77L) } returns listOf(first, second)
        coEvery { repo.save(any(), any()) } returns Unit
        val system = SoulSystem(repo)
        system.load(77L)

        assertEquals(
            SoulEquipResult.Fail(SoulSystem.ERR_WARRIOR_HAS_SOUL),
            system.equip(77L, 12001, 102L, npcElement = 2),
        )
        assertEquals(
            SoulEquipResult.Fail(SoulSystem.ERR_EQUIP_TAKEOFF),
            system.takeOff(77L, 12002, 101L),
        )

        val takenOff = system.takeOff(77L, 12001, 101L)
        assertTrue(takenOff is SoulEquipResult.Ok)
        assertEquals(0, system.find(77L, 101L)?.equipId)

        val failingRepo = mockk<SoulRepository>()
        val original = soul(sn = 201L)
        coEvery { failingRepo.findByCharacterId(55L) } returns listOf(original)
        coEvery { failingRepo.save(55L, any()) } throws IllegalStateException("db down")
        val failingSystem = SoulSystem(failingRepo)
        failingSystem.load(55L)

        assertEquals(
            SoulEquipResult.Fail(SoulSystem.ERR_GENERIC),
            failingSystem.equip(55L, 12001, 201L, npcElement = 2),
        )
        assertEquals(0, failingSystem.find(55L, 201L)?.equipId)
    }

    @Test
    fun handlerParsesExactC120001AndRejectsBattle() = runTest {
        val repo = mockk<SoulRepository>()
        val original = soul()
        coEvery { repo.findByCharacterId(77L) } returns listOf(original)
        coEvery { repo.save(any(), any()) } returns Unit
        val system = SoulSystem(repo)
        system.load(77L)

        val petSystem = mockk<PetSystem>()
        every { petSystem.getFollowNpc(77L, 3) } returns
            FollowNpcData(slotIndex = 3, npcId = 12001, canGrow = 1)
        val npcDef = mockk<NpcDef>()
        every { npcDef.element } returns 2
        val response = mockk<ResponseSender>(relaxed = true)
        val session = mockk<GameSession>()
        every { session.state } returns SessionState.IN_GAME
        every { session.roleId } returns 77
        every { session.battleId } returns 0L

        val handler = SoulHandler(system, petSystem, mapOf(12001 to npcDef), response)
        val request = PacketWriter()
            .writeByte(1)
            .writeShortLE(12001)
            .writeByte(3)
            .writeLongLE(original.sn)
            .toByteArray()
        assertEquals(12, request.size)

        handler.handle(session, 1, request)
        verify(exactly = 1) {
            response.sendSoulEquipResult(session, 1, 12001, original.sn)
        }
        assertEquals(12001, system.find(77L, original.sn)?.equipId)

        every { session.battleId } returns 99L
        handler.handle(session, 1, request)
        verify(exactly = 1) {
            response.sendSoulError(session, SoulSystem.ERR_IN_BATTLE)
        }
    }
}
