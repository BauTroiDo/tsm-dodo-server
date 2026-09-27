package tw.tsm.tests

import io.mockk.every
import io.mockk.mockk
import tw.tsm.domain.constants.EAttribute
import tw.tsm.domain.data.SkillDef
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlinx.coroutines.runBlocking
import tw.tsm.domain.enums.BattleStatus
import tw.tsm.domain.constants.BattleConstants
import tw.tsm.domain.constants.EHuman
import tw.tsm.gamelogic.battle.BattleAction
import tw.tsm.gamelogic.battle.BattleFighter
import tw.tsm.gamelogic.battle.BattleGrid
import tw.tsm.gamelogic.battle.BattleInstance
import tw.tsm.gamelogic.battle.BattleItemResolver
import tw.tsm.gamelogic.battle.BattleSkillResolver

class DvtBattleCommandSemanticsTest {
    private fun fighter(id: Long, col: Int, row: Int, learnedSkills: IntArray = intArrayOf(), elementValue: Int = 1, specialEquipId: Int = 0) = BattleFighter(
        roleId=id, kind=EHuman.PLAYER, name="f$id", col=col, row=row,
        maxHp=1000, maxSp=500, hp=1000, sp=500, level=1, element=elementValue,
        atk=100, def=10, agi=100, intelligence=100, skills=learnedSkills, specialEquipItemId=specialEquipId
    )

    private fun skill(fightWayValue: Int) = mockk<SkillDef> {
        every { fightWay } returns fightWayValue
        every { requireSp } returns 10
        every { fightArea } returns 1
        every { fightWayGrowType } returns 0
        every { attribute } returns EAttribute.HP
        every { round } returns 0
        every { howMuchTimes } returns 1
        every { numerical } returns 100
        every { element } returns 2
    }

    private fun fixture(a: BattleFighter, b: BattleFighter, skills: Map<Int, SkillDef> = emptyMap()): BattleInstance {
        val grid = BattleGrid().also { it.place(a); it.place(b) }
        return BattleInstance(
            battleId=1, warType=0, terrain=0, eventNo=0, grid=grid,
            skillResolver=BattleSkillResolver(skills),
            itemResolver=mockk<BattleItemResolver>(relaxed=true),
            involvedNpcDefs=emptyList()
        ).also { it.addFighter(a); it.addFighter(b) }
    }

    @Test
    fun `duplicate command cannot replace already accepted action`() {
        val actor = fighter(1, 2, 0)
        val target = fighter(2, 0, 0)
        val battle = fixture(actor, target)
        val first = BattleAction(BattleAction.KIND_SKILL, BattleConstants.HAND_FIGHT_SKILL_ID, 0, 0)
        val replay = BattleAction(BattleAction.KIND_SKILL, BattleConstants.HAND_FIGHT_SKILL_ID, 0, 1)

        assertTrue(battle.setPlayerAction(actor.roleId, actor.col, actor.row, first))
        assertFalse(battle.setPlayerAction(actor.roleId, actor.col, actor.row, replay))
        assertEquals(first, actor.pendingAction)
    }

    @Test
    fun `dead actor cannot queue a command`() {
        val actor = fighter(1, 2, 0).also { it.hp=0; it.isDead=true }
        val target = fighter(2, 0, 0)
        val battle = fixture(actor, target)
        val action = BattleAction(BattleAction.KIND_SKILL, BattleConstants.HAND_FIGHT_SKILL_ID, 0, 0)

        assertFalse(battle.setPlayerAction(actor.roleId, actor.col, actor.row, action))
        assertFalse(actor.hasActed)
    }
    @Test
    fun `fightWay eight accepts dead friendly target at queue time`() {
        val reviveId = 11011
        val actor = fighter(1, 2, 0)
        val deadAlly = fighter(2, 3, 0).also { it.hp=0; it.isDead=true }
        val battle = fixture(actor, deadAlly, mapOf(reviveId to skill(8)))
        val action = BattleAction(BattleAction.KIND_SKILL, reviveId, deadAlly.col, deadAlly.row)

        assertTrue(battle.setPlayerAction(actor.roleId, actor.col, actor.row, action))
        assertEquals(action, actor.pendingAction)
    }

    @Test
    fun `fightWay eight rejects living friendly target`() {
        val reviveId = 11011
        val actor = fighter(1, 2, 0)
        val livingAlly = fighter(2, 3, 0)
        val battle = fixture(actor, livingAlly, mapOf(reviveId to skill(8)))
        val action = BattleAction(BattleAction.KIND_SKILL, reviveId, livingAlly.col, livingAlly.row)

        assertFalse(battle.setPlayerAction(actor.roleId, actor.col, actor.row, action))
    }

    @Test
    fun `ordinary friendly skill still rejects dead target`() {
        val healId = 11001
        val actor = fighter(1, 2, 0)
        val deadAlly = fighter(2, 3, 0).also { it.hp=0; it.isDead=true }
        val battle = fixture(actor, deadAlly, mapOf(healId to skill(6)))
        val action = BattleAction(BattleAction.KIND_SKILL, healId, deadAlly.col, deadAlly.row)

        assertFalse(battle.setPlayerAction(actor.roleId, actor.col, actor.row, action))
    }


    @Test
    fun `round end clears accepted command and allows next round submission`() = runBlocking {
        val actor = fighter(1, 2, 0)
        val target = fighter(2, 0, 0)
        val battle = fixture(actor, target)
        val action = BattleAction(BattleAction.KIND_SKILL, BattleConstants.HAND_FIGHT_SKILL_ID, target.col, target.row)

        assertTrue(battle.setPlayerAction(actor.roleId, actor.col, actor.row, action))
        battle.resolveRound()

        assertFalse(actor.hasActed)
        assertEquals(null, actor.pendingAction)
        assertTrue(battle.setPlayerAction(actor.roleId, actor.col, actor.row, action))
    }

    @Test
    fun `blocked queued actor is cleared without executing and can act after status clears`() = runBlocking {
        val actor = fighter(1, 2, 0)
        val target = fighter(2, 0, 0)
        val battle = fixture(actor, target)
        val action = BattleAction(BattleAction.KIND_SKILL, BattleConstants.HAND_FIGHT_SKILL_ID, target.col, target.row)

        assertTrue(battle.setPlayerAction(actor.roleId, actor.col, actor.row, action))
        actor.setGroupedStatus(1, BattleStatus.ICE_BOUND, 2, 11014, battle.currentRound)
        val result = battle.resolveRound()

        assertTrue(result.actionResults.none { it.attackerCol == actor.col && it.attackerRow == actor.row })
        assertFalse(actor.hasActed)
        assertEquals(null, actor.pendingAction)

        actor.clearAllGroupedStatuses()
        assertTrue(battle.setPlayerAction(actor.roleId, actor.col, actor.row, action))
    }


    @Test
    fun `Lua forbidden non battle skill is rejected even when learned`() {
        val forbiddenId = 14003
        val actor = fighter(1, 2, 0, intArrayOf(forbiddenId))
        val target = fighter(2, 0, 0)
        val battle = fixture(actor, target, mapOf(forbiddenId to skill(1)))
        val action = BattleAction(BattleAction.KIND_SKILL, forbiddenId, target.col, target.row)

        assertFalse(battle.setPlayerAction(actor.roleId, actor.col, actor.row, action))
        assertFalse(actor.hasActed)
        assertEquals(null, actor.pendingAction)
    }


    @Test
    fun `earth summon requires Lua emblem mapping`() {
        val summonId = 10016
        val target = fighter(2, 0, 0)
        val valid = fighter(1, 2, 0, intArrayOf(summonId), elementValue=1, specialEquipId=23086)
        val validBattle = fixture(valid, target, mapOf(summonId to skill(1)))
        val action = BattleAction(BattleAction.KIND_SKILL, summonId, target.col, target.row)
        assertTrue(validBattle.setPlayerAction(valid.roleId, valid.col, valid.row, action))

        val invalid = fighter(3, 2, 0, intArrayOf(summonId), elementValue=1, specialEquipId=23087)
        val invalidTarget = fighter(4, 0, 0)
        val invalidBattle = fixture(invalid, invalidTarget, mapOf(summonId to skill(1)))
        assertFalse(invalidBattle.setPlayerAction(invalid.roleId, invalid.col, invalid.row, action))
    }


}
