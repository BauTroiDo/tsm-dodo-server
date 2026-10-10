package tw.tsm.tests

import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.app.handlers.ElfHandler
import tw.tsm.app.handlers.ItemHandler
import tw.tsm.domain.data.ItemDef
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.domain.interfaces.repositories.ElfRepository
import tw.tsm.domain.valueobjects.ThingData
import tw.tsm.gamelogic.systems.InventorySystem

class PhucThanUseItemTest {
    private val characterId = 41L
    private val itemId = 46038

    private fun makeHandler(
        inventory: InventorySystem,
        response: ResponseSender,
        repo: ElfRepository,
        elfHandler: ElfHandler,
        def: ItemDef,
    ): ItemHandler = ItemHandler(
        inventory, mockk(), mockk(), response, mockk(),
        mapOf(itemId to def), mockk(), mockk(), mockk(),
        mockk(), mockk(), mockk(), mockk(), mockk(), mockk(),
        elfRepository = repo, elfHandler = elfHandler,
    )

    private fun session(): GameSession = mockk(relaxed = true) {
        every { state } returns SessionState.IN_GAME
        every { roleId } returns characterId.toInt()
        every { battleId } returns 0L
    }

    private fun item(gain: Int): ItemDef {
        val def = mockk<ItemDef>()
        every { def.specialAbility } returns 93
        every { def.skillLink } returns gain
        return def
    }

    /** DVT C023-015: bagSlot(2) + quantity(4) + followIndex(1) + useType(1). */
    private fun packet(quantity: Int): ByteArray =
        byteArrayOf(7, 0, quantity.toByte(), 0, 0, 0, 0, 0)

    @Test
    fun `ExpGod item 46038 grants 10 charges per item and consumes exact quantity`() = runBlocking {
        val inv = mockk<InventorySystem>()
        val response = mockk<ResponseSender>(relaxed = true)
        val repo = mockk<ElfRepository>()
        val elf = mockk<ElfHandler>()
        val sess = session()
        every { inv.getBagItem(characterId, 7) } returns ThingData(itemId = itemId, quantity = 3)
        coEvery { repo.changeGodMission(characterId, 20) } returns 23
        coEvery { inv.removeBagItemsExact(characterId, listOf(7 to 2)) } returns true
        coEvery { elf.syncGodMission(sess) } just Runs

        makeHandler(inv, response, repo, elf, item(10)).handle(sess, 15, packet(2))

        coVerify(exactly = 1) { repo.changeGodMission(characterId, 20) }
        coVerify(exactly = 1) { inv.removeBagItemsExact(characterId, listOf(7 to 2)) }
        verify(exactly = 1) { response.sendReduceBagItem(sess, 7, 2) }
        coVerify(exactly = 1) { elf.syncGodMission(sess) }
    }

    @Test
    fun `GodCharge cap rejects item without consuming it`() = runBlocking {
        val inv = mockk<InventorySystem>()
        val response = mockk<ResponseSender>(relaxed = true)
        val repo = mockk<ElfRepository>()
        val sess = session()
        every { inv.getBagItem(characterId, 7) } returns ThingData(itemId = itemId, quantity = 3)
        coEvery { repo.changeGodMission(characterId, 20) } returns null

        makeHandler(inv, response, repo, mockk(), item(10)).handle(sess, 15, packet(2))

        coVerify(exactly = 0) { inv.removeBagItemsExact(any(), any()) }
        verify(exactly = 0) { response.sendReduceBagItem(any(), any(), any()) }
    }

    @Test
    fun `inventory failure rolls back charged points and does not acknowledge use`() = runBlocking {
        val inv = mockk<InventorySystem>()
        val response = mockk<ResponseSender>(relaxed = true)
        val repo = mockk<ElfRepository>()
        val sess = session()
        every { inv.getBagItem(characterId, 7) } returns ThingData(itemId = itemId, quantity = 3)
        coEvery { repo.changeGodMission(characterId, 20) } returns 23
        coEvery { repo.changeGodMission(characterId, -20) } returns 3
        coEvery { inv.removeBagItemsExact(characterId, listOf(7 to 2)) } returns false

        makeHandler(inv, response, repo, mockk(), item(10)).handle(sess, 15, packet(2))

        coVerify(exactly = 1) { repo.changeGodMission(characterId, -20) }
        verify(exactly = 0) { response.sendReduceBagItem(any(), any(), any()) }
    }

    @Test
    fun `unverified ExpGod zero skillLink is not consumed`() = runBlocking {
        val inv = mockk<InventorySystem>()
        val response = mockk<ResponseSender>(relaxed = true)
        val repo = mockk<ElfRepository>()
        val sess = session()
        every { inv.getBagItem(characterId, 7) } returns ThingData(itemId = itemId, quantity = 3)

        makeHandler(inv, response, repo, mockk(), item(0)).handle(sess, 15, packet(1))

        coVerify(exactly = 0) { repo.changeGodMission(any(), any()) }
        coVerify(exactly = 0) { inv.removeBagItemsExact(any(), any()) }
    }
}
