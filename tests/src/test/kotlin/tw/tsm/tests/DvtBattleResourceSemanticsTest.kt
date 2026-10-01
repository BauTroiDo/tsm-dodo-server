package tw.tsm.tests

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tw.tsm.domain.constants.EAttribute
import tw.tsm.domain.constants.EHuman
import tw.tsm.domain.data.SkillDef
import tw.tsm.gamelogic.battle.BattleFighter
import tw.tsm.gamelogic.battle.BattleGrid
import tw.tsm.gamelogic.battle.BattleSkillResolver

class DvtBattleResourceSemanticsTest {
    private fun fighter(id: Long, col: Int, row: Int) = BattleFighter(
        roleId=id, kind=EHuman.PLAYER, name="f$id", col=col, row=row,
        maxHp=1000, maxSp=500, hp=1000, sp=500, level=1, element=1,
        atk=100, def=10, agi=100, intelligence=100
    )

    private fun skill(requireSpValue: Int) = mockk<SkillDef> {
        every { attribute } returns EAttribute.ATK
        every { fightWay } returns 1
        every { round } returns 0
        every { fightArea } returns 1
        every { fightWayGrowType } returns 0
        every { requireSp } returns requireSpValue
        every { howMuchTimes } returns 1
        every { numerical } returns 100
        every { element } returns 1
        every { level } returns 1
        every { atkKind } returns 0
        every { learnLimit } returns 1
    }

    @Test
    fun `requireSp one consumes all current SP after valid target resolution`() {
        val id = 12001
        val resolver = BattleSkillResolver(mapOf(id to skill(1)))
        val attacker = fighter(1, 2, 0).also { it.sp = 137 }
        val target = fighter(2, 0, 0)
        val grid = BattleGrid().also { it.place(attacker); it.place(target) }

        val result = resolver.resolveSkill(attacker, grid, id, 0, 0, 0)

        assertEquals(137, result.spConsumed)
        assertEquals(0, attacker.sp)
        assertTrue(result.targets.isNotEmpty())
    }

    @Test
    fun `invalid target does not consume SP`() {
        val id = 12001
        val resolver = BattleSkillResolver(mapOf(id to skill(25)))
        val attacker = fighter(1, 2, 0).also { it.sp = 137 }
        val grid = BattleGrid().also { it.place(attacker) }

        val result = resolver.resolveSkill(attacker, grid, id, 0, 0, 0)

        assertEquals(0, result.spConsumed)
        assertEquals(137, attacker.sp)
        assertTrue(result.targets.isEmpty())
    }
}
