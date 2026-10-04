package tw.tsm.tests

import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import tw.tsm.domain.constants.EHuman
import tw.tsm.gamelogic.battle.BattleFighter
import tw.tsm.gamelogic.battle.BattleGrid
import tw.tsm.gamelogic.battle.BattleInstance
import tw.tsm.gamelogic.battle.BattleItemResolver
import tw.tsm.gamelogic.battle.BattleSkillResolver

class DungeonWeeklyRoundObjectiveTest {
    private fun fighter(id: Long, col: Int, row: Int, playerSide: Boolean): BattleFighter =
        BattleFighter(
            roleId = id,
            kind = if (playerSide) EHuman.PLAYER else EHuman.MINE_NPC,
            npcId = if (playerSide) 0 else id.toInt(),
            name = "f$id",
            col = col,
            row = row,
            maxHp = 1000,
            maxSp = 100,
            hp = 1000,
            sp = 100,
            level = 1,
            element = 1,
            atk = 100,
            def = 10,
            agi = 100,
            intelligence = 100,
        )

    private fun battle(winWithin: Int = 0, survive: Int = 0): BattleInstance {
        val player = fighter(1, 2, 2, true)
        val enemy = fighter(40000, 0, 2, false)
        val grid = BattleGrid()
        val battle = BattleInstance(
            battleId = 1,
            warType = 0,
            terrain = 0,
            eventNo = 0,
            grid = grid,
            skillResolver = BattleSkillResolver(emptyMap()),
            itemResolver = mockk<BattleItemResolver>(relaxed = true),
            involvedNpcDefs = emptyList(),
            dungeonId = 21,
            winWithinRounds = winWithin,
            surviveRounds = survive,
            suppressGenericRewards = true,
        )
        battle.addFighter(player)
        battle.addFighter(enemy)
        return battle
    }

    @Test
    fun `max round objective fails only after allowed final round remains uncleared`() = runBlocking {
        val battle = battle(winWithin = 3)

        repeat(2) {
            battle.resolveRound()
            assertNull(battle.checkBattleEnd())
        }

        battle.resolveRound()
        assertEquals(false, battle.checkBattleEnd()?.playerWin)
    }

    @Test
    fun `max round objective still wins when final enemy dies on boundary round`() = runBlocking {
        val battle = battle(winWithin = 3)
        repeat(2) { battle.resolveRound() }

        val enemy = battle.getAllFighters().first { it.isEnemySide }
        enemy.hp = 0
        enemy.isDead = true
        battle.resolveRound()

        assertEquals(true, battle.checkBattleEnd()?.playerWin)
    }

    @Test
    fun `limit round objective clears immediately after surviving target rounds`() = runBlocking {
        val battle = battle(survive = 10)

        repeat(9) {
            battle.resolveRound()
            assertNull(battle.checkBattleEnd())
        }

        battle.resolveRound()
        assertEquals(true, battle.checkBattleEnd()?.playerWin)
    }

    @Test
    fun `player death still loses before survival objective can clear`() = runBlocking {
        val battle = battle(survive = 10)
        repeat(9) { battle.resolveRound() }

        val player = battle.getAllFighters().first { it.isPlayerSide }
        player.hp = 0
        player.isDead = true
        battle.resolveRound()

        assertEquals(false, battle.checkBattleEnd()?.playerWin)
    }
}
