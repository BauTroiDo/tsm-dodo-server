package tw.tsm.tests

import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.app.handlers.ItemHandler
import tw.tsm.app.handlers.RelocateHandler
import tw.tsm.data.loaders.EveDataLoader
import tw.tsm.domain.constants.EGdThing
import tw.tsm.domain.data.*
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.gamelogic.systems.*
import tw.tsm.protocol.PacketReader
import tw.tsm.protocol.PacketWriter
import java.nio.file.Files
import java.nio.file.Path

class QuestSceneGoodsTest {
    private fun scene() = SceneEveData(emptyMap(), emptyMap(), emptyMap(), emptyMap(),
        goods = mapOf(3 to EveGoodsPlacement(3, 42009, 1637, 228, 1)))
    private fun session() = mockk<GameSession>(relaxed = true) {
        every { roleId } returns 31
        every { playerId } returns 13L
        every { mapId } returns 12001
        every { state } returns SessionState.IN_GAME
    }

    @Test fun dvtEveContainsBambooLeafOnZhuoMapAtExactLocation() {
        val root = generateSequence(Path.of("").toAbsolutePath()) { it.parent }
            .first { Files.exists(it.resolve("gamedata/Eve.emg")) }
        val scenes = EveDataLoader.load(Files.readAllBytes(root.resolve("gamedata/Eve.emg")))
        val leaf = scenes.getValue(12001).goods.getValue(3)
        assertEquals(EveGoodsPlacement(3, 42009, 1637, 228, 1), leaf)
        assertTrue(scenes.values.sumOf { it.goods.size } > 400)
    }

    @Test fun groundSpawnKeepsIndicesAndFullBagRestorePreservesItem() {
        var now = 10000L
        val ground = GroundItemManager(mapOf(12001 to scene())) { now }
        val first = ground.getItems(12001).single()
        assertEquals(3, first.index)
        assertEquals(42009, first.value)
        assertEquals(first, ground.removeItem(12001, 3))
        assertNull(ground.getItem(12001, 3))
        ground.restoreItem(12001, first)
        assertEquals(first, ground.getItem(12001, 3))
        assertEquals(first, ground.removeItem(12001, 3))
        now += 1000
        assertEquals(42009, ground.getItem(12001, 3)?.value)
        assertNotEquals(3, ground.addItem(12001, EGdThing.ITEM, 10001, 0, 0))
    }

    @Test fun enteringSceneSendsDvtGroundListWithAuthoritativeIndex() = runTest {
        val ground = GroundItemManager(mapOf(12001 to scene()))
        val sess = session()
        val packets = mutableListOf<ByteArray>()
        every { sess.send(capture(packets)) } just Runs
        RelocateHandler(mockk(relaxed = true), mockk(relaxed = true), ground).handle(sess, 1, byteArrayOf())
        val reader = PacketReader(packets.single())
        assertEquals(23, reader.readUByte())
        assertEquals(4, reader.readUByte())
        assertEquals(0, reader.readUByte())
        assertEquals(EGdThing.ITEM, reader.readUByte())
        assertEquals(3, reader.readUShortLE())
        assertEquals(42009, reader.readIntLE())
        assertEquals(1637, reader.readUShortLE())
        assertEquals(228, reader.readUShortLE())
    }

    @Test fun failedPickupLeavesLeafAtSameIndexAndForgedPayloadCannotReplaceIt() = runTest {
        val ground = GroundItemManager(mapOf(12001 to scene()))
        val inventory = mockk<InventorySystem>(relaxed = true)
        coEvery { inventory.addItemToBag(any(), any(), any()) } returns null
        val response = mockk<ResponseSender>(relaxed = true)
        val handler = ItemHandler(inventory, mockk(relaxed = true), ground, response,
            mockk(relaxed = true), emptyMap(), mockk(relaxed = true), mockk(relaxed = true),
            mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
            mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true))
        fun payload(value: Int) = PacketWriter().writeShortLE(3).writeShortLE(EGdThing.ITEM)
            .writeShortLE(value).writeShortLE(1637).writeShortLE(228).toByteArray()
        handler.handle(session(), 2, payload(42009))
        assertEquals(42009, ground.getItem(12001, 3)?.value)
        handler.handle(session(), 2, payload(29124))
        handler.handle(session(), 2, byteArrayOf(3))
        assertEquals(42009, ground.getItem(12001, 3)?.value)
        coVerify(exactly = 1) { inventory.addItemToBag(31L, 42009, 1) }
        verify(exactly = 0) { response.sendSetBagItem(any(), any(), any(), any()) }
    }
}
