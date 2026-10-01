package tw.tsm.tests

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tw.tsm.domain.constants.EHuman
import tw.tsm.domain.data.SkillDef
import tw.tsm.gamelogic.battle.BattleDamageCalc
import tw.tsm.gamelogic.battle.BattleFighter

class BattleDamageD1RegressionTest {
    private fun fighter() = BattleFighter(
        roleId = 1,
        kind = EHuman.PLAYER,
        name = "attacker",
        col = 2,
        row = 2,
        maxHp = 1000,
        maxSp = 500,
        hp = 1000,
        sp = 500,
        level = 50,
        element = 1,
        atk = 200,
        def = 50,
        agi = 100,
        intelligence = 120
    )

    private fun skill(
        atkKind: Int,
        numerical: Long = 0,
        level: Int = 1,
        learnLimit: Int = 1
    ) = SkillDef(
        name = "test",
        kind = 1,
        requireSp = 10,
        element = 1,
        numerical = numerical,
        attribute = 0,
        level = level,
        fightWay = 1,
        fightArea = 1,
        round = 0,
        spendSecond = 0,
        hitStatus = 0,
        howMuchTimes = 1,
        limitLv = 1,
        learnPoint = 1,
        levelUpPoint = 1,
        maxLv = 10,
        preSkills = emptyList(),
        atkKind = atkKind,
        turnKind = 0,
        learnLimit = learnLimit,
        useLimit = 1,
        fightWayGrowType = 0,
        description = ""
    )

    private fun baseDamage(
        attacker: BattleFighter,
        skill: SkillDef?,
        skillLevel: Int
    ): Int {
        val method = BattleDamageCalc::class.java.getDeclaredMethod(
            "calculateBaseDamage",
            BattleFighter::class.java,
            SkillDef::class.java,
            Int::class.javaPrimitiveType
        )
        method.isAccessible = true
        return method.invoke(BattleDamageCalc, attacker, skill, skillLevel) as Int
    }

    @Test
    fun `atkKind selects PC physical versus magic branch`() {
        val attacker = fighter()
        val magic = baseDamage(attacker, skill(atkKind = 0), 1)
        val physical = baseDamage(attacker, skill(atkKind = 1), 1)

        assertEquals(342, magic)
        assertEquals(486, physical)
        assertTrue(physical > magic)
    }

    @Test
    fun `skill level contributes to base damage`() {
        val attacker = fighter()
        val magic = skill(atkKind = 0)

        assertEquals(342, baseDamage(attacker, magic, 1))
        assertEquals(422, baseDamage(attacker, magic, 10))
    }

    @Test
    fun `numerical is not a generic attack damage multiplier`() {
        val attacker = fighter()
        val zeroNumerical = skill(atkKind = 1, numerical = 0)
        val hugeNumerical = skill(atkKind = 1, numerical = 999_999)

        assertEquals(
            baseDamage(attacker, zeroNumerical, 5),
            baseDamage(attacker, hugeNumerical, 5)
        )
    }

    @Test
    fun `normal attack keeps PC hand fight growth`() {
        assertEquals(477, baseDamage(fighter(), null, 1))
    }
}
