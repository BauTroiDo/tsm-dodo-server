package tw.tsm.tests

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tw.tsm.domain.constants.EHuman
import tw.tsm.domain.enums.BattleStatus
import tw.tsm.gamelogic.battle.BattleDamageCalc
import tw.tsm.gamelogic.battle.BattleFighter

class BattleDamageD6RegressionTest {
    private fun fighter(roleId: Long, col: Int) = BattleFighter(
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
        intelligence = 120
    )

    private fun preDefense(
        base: Int,
        attacker: BattleFighter,
        target: BattleFighter
    ): Int {
        val method = BattleDamageCalc::class.java.getDeclaredMethod(
            "applyPreDefenseStatusModifiers",
            Int::class.javaPrimitiveType,
            BattleFighter::class.java,
            BattleFighter::class.java
        )
        method.isAccessible = true
        return method.invoke(BattleDamageCalc, base, attacker, target) as Int
    }

    private fun finalDamage(reduced: Double, defending: Boolean): Int {
        val method = BattleDamageCalc::class.java.getDeclaredMethod(
            "calculateFinalDamage",
            Double::class.javaPrimitiveType,
            Double::class.javaPrimitiveType,
            Double::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType
        )
        method.isAccessible = true
        return method.invoke(
            BattleDamageCalc, reduced, 1.0, 1.0, 0, false, defending
        ) as Int
    }

    private fun status(
        fighter: BattleFighter,
        group: Int,
        battleStatus: BattleStatus,
        skillId: Int
    ) {
        fighter.setGroupedStatus(group, battleStatus, 3, skillId, 1)
    }

    @Test
    fun `classic defensive statuses scale base damage before defense`() {
        val attacker = fighter(1, 2)
        val target = fighter(2, 0)

        status(target, 2, BattleStatus.ICE_WALL, 11002)
        assertEquals(750, preDefense(1000, attacker, target))

        target.clearAllGroupedStatuses()
        status(target, 2, BattleStatus.GOLD_SHIELD, 10031)
        assertEquals(500, preDefense(1000, attacker, target))

        target.clearAllGroupedStatuses()
        status(target, 1, BattleStatus.ICE_BOUND, 20026)
        assertEquals(750, preDefense(1000, attacker, target))
    }

    @Test
    fun `ordinary ice bound no longer has fabricated one point five multiplier`() {
        val attacker = fighter(1, 2)
        val target = fighter(2, 0)
        status(target, 1, BattleStatus.ICE_BOUND, 11014)

        assertEquals(1000, preDefense(1000, attacker, target))
    }

    @Test
    fun `classic attacker statuses match PC multipliers`() {
        val attacker = fighter(1, 2)
        val target = fighter(2, 0)

        status(attacker, 2, BattleStatus.ENLARGE, 13012)
        assertEquals(1250, preDefense(1000, attacker, target))

        attacker.clearAllGroupedStatuses()
        status(attacker, 3, BattleStatus.SHRINK, 13011)
        assertEquals(750, preDefense(1000, attacker, target))

        attacker.clearAllGroupedStatuses()
        status(attacker, 5, BattleStatus.WILD_FIGHT_SOUL, 12025)
        assertEquals(1250, preDefense(1000, attacker, target))

        attacker.clearAllGroupedStatuses()
        status(attacker, 5, BattleStatus.ANGRY_MIND, 14040)
        assertEquals(1500, preDefense(1000, attacker, target))
    }

    @Test
    fun `defend keeps both PC stages`() {
        val attacker = fighter(1, 2)
        val target = fighter(2, 0)
        target.isDefending = true

        val pre = preDefense(1000, attacker, target)
        assertEquals(750, pre)
        assertEquals(375, finalDamage(pre.toDouble(), defending = true))
    }

    @Test
    fun `status mapping includes DVT shrink skill`() {
        assertEquals(BattleStatus.SHRINK, BattleDamageCalc.statusFromSkillId(13011))
    }
}
