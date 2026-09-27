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
        attributeKind: Int,
        fightWayValue: Int,
        rounds: Int
    ) = mockk<SkillDef> {
        every { attribute } returns attributeKind
        every { fightWay } returns fightWayValue
        every { round } returns rounds
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
            mapOf(skillId to skill(EAttribute.FAINT, fightWayValue = 1, rounds = 3))
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
            mapOf(skillId to skill(EAttribute.VARY, fightWayValue = 6, rounds = 3))
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
    @Test
    fun `modeled status identity with non-status attribute stays on damage path`() {
        // 13007 is client-mapped DizzyAtk/Seal, but status identity alone must not
        // classify a skill as status-only. SkillData.attribute remains authoritative.
        val skillId = 13007
        val resolver = BattleSkillResolver(
            mapOf(skillId to skill(EAttribute.ATK, fightWayValue = 1, rounds = 3))
        )
        val attacker = fighter(1, col = 2, row = 0)
        val target = fighter(2, col = 0, row = 0).also { it.agi = 0 }
        val grid = BattleGrid().also {
            it.place(attacker)
            it.place(target)
        }

        var observedDamage = false
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

            if (result.targets.single().attrChanges.any { it.attrKind == EAttribute.HP && !it.isPositive }) {
                observedDamage = true
            }
        }

        assertTrue(observedDamage)
    }


    @Test
    fun `dispel exposes exactly the lua groups it clears`() {
        // 10009 is listed by Lua SealSkill + VarySkill as a clear skill, but not
        // by Faint/Spec/Strong. S:050-001 must expose those exact Attr_Kind groups.
        val skillId = 10009
        val resolver = BattleSkillResolver(
            mapOf(skillId to skill(EAttribute.VARY, fightWayValue = 6, rounds = 0))
        )
        val attacker = fighter(1, col = 2, row = 0)
        val ally = fighter(2, col = 2, row = 1).also {
            it.setGroupedStatus(1, BattleStatus.ICE_BOUND, 3, 11014, 1)
            it.setGroupedStatus(2, BattleStatus.BOUNDARY, 3, 10010, 1)
            it.setGroupedStatus(3, BattleStatus.POISON, 3, 14015, 1)
        }
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

        assertFalse(ally.groupedStatuses.containsKey(1))
        assertFalse(ally.groupedStatuses.containsKey(2))
        assertEquals(BattleStatus.POISON, ally.groupedStatuses[3]?.status)

        val attrs = result.targets.single().attrChanges.map { it.attrKind }.toSet()
        assertTrue(EAttribute.SEAL in attrs)
        assertTrue(EAttribute.VARY in attrs)
        assertFalse(EAttribute.FAINT in attrs)
        assertFalse(EAttribute.SPEC in attrs)
        assertFalse(EAttribute.STRONG in attrs)
        assertFalse(EAttribute.HP in attrs)
    }


    @Test
    fun `fightWay eight revives the selected dead ally`() {
        val skillId = 11011
        val resolver = BattleSkillResolver(
            mapOf(skillId to skill(EAttribute.HP, fightWayValue = 8, rounds = 0))
        )
        val attacker = fighter(1, col = 2, row = 0)
        val dead = fighter(2, col = 2, row = 1).also {
            it.hp = 0
            it.isDead = true
        }
        val living = fighter(3, col = 3, row = 1)
        val grid = BattleGrid().also {
            it.place(attacker)
            it.place(dead)
            it.place(living)
        }

        val result = resolver.resolveSkill(
            attacker = attacker,
            grid = grid,
            skillId = skillId,
            targetCol = dead.col,
            targetRow = dead.row,
            terrain = 0,
            currentRound = 7
        )

        assertTrue(dead.hp > 0)
        assertFalse(dead.isDead)
        assertEquals(1000, living.hp)
        assertEquals(dead.col, result.targets.single().targetCol)
        assertEquals(dead.row, result.targets.single().targetRow)
        assertTrue(result.targets.single().attrChanges.any { it.attrKind == EAttribute.HP && it.isPositive })
    }


}
