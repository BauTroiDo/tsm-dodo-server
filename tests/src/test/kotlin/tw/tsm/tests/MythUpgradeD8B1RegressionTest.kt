package tw.tsm.tests

import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tw.tsm.app.response.GodskinResponseSender
import tw.tsm.domain.constants.EItemKind
import tw.tsm.domain.constants.EItemUseKind
import tw.tsm.domain.data.ItemDef
import tw.tsm.domain.interfaces.SessionSender
import tw.tsm.domain.valueobjects.MythAttribute
import tw.tsm.domain.valueobjects.ThingData
import tw.tsm.gamelogic.systems.InventorySystem
import tw.tsm.gamelogic.systems.MythUpgradeResult
import tw.tsm.gamelogic.systems.MythUpgradeSystem
import tw.tsm.protocol.PacketReader

class MythUpgradeD8B1RegressionTest {
    private fun def(kind: Int, special: Int = 0, skillLink: Int = 0): ItemDef = mockk {
        every { this@mockk.kind } returns kind
        every { specialAbility } returns special
        every { this@mockk.skillLink } returns skillLink
    }

    private fun myth(level: Int = 3) = ThingData(
        itemId = 50001, quantity = 1, mythLevel = level,
        myth = listOf(
            MythAttribute(101, 10, 1),
            MythAttribute(102, 20, 2),
            MythAttribute(105, 3, 3),
            MythAttribute(106, 4, 4),
        )
    )

    @Test
    fun `godskin stone jumps to skillLink and consumes exactly one stone`() = runBlocking {
        val inventory = mockk<InventorySystem>()
        val before = myth(3)
        val stone = ThingData(itemId = 60001, quantity = 2)
        every { inventory.getBagItem(1L, 5) } returns before
        every { inventory.getBagItem(1L, 9) } returns stone
        val changes = slot<Map<Int, ThingData?>>()
        coEvery { inventory.setBagItemsAtomic(1L, capture(changes)) } returns Unit

        val system = MythUpgradeSystem(
            inventory,
            mapOf(
                50001 to def(EItemKind.MYTH),
                60001 to def(EItemKind.SPECIAL_ABILITY, EItemUseKind.GODSKIN_STONE, 8),
            )
        )
        val result = system.upgradeWithStone(1L, 5, 9)

        assertEquals(MythUpgradeResult.RESULT_SUCCESS, result.code)
        assertEquals(8, result.updatedMyth!!.mythLevel)
        assertEquals(before.myth, result.updatedMyth!!.myth)
        assertEquals(1, changes.captured.getValue(9)!!.quantity)
        assertEquals(8, changes.captured.getValue(5)!!.mythLevel)
    }

    @Test
    fun `invalid stone or non increasing target never mutates`() = runBlocking {
        val inventory = mockk<InventorySystem>()
        every { inventory.getBagItem(1L, 5) } returns myth(8)
        every { inventory.getBagItem(1L, 9) } returns ThingData(itemId = 60001, quantity = 1)
        val system = MythUpgradeSystem(
            inventory,
            mapOf(
                50001 to def(EItemKind.MYTH),
                60001 to def(EItemKind.SPECIAL_ABILITY, EItemUseKind.GODSKIN_STONE, 8),
            )
        )

        val result = system.upgradeWithStone(1L, 5, 9)
        assertEquals(MythUpgradeResult.RESULT_INVALID, result.code)
        coVerify(exactly = 0) { inventory.setBagItemsAtomic(any(), any()) }
    }

    @Test
    fun `S108-001 success uses one byte new level and four myth attributes`() {
        val packets = mutableListOf<ByteArray>()
        val session = mockk<SessionSender> { every { send(capture(packets)) } just Runs }
        val upgraded = myth(8)

        GodskinResponseSender().sendUpgradeResult(session, 1, 300, upgraded)

        val reader = PacketReader(packets.single())
        assertEquals(108, reader.readUByte())
        assertEquals(1, reader.readUByte())
        assertEquals(0, reader.readUByte())
        assertEquals(1, reader.readUByte())
        assertEquals(300, reader.readUShortLE())
        assertEquals(8, reader.readUByte())
        upgraded.myth.forEach { attr ->
            assertEquals(attr.kind, reader.readUByte())
            assertEquals(attr.value, reader.readUShortLE())
            assertEquals(attr.extra, reader.readUShortLE())
        }
        assertEquals(0, reader.remaining)
    }
}
