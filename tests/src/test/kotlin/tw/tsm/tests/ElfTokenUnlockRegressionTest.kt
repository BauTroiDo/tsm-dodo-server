package tw.tsm.tests

import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.app.handlers.ElfCatalog
import tw.tsm.app.handlers.ElfTokenUnlockService
import tw.tsm.domain.constants.EItemUseKind
import tw.tsm.domain.data.ItemDef
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.domain.valueobjects.ThingData
import tw.tsm.gamelogic.systems.BitFlagSystem
import tw.tsm.gamelogic.systems.InventorySystem
import java.nio.file.Files
import java.nio.file.Path

class ElfTokenUnlockRegressionTest {
    private val catalog by lazy {
        ElfCatalog.fromPacked(
            Files.readAllBytes(Path.of("..", "File.dat", "Data", "elf_C.dat")),
            Files.readAllBytes(Path.of("..", "File.dat", "Data-ios", "IOS_ElfExp_C.dat"))
        )
    }

    private class Fixture(val catalog: ElfCatalog, val id: Int = 31912) {
        val flags = mockk<BitFlagSystem>()
        val inv = mockk<InventorySystem>()
        val resp = mockk<ResponseSender>(relaxed = true)
        val session = mockk<GameSession>(relaxed = true)
        val thing = mockk<ThingData>()
        val def = mockk<ItemDef>()
        val frames = mutableListOf<ByteArray>()
        val handler: ElfTokenUnlockService

        init {
            every { session.roleId } returns 41
            every { session.send(capture(frames)) } just Runs
            every { thing.itemId } returns id
            every { thing.quantity } returns 1
            every { def.specialAbility } returns EItemUseKind.ELF_TOKEN
            every { def.kind } returns 93
            every { inv.getBagItem(41L, 7) } returns thing
            coEvery { inv.removeBagItemsExact(41L, listOf(7 to 1)) } returns true
            coEvery { flags.getSetFlagIds(41L) } returns emptySet()
            coEvery { flags.setFlag(any(), any()) } just Runs
            coEvery { flags.clearFlag(any(), any()) } just Runs
            handler = ElfTokenUnlockService(flags, inv, catalog, resp, mapOf(id to def))
        }
    }

    @Test
    fun `valid 31912 Bạch Ngọc ticket unlocks elf 12 and updates DVT UI packets`() = runBlocking {
        val f = Fixture(catalog)
        assertEquals(6092, catalog.defs.getValue(12).ownershipFlag)
        assertTrue(f.handler.redeem(f.session, 7, 1, 0))
        coVerify(exactly = 1) { f.flags.setFlag(41L, 6092) }
        coVerify(exactly = 1) { f.inv.removeBagItemsExact(41L, listOf(7 to 1)) }
        verify(exactly = 1) { f.resp.sendBitFlagChanges(f.session, listOf(6092 to true)) }
        verify(exactly = 1) { f.resp.sendReduceBagItem(f.session, 7, 1) }
        verify(exactly = 1) { f.resp.sendUseItemSuccess(f.session, 31912, 1, true) }
        assertArrayEquals(byteArrayOf(107, 1, 0, 12, 0), f.frames[0])
        assertArrayEquals(byteArrayOf(107, 6, 0, 12, 0, 1, 0, 0, 0, 0, 0), f.frames[1])
    }

    @Test
    fun `duplicate owned Elf ticket stays in inventory`() = runBlocking {
        val f = Fixture(catalog)
        coEvery { f.flags.getSetFlagIds(41L) } returns setOf(6092)
        assertFalse(f.handler.redeem(f.session, 7, 1, 0))
        coVerify(exactly = 0) { f.inv.removeBagItemsExact(any(), any()) }
        coVerify(exactly = 0) { f.flags.setFlag(any(), any()) }
        assertTrue(f.frames.isEmpty())
    }

    @Test
    fun `ticket for absent Elf no 25 is never spent`() = runBlocking {
        val f = Fixture(catalog, 31925)
        assertNull(catalog.defs[25])
        assertFalse(f.handler.redeem(f.session, 7, 1, 0))
        coVerify(exactly = 0) { f.inv.removeBagItemsExact(any(), any()) }
        coVerify(exactly = 0) { f.flags.setFlag(any(), any()) }
    }

    @Test
    fun `failed bag mutation rolls back ownership flag and retains ticket`() = runBlocking {
        val f = Fixture(catalog)
        coEvery { f.inv.removeBagItemsExact(41L, listOf(7 to 1)) } returns false
        assertFalse(f.handler.redeem(f.session, 7, 1, 0))
        coVerify(exactly = 1) { f.flags.setFlag(41L, 6092) }
        coVerify(exactly = 1) { f.flags.clearFlag(41L, 6092) }
        verify(exactly = 0) { f.resp.sendBitFlagChanges(any(), any()) }
    }

    @Test
    fun `cannot redeem multiple owned Elf tickets in a single packet`() = runBlocking {
        val f = Fixture(catalog)
        assertFalse(f.handler.redeem(f.session, 7, 2, 0))
        coVerify(exactly = 0) { f.flags.setFlag(any(), any()) }
        coVerify(exactly = 0) { f.inv.removeBagItemsExact(any(), any()) }
    }

    @Test
    fun `failed flag persistence does not remove ticket`() = runBlocking {
        val f = Fixture(catalog)
        coEvery { f.flags.setFlag(41L, 6092) } throws IllegalStateException("Test DB failure")
        assertFalse(f.handler.redeem(f.session, 7, 1, 0))
        coVerify(exactly = 0) { f.inv.removeBagItemsExact(any(), any()) }
    }
}
