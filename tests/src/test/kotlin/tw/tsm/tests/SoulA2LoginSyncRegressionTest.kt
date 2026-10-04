package tw.tsm.tests

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tw.tsm.app.response.SoulResponseSender
import tw.tsm.domain.interfaces.SessionSender
import tw.tsm.domain.interfaces.repositories.SoulRepository
import tw.tsm.domain.valueobjects.SoulSkillData
import tw.tsm.domain.valueobjects.SoulThingData
import tw.tsm.gamelogic.systems.SoulSystem

class SoulA2LoginSyncRegressionTest {
    private class CaptureSession : SessionSender {
        var packet: ByteArray? = null
        override fun send(data: ByteArray) { packet = data }
        override fun close() = Unit
    }

    private fun sampleSoul() = SoulThingData(
        sn = 0x0102030405060708L,
        itemId = 59576,
        element = 2,
        equipId = 12001,
        level = 123,
        starLevel = 4,
        startAtt = listOf(1, 2, 3, 4, 5, 6),
        nowAtt = listOf(11, 12, 13, 14, 15, 16),
        skills = listOf(
            SoulSkillData(1001, 1),
            SoulSkillData(1002, 2),
            SoulSkillData(1003, 3),
            SoulSkillData(1004, 4),
        ),
        lockFlags = 3,
    )

    @Test
    fun loginSnapshotMatchesDvtSoulThingWireByteForByte() {
        val session = CaptureSession()
        SoulResponseSender().sendSnapshot(session, listOf(sampleSoul()))

        val expected = intArrayOf(
            120, 8, 0, 1, 0,
            8, 7, 6, 5, 4, 3, 2, 1,
            184, 232,
            2,
            225, 46,
            123, 0,
            4,
            1, 0, 2, 0, 3, 0, 4, 0, 5, 0, 6, 0,
            11, 0, 12, 0, 13, 0, 14, 0, 15, 0, 16, 0,
            233, 3, 1,
            234, 3, 2,
            235, 3, 3,
            236, 3, 4,
            3,
            1,
        ).map { it.toByte() }.toByteArray()

        assertEquals(59, expected.size)
        assertArrayEquals(expected, session.packet)
    }

    @Test
    fun emptyLoginSnapshotStillSendsAuthoritativeInitPacket() {
        val session = CaptureSession()
        SoulResponseSender().sendSnapshot(session, emptyList())
        assertArrayEquals(
            byteArrayOf(120, 8, 0, 0, 0, 1),
            session.packet,
        )
    }

    @Test
    fun soulSystemLoadsSnapshotsAndUnloadsWithoutMutation() = runTest {
        val repo = mockk<SoulRepository>()
        val soul = sampleSoul()
        coEvery { repo.findByCharacterId(77L) } returns listOf(soul)

        val system = SoulSystem(repo)
        assertEquals(listOf(soul), system.load(77L))
        assertEquals(soul, system.find(77L, soul.sn))
        assertEquals(soul, system.equippedForNpc(77L, 12001))

        system.unload(77L)
        assertEquals(emptyList<SoulThingData>(), system.snapshot(77L))
    }
}
