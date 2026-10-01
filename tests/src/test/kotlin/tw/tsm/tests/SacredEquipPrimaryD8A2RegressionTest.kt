package tw.tsm.tests

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tw.tsm.domain.constants.EAttribute
import tw.tsm.domain.constants.EItemKind
import tw.tsm.domain.data.ItemDef
import tw.tsm.domain.data.SacredEquipPrimaryDef
import tw.tsm.domain.valueobjects.ThingData
import tw.tsm.gamelogic.systems.EquipBonusCalculator

class SacredEquipPrimaryD8A2RegressionTest {
    private fun mythItem(primaryNo: Int): ItemDef = mockk {
        every { kind } returns EItemKind.MYTH
        every { attr1Kind } returns primaryNo
        every { attr1Value } returns 999
        every { attr2Kind } returns 0
        every { attr2Value } returns 0
    }

    @Test
    fun mythMainAttributeUsesSacredTableAndMythLevelDirectly() {
        val sacred = mapOf(
            13 to SacredEquipPrimaryDef(13, EAttribute.EQUIP_ATK, (0..15).map { 100 + it * 10 }),
            49 to SacredEquipPrimaryDef(49, EAttribute.ATK_PERCENT, (0..15).map { it + 1 })
        )
        val equips = mapOf(
            1 to ThingData(itemId = 50001, quantity = 1, mythLevel = 2),
            2 to ThingData(itemId = 50002, quantity = 1, mythLevel = 3)
        )
        val defs = mapOf(
            50001 to mythItem(13),
            50002 to mythItem(49)
        )

        val bonus = EquipBonusCalculator.calculateTotalBonus(
            equips = equips,
            itemDefs = defs,
            sacredEquipPrimaryDefs = sacred
        )

        assertEquals(120, bonus.equipAtk)
        assertEquals(4, bonus.atkPercent)
    }

    @Test
    fun mythAttr1RawValueIsNotTreatedAsLegacyEquipStat() {
        val sacred = mapOf(
            13 to SacredEquipPrimaryDef(13, EAttribute.EQUIP_ATK, List(16) { 50 })
        )
        val bonus = EquipBonusCalculator.calculateTotalBonus(
            equips = mapOf(1 to ThingData(itemId = 50001, quantity = 1, mythLevel = 0)),
            itemDefs = mapOf(50001 to mythItem(13)),
            sacredEquipPrimaryDefs = sacred
        )

        assertEquals(50, bonus.equipAtk)
    }
}
