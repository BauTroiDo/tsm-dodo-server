package tw.tsm.tests

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tw.tsm.domain.constants.EAttackResult
import tw.tsm.domain.constants.EHuman
import tw.tsm.domain.data.SkillDef
import tw.tsm.gamelogic.battle.BattleFighter
import tw.tsm.gamelogic.battle.NpcPassiveCombatRules

class NpcPassiveCombatRulesTest {
    private fun fighter(passiveSkillId: Int) = BattleFighter(
        roleId = -passiveSkillId.toLong(),
        kind = EHuman.MINE_NPC,
        name = "npc-$passiveSkillId",
        col = 0,
        row = 0,
        maxHp = 10000,
        maxSp = 1000,
        hp = 10000,
        sp = 1000,
        level = 100,
        element = 1,
        atk = 500,
        def = 300,
        agi = 200,
        intelligence = 400,
        passiveSkillId = passiveSkillId,
        passiveSkillLevel = 1
    )

    private fun skill(atkKind: Int) = SkillDef(
        name = "test",
        kind = 1,
        requireSp = 0,
        element = 1,
        numerical = 0,
        attribute = 0,
        level = 1,
        fightWay = 0,
        fightArea = 1,
        round = 0,
        spendSecond = 0,
        hitStatus = 0,
        howMuchTimes = 1,
        limitLv = 1,
        learnPoint = 0,
        levelUpPoint = 0,
        maxLv = 10,
        preSkills = emptyList(),
        atkKind = atkKind,
        turnKind = 0,
        learnLimit = 0,
        useLimit = 0,
        fightWayGrowType = 0,
        description = ""
    )

    @Test
    fun `14057 halves normal and atkKind1 physical final damage`() {
        val target = fighter(NpcPassiveCombatRules.HALF_PHYSICAL_DAMAGE)

        assertEquals(
            50,
            NpcPassiveCombatRules.applyFinalIncomingDamage(
                damage = 101,
                target = target,
                skill = null,
                attackResult = EAttackResult.HIT
            )
        )
        assertEquals(
            50,
            NpcPassiveCombatRules.applyFinalIncomingDamage(
                damage = 101,
                target = target,
                skill = skill(atkKind = 1),
                attackResult = EAttackResult.HIT
            )
        )
    }

    @Test
    fun `14057 does not reduce non physical skills`() {
        val target = fighter(NpcPassiveCombatRules.HALF_PHYSICAL_DAMAGE)

        assertEquals(
            101,
            NpcPassiveCombatRules.applyFinalIncomingDamage(
                damage = 101,
                target = target,
                skill = skill(atkKind = 0),
                attackResult = EAttackResult.HIT
            )
        )
    }

    @Test
    fun `14061 blocks only an already marked Tian Lei result`() {
        val target = fighter(NpcPassiveCombatRules.THUNDER_SHIELD)

        assertEquals(
            0,
            NpcPassiveCombatRules.applyFinalIncomingDamage(
                damage = 777,
                target = target,
                skill = skill(atkKind = 0),
                attackResult = EAttackResult.THUNDER
            )
        )
        assertEquals(
            777,
            NpcPassiveCombatRules.applyFinalIncomingDamage(
                damage = 777,
                target = target,
                skill = skill(atkKind = 0),
                attackResult = EAttackResult.HIT
            )
        )
    }

    @Test
    fun `physical classification follows existing damage atkKind contract`() {
        assertTrue(NpcPassiveCombatRules.isPhysicalAttack(null))
        assertTrue(NpcPassiveCombatRules.isPhysicalAttack(skill(atkKind = 1)))
        assertFalse(NpcPassiveCombatRules.isPhysicalAttack(skill(atkKind = 0)))
        assertFalse(NpcPassiveCombatRules.isPhysicalAttack(skill(atkKind = 2)))
    }
}
