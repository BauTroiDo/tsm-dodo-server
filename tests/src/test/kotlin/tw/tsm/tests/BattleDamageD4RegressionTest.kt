package tw.tsm.tests

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tw.tsm.domain.constants.EAttackResult
import tw.tsm.domain.constants.EAttribute
import tw.tsm.domain.constants.EHuman
import tw.tsm.gamelogic.battle.BattleDamageCalc
import tw.tsm.gamelogic.battle.BattleFighter
import tw.tsm.gamelogic.systems.EquipBonusCalculator

class BattleDamageD4RegressionTest {
    private fun missChance(
        attackerLevel: Int,
        targetLevel: Int,
        hit: Int,
        dodge: Int
    ): Double {
        val method = BattleDamageCalc::class.java.getDeclaredMethod(
            "calculateMissChance",
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType
        )
        method.isAccessible = true
        return method.invoke(BattleDamageCalc, attackerLevel, targetLevel, hit, dodge) as Double
    }

    private fun critMultiplier(extra: Int): Double {
        val method = BattleDamageCalc::class.java.getDeclaredMethod(
            "calculateCritMultiplier",
            Int::class.javaPrimitiveType
        )
        method.isAccessible = true
        return method.invoke(BattleDamageCalc, extra) as Double
    }

    private fun decodeAttr(kind: Int, raw: Int): Int {
        val method = EquipBonusCalculator::class.java.getDeclaredMethod(
            "decodeAttributeValue",
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType
        )
        method.isAccessible = true
        return method.invoke(EquipBonusCalculator, kind, raw) as Int
    }

    private fun fighter(
        roleId: Long,
        col: Int,
        crit: Int = 0,
        critDamage: Int = 0,
        dodge: Int = 0,
        hit: Int = 0
    ) = BattleFighter(
        roleId = roleId,
        kind = if (col >= 2) EHuman.PLAYER else EHuman.MINE_NPC,
        name = "fighter-$roleId",
        col = col,
        row = 2,
        maxHp = 5000,
        maxSp = 500,
        hp = 5000,
        sp = 500,
        level = 50,
        element = 1,
        atk = 200,
        def = 20,
        agi = 100,
        intelligence = 120,
        crit = crit,
        critDamage = critDamage,
        dodge = dodge,
        hit = hit
    )

    @Test
    fun `ordinary miss chance preserves PC baseline when mobile stats are zero`() {
        assertEquals(1.0, missChance(50, 50, 0, 0), 0.0001)
        assertEquals(3.0, missChance(50, 60, 0, 0), 0.0001)
        assertEquals(0.0, missChance(60, 50, 0, 0), 0.0001)
    }

    @Test
    fun `DVT hit and dodge offset baseline in percentage points and clamp at thirty`() {
        assertEquals(31.0, missChance(50, 50, 0, 30), 0.0001)
        assertEquals(1.0, missChance(50, 50, 30, 30), 0.0001)
        assertEquals(0.0, missChance(50, 50, 30, 0), 0.0001)
        assertEquals(31.0, missChance(50, 50, -99, 999), 0.0001)
        assertEquals(0.0, missChance(50, 50, 999, -99), 0.0001)
    }

    @Test
    fun `DVT crit damage has fifty percent base plus extra attribute`() {
        assertEquals(1.5, critMultiplier(0), 0.0001)
        assertEquals(1.7, critMultiplier(20), 0.0001)
        assertEquals(1.5, critMultiplier(-20), 0.0001)
    }

    @Test
    fun `mobile percent attributes use direct item values while legacy equip stays base100`() {
        assertEquals(4, decodeAttr(EAttribute.DODGE, 4))
        assertEquals(1, decodeAttr(EAttribute.HIT, 1))
        assertEquals(7, decodeAttr(EAttribute.CRITICAL, 7))
        assertEquals(20, decodeAttr(EAttribute.CRIT_DAMAGE, 20))
        assertEquals(49, decodeAttr(EAttribute.EQUIP_ATK, 149))
        assertEquals(0, decodeAttr(EAttribute.EQUIP_ATK, 0))
    }

    @Test
    fun `one hundred percent crit uses DVT thunder result channel`() {
        val attacker = fighter(1, 2, crit = 100, hit = 30)
        val target = fighter(2, 0)

        val result = BattleDamageCalc.calculateAttack(attacker, target, null)

        assertEquals(EAttackResult.THUNDER, result.attackResult)
        assertTrue(result.damage > 0)
    }

    @Test
    fun `zero crit remains normal hit when miss chance is zero`() {
        val attacker = fighter(1, 2, crit = 0, hit = 30)
        val target = fighter(2, 0)

        val result = BattleDamageCalc.calculateAttack(attacker, target, null)

        assertEquals(EAttackResult.HIT, result.attackResult)
        assertTrue(result.damage > 0)
    }
}
