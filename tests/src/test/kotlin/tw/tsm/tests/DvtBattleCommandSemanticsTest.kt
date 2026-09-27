package tw.tsm.tests

import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tw.tsm.domain.constants.BattleConstants
import tw.tsm.domain.constants.EHuman
import tw.tsm.gamelogic.battle.BattleAction
import tw.tsm.gamelogic.battle.BattleFighter
import tw.tsm.gamelogic.battle.BattleGrid
import tw.tsm.gamelogic.battle.BattleInstance
import tw.tsm.gamelogic.battle.BattleItemResolver
import tw.tsm.gamelogic.battle.BattleSkillResolver

class DvtBattleCommandSemanticsTest {
    private fun fighter(id: Long, col: Int, row: Int) = BattleFighter(
        roleId=id, kind=EHuman.PLAYER, name="f$id", col=col, row=row,
        maxHp=1000, maxSp=500, hp=1000, sp=500, level=1, element=1,
        atk=100, def=10, agi=100, intelligence=100
    )

    private fun fixture(a: BattleFighter, b: BattleFighter): BattleInstance {
        val grid = BattleGrid().also { it.place(a); it.place(b) }
        return BattleInstance(
            battleId=1, warType=0, terrain=0, eventNo=0, grid=grid,
            skillResolver=BattleSkillResolver(emptyMap()),
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
}
