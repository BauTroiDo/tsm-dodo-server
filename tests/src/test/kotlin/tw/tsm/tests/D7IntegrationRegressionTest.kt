package tw.tsm.tests

import io.mockk.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tw.tsm.app.response.AstrolabeResponseSender
import tw.tsm.app.response.GodskinResponseSender
import tw.tsm.domain.constants.EAttribute
import tw.tsm.domain.constants.EItemKind
import tw.tsm.domain.data.ItemDef
import tw.tsm.domain.interfaces.SessionSender
import tw.tsm.domain.valueobjects.MythAttribute
import tw.tsm.domain.valueobjects.ThingData
import tw.tsm.gamelogic.systems.EquipBonusCalculator
import tw.tsm.protocol.PacketReader

class D7IntegrationRegressionTest {
    @Test
    fun `S078-001 sends levelUp flag and exactly seven star levels`() {
        val packets = mutableListOf<ByteArray>()
        val session = mockk<SessionSender> {
            every { send(capture(packets)) } just Runs
        }

        AstrolabeResponseSender().sendLevels(
            session,
            isLevelUp = true,
            levels = intArrayOf(1, 2, 3, 4, 5, 6, 7)
        )

        val reader = PacketReader(packets.single())
        assertEquals(78, reader.readUByte())
        assertEquals(1, reader.readUByte())
        assertEquals(0, reader.readUByte())
        assertEquals(1, reader.readUByte())
        for (level in 1..7) {
            assertEquals(level, reader.readUByte())
        }
        assertEquals(0, reader.remaining)
    }

    @Test
    fun `S108-002 success sends myth level not rune slot`() {
        val packets = mutableListOf<ByteArray>()
        val session = mockk<SessionSender> {
            every { send(capture(packets)) } just Runs
        }
        val myth = ThingData(
            itemId = 13101,
            quantity = 1,
            mythLevel = 7,
            myth = listOf(
                MythAttribute(210, 11, 1),
                MythAttribute(101, 4, 2),
                MythAttribute(105, 3, 1),
                MythAttribute(106, 2, 3)
            )
        )

        GodskinResponseSender().sendEnchantResult(
            session = session,
            result = 1,
            mythSlot = 300,
            runeSlot = 999,
            mythThing = myth
        )

        val reader = PacketReader(packets.single())
        assertEquals(108, reader.readUByte())
        assertEquals(2, reader.readUByte())
        assertEquals(0, reader.readUByte())
        assertEquals(1, reader.readUByte())
        assertEquals(300, reader.readUShortLE())
        assertEquals(7, reader.readUShortLE())
        myth.myth.forEach { attr ->
            assertEquals(attr.kind, reader.readUByte())
            assertEquals(attr.value, reader.readUShortLE())
            assertEquals(attr.extra, reader.readUShortLE())
        }
        assertEquals(0, reader.remaining)
    }

    @Test
    fun `equipped myth sub attributes contribute value plus extra directly`() {
        val itemDef = mockk<ItemDef> {
            every { kind } returns EItemKind.MYTH
            every { attr1Kind } returns 0
            every { attr1Value } returns 0
            every { attr2Kind } returns 0
            every { attr2Value } returns 0
        }
        val myth = ThingData(
            itemId = 13101,
            quantity = 1,
            myth = listOf(
                MythAttribute(EAttribute.EQUIP_ATK, 9, 6),
                MythAttribute(EAttribute.ATK_PERCENT, 4, 2),
                MythAttribute(EAttribute.DODGE, 3, 1),
                MythAttribute(EAttribute.HIT, 2, 3)
            )
        )

        val bonus = EquipBonusCalculator.calculateTotalBonus(
            equips = mapOf(1 to myth),
            itemDefs = mapOf(13101 to itemDef)
        )

        assertEquals(15, bonus.equipAtk)
        assertEquals(6, bonus.atkPercent)
        assertEquals(4, bonus.dodge)
        assertEquals(5, bonus.hit)
    }
}
