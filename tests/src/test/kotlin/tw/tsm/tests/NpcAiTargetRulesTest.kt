package tw.tsm.tests

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tw.tsm.domain.constants.EHuman
import tw.tsm.gamelogic.battle.BattleFighter
import tw.tsm.gamelogic.battle.BattleGrid
import tw.tsm.gamelogic.battle.NpcAiTargetRules

class NpcAiTargetRulesTest {
    private fun fighter(id: Long, col: Int, row: Int, dead: Boolean = false) =
        BattleFighter(
            roleId = id,
            kind = if (col >= 2) EHuman.PLAYER else EHuman.MINE_NPC,
            name = "f$id",
            col = col,
            row = row,
            maxHp = 1000,
            maxSp = 500,
            hp = if (dead) 0 else 1000,
            sp = 500,
            level = 1,
            element = 1,
            atk = 100,
            def = 10,
            agi = 100,
            intelligence = 100,
        ).also { it.isDead = dead }

    private fun grid(vararg fighters: BattleFighter) =
        BattleGrid().also { g -> fighters.forEach(g::place) }

    @Test
    fun `fightWay four and nineteen target living npc side`() {
        val npc = fighter(-1, 0, 0)
        val ally = fighter(-2, 1, 1)
        val enemy = fighter(3, 2, 0)
        val grid = grid(npc, ally, enemy)

        for (way in listOf(4, 19)) {
            val targets = NpcAiTargetRules.candidates(npc, grid, way)
            assertEquals(setOf(npc, ally), targets.toSet())
            assertTrue(targets.all { !it.isPlayerSide })
        }
    }

    @Test
    fun `fightWay five and eighteen can target any living battlefield unit`() {
        val npc = fighter(-1, 0, 0)
        val ally = fighter(-2, 1, 1)
        val enemy = fighter(3, 2, 0)
        val deadEnemy = fighter(4, 3, 1, dead = true)
        val grid = grid(npc, ally, enemy, deadEnemy)

        for (way in listOf(5, 18)) {
            val targets = NpcAiTargetRules.candidates(npc, grid, way)
            assertEquals(setOf(npc, ally, enemy), targets.toSet())
            assertFalse(deadEnemy in targets)
        }
    }

    @Test
    fun `ordinary attack target pool stays on opposing living side`() {
        val npc = fighter(-1, 0, 0)
        val ally = fighter(-2, 1, 1)
        val enemy = fighter(3, 2, 0)
        val deadEnemy = fighter(4, 3, 1, dead = true)
        val grid = grid(npc, ally, enemy, deadEnemy)

        val targets = NpcAiTargetRules.candidates(npc, grid, fightWay = 1)

        assertEquals(listOf(enemy), targets)
    }
}
