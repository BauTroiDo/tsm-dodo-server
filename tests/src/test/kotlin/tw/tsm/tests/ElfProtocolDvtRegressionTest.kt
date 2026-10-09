package tw.tsm.tests

import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.app.handlers.ElfCatalog
import tw.tsm.app.handlers.ElfHandler
import tw.tsm.app.handlers.ElfPackets
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.SessionSender
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.domain.interfaces.repositories.*
import tw.tsm.gamelogic.systems.BitFlagSystem
import tw.tsm.gamelogic.systems.InventorySystem
import java.nio.file.Files
import java.nio.file.Path

class ElfProtocolDvtRegressionTest {
    private val catalog: ElfCatalog by lazy {
        ElfCatalog.fromPacked(
            Files.readAllBytes(Path.of("..", "File.dat", "Data", "elf_C.dat")),
            Files.readAllBytes(Path.of("..", "File.dat", "Data-ios", "IOS_ElfExp_C.dat")),
        )
    }

    @Test
    fun `PC DVT has 24 collectable elves and default index 999`() {
        assertEquals(25, catalog.defs.size)
        assertEquals(999, catalog.defs.getValue(999).no)
        assertEquals(6034, catalog.defs.getValue(1).ownershipFlag)
        assertEquals(20, catalog.expByLevel.size)
        assertEquals(20, catalog.expByLevel[1])
        assertEquals(33280, catalog.expByLevel[20])
    }

    @Test
    fun `S107-005 and S107-006 respect client payload layout`() {
        val sender = mockk<SessionSender>(relaxed = true)
        val frames = mutableListOf<ByteArray>()
        every { sender.send(capture(frames)) } just Runs
        ElfPackets.snapshot(sender, 999, 0, emptyList())
        assertArrayEquals(byteArrayOf(107, 5, 0, -25, 3, 0, 0, 0, 0, 0), frames[0])
        ElfPackets.snapshot(sender, 1, 19, listOf(ElfProgressRecord(1, 2, 12)))
        assertArrayEquals(byteArrayOf(107, 5, 0, 1, 0, 19, 0, 0, 0, 1, 1, 0, 2, 0, 12, 0, 0, 0), frames[1])
        ElfPackets.progress(sender, ElfProgressRecord(2, 3, 45))
        assertArrayEquals(byteArrayOf(107, 6, 0, 2, 0, 3, 0, 45, 0, 0, 0), frames[2])
    }

    @Test
    fun `elf deployment denied if missing ownership flag, without DB write`() = runBlocking {
        val repo = mockk<ElfRepository>()
        val bits = mockk<BitFlagSystem>()
        val inv = mockk<InventorySystem>()
        val resp = mockk<ResponseSender>()
        val session = mockk<GameSession>(relaxed = true)
        every { session.state } returns SessionState.IN_GAME
        every { session.roleId } returns 41
        every { session.battleId } returns 0L
        val frames = mutableListOf<ByteArray>()
        every { session.send(capture(frames)) } just Runs
        coEvery { repo.load(41L) } returns ElfSnapshotRecord(ElfProfileRecord(), emptyList())
        coEvery { bits.getSetFlagIds(41L) } returns emptySet()
        val h = ElfHandler(repo, bits, inv, resp, emptyMap(), catalog)
        h.handle(session, 2, byteArrayOf(1, 0))
        assertArrayEquals(byteArrayOf(107, 2, 0, 1, 0, 0), frames.single())
        coVerify(exactly = 0) { repo.setActive(any(), any()) }
    }

    @Test
    fun `elf query returns no unauthorized progress and default index`() = runBlocking {
        val repo = mockk<ElfRepository>()
        val bits = mockk<BitFlagSystem>()
        val session = mockk<GameSession>(relaxed = true)
        every { session.state } returns SessionState.IN_GAME
        every { session.roleId } returns 41
        every { session.battleId } returns 0L
        val frames = mutableListOf<ByteArray>()
        every { session.send(capture(frames)) } just Runs
        coEvery { repo.load(41L) } returns ElfSnapshotRecord(ElfProfileRecord(1, 0), listOf(ElfProgressRecord(1, 9, 45)))
        coEvery { bits.getSetFlagIds(41L) } returns emptySet()
        val h = ElfHandler(repo, bits, mockk(), mockk(), emptyMap(), catalog)
        h.handle(session, 5, byteArrayOf())
        assertArrayEquals(byteArrayOf(107, 5, 0, -25, 3, 0, 0, 0, 0, 0), frames.single())
    }

    @Test
    fun `PC C107-005 must return owned elf levels despite uncleared Lua sendBuffer`() = runBlocking {
        val repo = mockk<ElfRepository>()
        val bits = mockk<BitFlagSystem>()
        val session = mockk<GameSession>(relaxed = true)
        every { session.state } returns SessionState.IN_GAME
        every { session.roleId } returns 41
        every { session.battleId } returns 0L
        val frames = mutableListOf<ByteArray>()
        every { session.send(capture(frames)) } just Runs
        coEvery { bits.getSetFlagIds(41L) } returns setOf(6089, 6118)
        coEvery { repo.load(41L) } returns ElfSnapshotRecord(ElfProfileRecord(), emptyList())

        val handler = ElfHandler(repo, bits, mockk(), mockk(), emptyMap(), catalog)
        handler.handle(session, 5, byteArrayOf())
        // UIElf.OnOpen uses a shared sendBuffer and can resend leftover bytes.
        handler.handle(session, 5, byteArrayOf(11, 0, 7, 9))
        assertEquals(2, frames.size, "Both empty and stale-buffer queries must receive S107-005")
        assertArrayEquals(frames[0], frames[1])
        val frame = java.nio.ByteBuffer.wrap(frames[1])
            .order(java.nio.ByteOrder.LITTLE_ENDIAN)
        assertEquals(107, frame.get(0).toInt() and 255)
        assertEquals(5, frame.get(1).toInt() and 255)
        assertEquals(2, frame.get(9).toInt() and 255)
        assertEquals(11, frame.getShort(10).toInt() and 65535)
        assertEquals(1, frame.getShort(12).toInt() and 65535)
        assertEquals(23, frame.getShort(18).toInt() and 65535)
        assertEquals(1, frame.getShort(20).toInt() and 65535)
    }

    @Test
    fun `stale-buffer snapshot cannot be queried before gameplay`() = runBlocking {
        val session = mockk<GameSession>(relaxed = true)
        every { session.state } returns SessionState.AUTHENTICATED
        every { session.roleId } returns 41
        every { session.battleId } returns 0L
        val handler = ElfHandler(mockk(), mockk(), mockk(), mockk(), emptyMap(), catalog)
        handler.handle(session, 5, byteArrayOf(11, 0))
        verify(exactly = 0) { session.send(any()) }
    }

}
