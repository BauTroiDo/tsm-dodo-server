package tw.tsm.tests

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tw.tsm.domain.constants.EAttribute
import tw.tsm.gamelogic.battle.BattlePercentStatCalc
import tw.tsm.gamelogic.systems.EquipBonusCalculator

class BattleDamageD5RegressionTest {
    private fun decodeAttr(kind: Int, raw: Int): Int {
        val method = EquipBonusCalculator::class.java.getDeclaredMethod(
            "decodeAttributeValue",
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType
        )
        method.isAccessible = true
        return method.invoke(EquipBonusCalculator, kind, raw) as Int
    }

    @Test
    fun `DVT atk and def percent use direct item values`() {
        assertEquals(1, decodeAttr(EAttribute.ATK_PERCENT, 1))
        assertEquals(5, decodeAttr(EAttribute.ATK_PERCENT, 5))
        assertEquals(1, decodeAttr(EAttribute.DEF_PERCENT, 1))
        assertEquals(5, decodeAttr(EAttribute.DEF_PERCENT, 5))
    }

    @Test
    fun `DVT total damage percent contracts are direct values`() {
        assertEquals(7, decodeAttr(EAttribute.INCREASE_TOTAL_DAMAGE, 7))
        assertEquals(8, decodeAttr(EAttribute.DAMAGE_REDUCTION, 8))
        assertEquals(9, decodeAttr(EAttribute.REDUCE_TOTAL_DAMAGE, 9))
        assertEquals(10, decodeAttr(EAttribute.INCREASE_DAMAGE_TAKEN_SELF, 10))
    }

    @Test
    fun `legacy equip stats remain base100 encoded`() {
        assertEquals(49, decodeAttr(EAttribute.EQUIP_ATK, 149))
        assertEquals(32, decodeAttr(EAttribute.EQUIP_DEF, 132))
        assertEquals(0, decodeAttr(EAttribute.EQUIP_ATK, 0))
    }

    @Test
    fun `atk percent applies only to base atk with DVT round half up`() {
        assertEquals(152, BattlePercentStatCalc.addBasePercent(150, 1))
        assertEquals(158, BattlePercentStatCalc.addBasePercent(150, 5))
        assertEquals(156, BattlePercentStatCalc.addBasePercent(149, 5))
    }

    @Test
    fun `def percent applies only to base def with same DVT rounding`() {
        assertEquals(101, BattlePercentStatCalc.addBasePercent(100, 1))
        assertEquals(105, BattlePercentStatCalc.addBasePercent(100, 5))
        assertEquals(152, BattlePercentStatCalc.addBasePercent(150, 1))
    }
}
