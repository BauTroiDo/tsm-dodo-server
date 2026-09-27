package tw.tsm.tests

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tw.tsm.domain.constants.EAttribute
import tw.tsm.domain.constants.EHuman
import tw.tsm.domain.data.SkillDef
import tw.tsm.domain.enums.BattleStatus
import tw.tsm.gamelogic.battle.BattleFighter
import tw.tsm.gamelogic.battle.BattleGrid
import tw.tsm.gamelogic.battle.BattleSkillResolver

class DvtBattleStatusExecutionTest {

    private fun fighter(id: Long, col: Int, row: Int) = BattleFighter(
        roleId = id,
        kind = EHuman.PLAYER,
        name = "fighter-$id",
        col = col,
        row = row,
        maxHp = 1000,
        maxSp = 1000,
        hp = 1000,
        sp = 1000,
        level = 1,
        element = 1,
        atk = 100,
        def = 10,
        agi = 100,
        intelligence = 100
    )

    private fun skill(
        attribute: Int,
        fightWay: Int,
        round: Int
    ) = mockk<SkillDef> {
        every { this@mockk.attribute } returns attribute
        every { this@mockk.fightWay } returns fightWay
        every { this@mockk.round } returns round
        every { fightArea } returns 1
        every { fightWayGrowType } returns 0
        every { requireSp } returns 0
        every { howMuchTimes } returns 1
        every { numerical } returns 100
        every { element } returns 1
    }

    @Test
    fun `enemy status attribute never fabricates hp damage`() {
        // 14021 = Chaos in Attack.lua and FightRoleController.FaintSkill.
        val skillId = 14021
        val resolver = BattleSkillResolver(
            mapOf(skillId to skill(EAttribute.FAINT, fightWay = 1, round = 3))
        )
        val attacker = fighter(1, col = 2, row = 0)
        val target = fighter(2, col = 0, row = 0)
        val grid = BattleGrid().also {
            it.place(attacker)
            it.place(target)
        }

        // Random hit/status rolls may differ, but a status-only skill must never
        // mutate HP or serialize an HP delta.
        repeat(100) {
            target.hp = 1000
            target.clearAllGroupedStatuses()

            val result = resolver.resolveSkill(
                attacker = attacker,
                grid = grid,
                skillId = skillId,
                targetCol = target.col,
                targetRow = target.row,
                terrain = 0,
                currentRound = 7
            )

            assertEquals(1000, target.hp)
            val targetResult = result.targets.single()
            assertFalse(targetResult.attrChanges.any { it.attrKind == EAttribute.HP })

            // If the server's existing chance policy applied Chaos this roll,
            // S:050-001 must expose Faint Attr_Kind so Lua RunEffect can HandleStatus.
            if (target.groupedStatuses[3]?.status == BattleStatus.CHAOS) {
                assertTrue(targetResult.attrChanges.any { it.attrKind == EAttribute.FAINT })
            }
        }
    }

    @Test
    fun `friendly modeled status is exposed through its lua status attribute`() {
        // 14012 = SameHeart, a client-proven Vary-group friendly status.
        val skillId = 14012
        val resolver = BattleSkillResolver(
            mapOf(skillId to skill(EAttribute.VARY, fightWay = 6, round = 3))
        )
        val attacker = fighter(1, col = 2, row = 0)
        val ally = fighter(2, col = 2, row = 1)
        val grid = BattleGrid().also {
            it.place(attacker)
            it.place(ally)
        }

        val result = resolver.resolveSkill(
            attacker = attacker,
            grid = grid,
            skillId = skillId,
            targetCol = ally.col,
            targetRow = ally.row,
            terrain = 0,
            currentRound = 7
        )

        val targetResult = result.targets.single()
        assertEquals(1000, ally.hp)
        assertTrue(targetResult.attrChanges.any { it.attrKind == EAttribute.VARY })
        assertFalse(targetResult.attrChanges.any { it.attrKind == EAttribute.HP })
        assertEquals(BattleStatus.SAME_HEART, ally.groupedStatuses[2]?.status)
    }
}
