package tw.tsm.tests

import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.app.handlers.ItemHandler
import tw.tsm.app.response.InventoryResponseSender
import tw.tsm.domain.constants.EItemUseKind
import tw.tsm.domain.data.ItemDef
import tw.tsm.domain.data.NpcDef
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.domain.interfaces.SessionSender
import tw.tsm.domain.valueobjects.FollowNpcData
import tw.tsm.domain.valueobjects.ThingData
import tw.tsm.gamelogic.systems.InventorySystem
import tw.tsm.gamelogic.systems.PetEquipSystem
import tw.tsm.gamelogic.systems.PetSystem
import tw.tsm.protocol.ThingDataWire
import java.nio.ByteBuffer
import java.nio.ByteOrder

class DvtItemWireTest {
    private class Capture : SessionSender {
        lateinit var packet: ByteArray
        override fun send(data: ByteArray) { packet = data }
        override fun close() {}
    }
    private val inventory = mockk<InventorySystem>()
    private val pet = mockk<PetEquipSystem>()
    private val response = mockk<ResponseSender>(relaxed = true)
    private val session = mockk<GameSession>(relaxed = true) {
        every { state } returns SessionState.IN_GAME
        every { roleId } returns 42
    }
    private val handler = ItemHandler(inventory, mockk(), mockk(), response, mockk(),
        emptyMap(), mockk(), mockk(), mockk(), mockk(), mockk(), mockk(), pet, mockk(), mockk())

    @Test fun `bag drop and use read full index before looking up inventory`() = runTest {
        every { inventory.getBagItem(42, 0x1234) } returns null
        handler.handle(session, 3, byteArrayOf(0x34,0x12,2,1,0,0))
        handler.handle(session, 15, byteArrayOf(0x34,0x12,2,1,0,0,2,1))
        verify(exactly = 2) { inventory.getBagItem(42, 0x1234) }
    }

    @Test fun `bag move batch decodes both word indices and preserves amount`() = runTest {
        coEvery { inventory.moveBagItem(42, 0x1234, 258, 0x5678) } returns true
        handler.handle(session, 10, byteArrayOf(1,0x34,0x12,2,1,0,0,0x78,0x56))
        coVerify(exactly = 1) { inventory.moveBagItem(42, 0x1234, 258, 0x5678) }
        verify { response.sendMoveBagItem(session, 0x1234, 258, 0x5678) }
    }

    @Test fun `truncated move batch is rejected before mutation`() = runTest {
        handler.handle(session, 10, byteArrayOf(2,0x34,0x12,2,1,0,0,0x78,0x56))
        verify { inventory wasNot Called }
    }

    @Test fun `pet equip retains byte follow slot and word bag slot`() = runTest {
        coEvery { pet.equip(42, 2, 0x1234) } returns PetEquipSystem.EquipResult.Fail
        handler.handle(session, 17, byteArrayOf(2,0x34,0x12))
        coVerify(exactly = 1) { pet.equip(42, 2, 0x1234) }
    }

    @Test fun `npc picture token recruits DVT follow npc and consumes exactly one`() = runTest {
        val itemId = 31000
        val npcId = 9001
        val itemDef = mockk<ItemDef> {
            every { kind } returns 0 // không phải Transform Doll
            every { specialAbility } returns EItemUseKind.NPC_PICTURE
            every { spare3 } returns npcId
        }
        val npcDef = mockk<NpcDef>(relaxed = true)
        val petSystem = mockk<PetSystem>()
        val petData = FollowNpcData(slotIndex = 2, npcId = npcId, canGrow = 1)

        every { inventory.getBagItem(42, 0x1234) } returns ThingData(itemId = itemId, quantity = 3)
        every {
            petSystem.addFollowNpc(
                characterId = 42,
                npcId = npcId,
                npcDef = npcDef,
                canGrow = 1,
                allowDuplicateInCart = true,
            )
        } returns (2 to petData)
        every { petSystem.getFollowPets(42) } returns mapOf(2 to petData)
        coEvery { inventory.removeFromBag(42, 0x1234, 1) } returns true

        val npcPictureHandler = ItemHandler(
            inventorySystem = inventory,
            moneySystem = mockk(),
            groundItemManager = mockk(),
            responseSender = response,
            worldManager = mockk(),
            itemDefs = mapOf(itemId to itemDef),
            itemUseProcessor = mockk(),
            playerStateManager = mockk(),
            statCalc = mockk(),
            mountSystem = mockk(),
            flyPointSystem = mockk(),
            characterRepository = mockk(),
            petEquipSystem = pet,
            questSystem = mockk(),
            skillSystem = mockk(),
            dailyProgressSystem = null,
            petSystem = petSystem,
            npcDefs = mapOf(npcId to npcDef),
        )

        npcPictureHandler.handle(
            session,
            15,
            byteArrayOf(0x34, 0x12, 3, 0, 0, 0, 0, 1)
        )

        coVerify(exactly = 1) { inventory.removeFromBag(42, 0x1234, 1) }
        verify(exactly = 1) { response.sendReduceBagItem(session, 0x1234, 1) }
        verify(exactly = 1) { response.sendUseItemSuccess(session, itemId, 1, true) }
        verify(exactly = 1) {
            response.sendFollowNpcAdd(session, any(), 2, npcId, 1, true)
        }
        verify(exactly = 1) { response.sendFollowNpcFullData(session, listOf(petData)) }
    }

    @Test fun `npc picture duplicate or full roster does not consume token`() = runTest {
        val itemId = 31000
        val npcId = 9001
        val itemDef = mockk<ItemDef> {
            every { kind } returns 0 // không phải Transform Doll
            every { specialAbility } returns EItemUseKind.NPC_PICTURE
            every { spare3 } returns npcId
        }
        val npcDef = mockk<NpcDef>(relaxed = true)
        val petSystem = mockk<PetSystem>()

        every { inventory.getBagItem(42, 0x1234) } returns ThingData(itemId = itemId, quantity = 1)
        every {
            petSystem.addFollowNpc(
                characterId = 42,
                npcId = npcId,
                npcDef = npcDef,
                canGrow = 1,
                allowDuplicateInCart = true,
            )
        } returns null
        // Null may mean duplicate or capacity. This fixture models the duplicate path.
        every { petSystem.hasFreeUnlockedFollowSlot(42) } returns true

        val npcPictureHandler = ItemHandler(
            inventorySystem = inventory,
            moneySystem = mockk(),
            groundItemManager = mockk(),
            responseSender = response,
            worldManager = mockk(),
            itemDefs = mapOf(itemId to itemDef),
            itemUseProcessor = mockk(),
            playerStateManager = mockk(),
            statCalc = mockk(),
            mountSystem = mockk(),
            flyPointSystem = mockk(),
            characterRepository = mockk(),
            petEquipSystem = pet,
            questSystem = mockk(),
            skillSystem = mockk(),
            dailyProgressSystem = null,
            petSystem = petSystem,
            npcDefs = mapOf(npcId to npcDef),
        )

        npcPictureHandler.handle(
            session,
            15,
            byteArrayOf(0x34, 0x12, 1, 0, 0, 0, 0, 1)
        )

        coVerify(exactly = 0) { inventory.removeFromBag(any(), any(), any()) }
        verify(exactly = 0) { response.sendUseItemSuccess(any(), any(), any(), any()) }
        verify(exactly = 0) { response.sendFollowNpcAdd(any(), any(), any(), any(), any(), any()) }
    }

    @Test fun `player equipment reads word bag index and complete unequip suggestion`() = runTest {
        coEvery { inventory.equipItem(42, 0x1234, any()) } returns null
        coEvery { inventory.unequipItem(42, 3) } returns -1
        handler.handle(session, 11, byteArrayOf(0x34,0x12))
        handler.handle(session, 12, byteArrayOf(3,0x34,0x12))
        coVerify(exactly = 1) { inventory.equipItem(42, 0x1234, any()) }
        coVerify(exactly = 1) { inventory.unequipItem(42, 3) }
    }

    @Test fun `pet unequip validates the entire suggested destination`() = runTest {
        coEvery { pet.unequip(42, 2, 3) } returns PetEquipSystem.UnequipResult.Fail
        handler.handle(session, 18, byteArrayOf(2,3,0x34))
        coVerify(exactly = 0) { pet.unequip(any(), any(), any()) }
        handler.handle(session, 18, byteArrayOf(2,3,0x34,0x12))
        coVerify(exactly = 1) { pet.unequip(42, 2, 3) }
    }

    @Test fun `bag and bank response index fields follow DVT UInt16 reads`() {
        val sender = InventoryResponseSender()
        val s = Capture()
        val cases: List<Pair<() -> Unit, ByteArray>> = listOf(
            { sender.sendReduceBagItem(s, 0x1234, 258) } to byteArrayOf(23,9,0,0x34,0x12,2,1,0,0),
            { sender.sendMoveBagItem(s, 0x1234, 258, 0x5678) } to byteArrayOf(23,10,0,0x34,0x12,2,1,0,0,0x78,0x56),
            { sender.sendUnequip(s, 3, 0x1234) } to byteArrayOf(23,16,0,3,0x34,0x12),
            { sender.sendEquip(s, 0x1234) } to byteArrayOf(23,17,0,0x34,0x12),
            { sender.sendPetUnequipToBag(s, 2, 3, 0x1234) } to byteArrayOf(23,22,0,2,3,0x34,0x12),
            { sender.sendPetEquip(s, 2, 0x1234) } to byteArrayOf(23,23,0,2,0x34,0x12),
            { sender.sendBankReduceItem(s, 0x1234, 258) } to byteArrayOf(30,5,0,0x34,0x12,2,1,0,0)
        )
        for ((send, expected) in cases) { send(); assertArrayEquals(expected, s.packet) }
    }

    @Test fun `bag and bank item lists retain DVT NFT ThingData alignment`() {
        val sender = InventoryResponseSender()
        val s = Capture()
        val thing = ThingData(itemId = 0x4567, quantity = 258, styleLevel = 7)
        val items = linkedMapOf(0x1234 to thing, 0x5678 to thing)
        val cases: List<Pair<() -> Unit, Int>> = listOf(
            { sender.sendAllBagItems(s, items, false) } to 6,
            { sender.sendBankAllItems(s, items) } to 3,
            { sender.sendSetBagItem(s, 0x1234, thing, true) } to 3,
            { sender.sendBankSetItem(s, 0x1234, thing) } to 3
        )
        for ((index, case) in cases.withIndex()) {
            case.first()
            val r = ByteBuffer.wrap(s.packet).order(ByteOrder.LITTLE_ENDIAN)
            r.position(case.second)
            val slots = if (index < 2) listOf(0x1234, 0x5678) else listOf(0x1234)
            for (slot in slots) {
                assertEquals(slot, r.short.toInt())
                val recordStart = r.position()
                assertEquals(0x4567, r.short.toInt())
                assertEquals(258, r.int)
                r.position(r.position() + 28) // remaining base fields before styleLv
                assertEquals(7, r.get().toInt())

                // Lua DVT NFT build consumes another 21 bytes after the 35-byte base ThingData.
                repeat(ThingDataWire.NFT_EXTENSION_SIZE) {
                    assertEquals(0, r.get().toInt())
                }
                assertEquals(ThingDataWire.SIZE, r.position() - recordStart)
            }
            if (index == 2) assertEquals(1, r.get().toInt())
            assertFalse(r.hasRemaining())
        }
    }

    @Test fun `DVT NFT extension prevents phantom next item and quantity`() {
        val sender = InventoryResponseSender()
        val s = Capture()
        val items = linkedMapOf(
            0 to ThingData(itemId = 26460, quantity = 401),
            1 to ThingData(itemId = 46240, quantity = 2),
            2 to ThingData(itemId = 46238, quantity = 1)
        )

        sender.sendAllBagItems(s, items, false)
        val r = ByteBuffer.wrap(s.packet).order(ByteOrder.LITTLE_ENDIAN)
        r.position(6)

        for ((slot, thing) in items) {
            assertEquals(slot, r.short.toInt())
            assertEquals(thing.itemId, r.short.toInt() and 0xFFFF)
            assertEquals(thing.quantity, r.int)
            r.position(r.position() + (ThingDataWire.SIZE - 6))
        }
        assertFalse(r.hasRemaining())
    }
}
