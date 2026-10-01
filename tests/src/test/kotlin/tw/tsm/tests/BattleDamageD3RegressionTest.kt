package tw.tsm.tests

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tw.tsm.domain.data.SkillDef
import tw.tsm.gamelogic.battle.BattleDamageCalc
import tw.tsm.gamelogic.battle.BattleSkillResolver

class BattleDamageD3RegressionTest {
    private fun skill(
        fightArea: Int = 1,
        growType: Int = 0
    ) = SkillDef(
        name = "test",
        kind = 1,
        requireSp = 10,
        element = 1,
        numerical = 0,
        attribute = 0,
        level = 1,
        fightWay = 1,
        fightArea = fightArea,
        round = 0,
        spendSecond = 0,
        hitStatus = 0,
        howMuchTimes = 1,
        limitLv = 1,
        learnPoint = 1,
        levelUpPoint = 1,
        maxLv = 10,
        preSkills = emptyList(),
        atkKind = 0,
        turnKind = 0,
        learnLimit = 1,
        useLimit = 1,
        fightWayGrowType = growType,
        description = ""
    )

    private fun damageDivisor(area: Int): Int {
        val resolver = BattleSkillResolver(emptyMap())
        val method = BattleSkillResolver::class.java.getDeclaredMethod(
            "getDamageDivisor",
            Int::class.javaPrimitiveType
        )
        method.isAccessible = true
        return method.invoke(resolver, area) as Int
    }

    private fun fightArea(skill: SkillDef, level: Int): Int {
        val resolver = BattleSkillResolver(emptyMap())
        val method = BattleSkillResolver::class.java.getDeclaredMethod(
            "getFightArea",
            SkillDef::class.java,
            Int::class.javaPrimitiveType
        )
        method.isAccessible = true
        return method.invoke(resolver, skill, level) as Int
    }

    private fun dividedBase(base: Int, divisor: Int): Int {
        val method = BattleDamageCalc::class.java.getDeclaredMethod(
            "applyMultiTargetDivision",
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType
        )
        method.isAccessible = true
        return method.invoke(BattleDamageCalc, base, divisor) as Int
    }

    @Test
    fun `fightArea divisor follows PC nb target baseline`() {
        assertEquals(1, damageDivisor(1))
        assertEquals(2, damageDivisor(2))
        assertEquals(2, damageDivisor(3))
        assertEquals(2, damageDivisor(4))
        assertEquals(3, damageDivisor(5))
        assertEquals(3, damageDivisor(6))
        assertEquals(3, damageDivisor(7))
        assertEquals(4, damageDivisor(8))
        assertEquals(1, damageDivisor(255))
    }

    @Test
    fun `multi target division uses integer truncation before defense`() {
        assertEquals(101, dividedBase(203, 2))
        assertEquals(67, dividedBase(203, 3))
        assertEquals(50, dividedBase(203, 4))
        assertEquals(203, dividedBase(203, 1))
    }

    @Test
    fun `DVT grow type one expands area and divisor by skill level`() {
        val s = skill(fightArea = 1, growType = 1)

        assertEquals(1, fightArea(s, 1))
        assertEquals(2, fightArea(s, 2))
        assertEquals(5, fightArea(s, 4))
        assertEquals(6, fightArea(s, 7))
        assertEquals(8, fightArea(s, 10))

        assertEquals(1, damageDivisor(fightArea(s, 1)))
        assertEquals(2, damageDivisor(fightArea(s, 2)))
        assertEquals(3, damageDivisor(fightArea(s, 4)))
        assertEquals(3, damageDivisor(fightArea(s, 7)))
        assertEquals(4, damageDivisor(fightArea(s, 10)))
    }

    @Test
    fun `DVT grow type two starts at row three and reaches all at level ten`() {
        val s = skill(fightArea = 3, growType = 2)

        assertEquals(3, fightArea(s, 1))
        assertEquals(5, fightArea(s, 4))
        assertEquals(6, fightArea(s, 7))
        assertEquals(8, fightArea(s, 10))
    }
}
