package tw.tsm.tests

import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tw.tsm.domain.constants.EAttribute
import tw.tsm.domain.constants.EHuman
import tw.tsm.domain.enums.BattleStatus
import tw.tsm.gamelogic.battle.BattleFighter
import tw.tsm.gamelogic.battle.BattleGrid
import tw.tsm.gamelogic.battle.BattleInstance
import tw.tsm.gamelogic.battle.BattleItemResolver
import tw.tsm.gamelogic.battle.BattleSkillResolver

class DvtBattleStatusLifecycleTest {
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

    private fun battle(vararg fighters: BattleFighter): BattleInstance {
        val grid = BattleGrid()
        fighters.forEach(grid::place)
        val battle = BattleInstance(
            battleId = 1,
            warType = 0,
            terrain = 0,
            eventNo = 0,
            grid = grid,
            skillResolver = BattleSkillResolver(emptyMap()),
            itemResolver = mockk<BattleItemResolver>(relaxed = true),
            involvedNpcDefs = emptyList()
        )
        fighters.forEach(battle::addFighter)
        return battle
    }

    @Test
    fun `duration one status clears before the next command round`() = runBlocking {
        val a = fighter(1, 2, 0)
        val b = fighter(2, 0, 0)
        val battle = battle(a, b)
        a.setGroupedStatus(1, BattleStatus.ICE_BOUND, 1, 11014, battle.currentRound)

        val first = battle.resolveRound()

        assertFalse(a.groupedStatuses.containsKey(1))
        assertEquals(1, first.statusRestores.size)
        val restore = first.statusRestores.single()
        assertEquals(1, restore.statusKind)
        assertEquals(0, restore.skillId)
    }

    @Test
    fun `duration two status survives exactly one following round`() = runBlocking {
        val a = fighter(1, 2, 0)
        val b = fighter(2, 0, 0)
        val battle = battle(a, b)
        a.setGroupedStatus(1, BattleStatus.ICE_BOUND, 2, 11014, battle.currentRound)

        val first = battle.resolveRound()
        assertEquals(1, a.groupedStatuses[1]?.rounds)
        assertTrue(first.statusRestores.isEmpty())

        val second = battle.resolveRound()
        assertFalse(a.groupedStatuses.containsKey(1))
        assertEquals(1, second.statusRestores.size)
        assertEquals(0, second.statusRestores.single().skillId)
    }

    @Test
    fun `status groups expire independently and restore with generic clear skill zero`() = runBlocking {
        val a = fighter(1, 2, 0)
        val b = fighter(2, 0, 0)
        val battle = battle(a, b)
        a.setGroupedStatus(1, BattleStatus.ICE_BOUND, 1, 11014, -1)
        a.setGroupedStatus(2, BattleStatus.BOUNDARY, 2, 10010, -1)

        val first = battle.resolveRound()
        assertFalse(a.groupedStatuses.containsKey(1))
        assertEquals(1, a.groupedStatuses[2]?.rounds)
        assertEquals(listOf(1), first.statusRestores.map { it.statusKind })
        assertEquals(0, first.statusRestores.single().skillId)

        val second = battle.resolveRound()
        assertFalse(a.groupedStatuses.containsKey(2))
        assertEquals(listOf(2), second.statusRestores.map { it.statusKind })
        assertEquals(0, second.statusRestores.single().skillId)
    }

    @Test
    fun `legacy clump ticks 30 once while duration remains active`() = runBlocking {
        val a = fighter(1, 2, 0)
        val b = fighter(2, 0, 0)
        val battle = battle(a, b)
        a.setGroupedStatus(1, BattleStatus.CLUMP, 2, 10004, battle.currentRound)

        val first = battle.resolveRound()
        assertEquals(970, a.hp)
        assertEquals(1, a.groupedStatuses[1]?.rounds)
        val tick = first.actionResults.single()
        assertEquals(20001, tick.skillId)
        val hpChange = tick.targets.single().attrChanges.single()
        assertEquals(EAttribute.HP, hpChange.attrKind)
        assertEquals(30, hpChange.value)
        assertFalse(hpChange.isPositive)

        val second = battle.resolveRound()
        assertEquals(970, a.hp)
        assertTrue(second.actionResults.isEmpty())
        assertEquals(0, second.statusRestores.single().skillId)
    }

    @Test
    fun `legacy poison ticks 90 until the final expiry boundary`() = runBlocking {
        val a = fighter(1, 2, 0)
        val b = fighter(2, 0, 0)
        val battle = battle(a, b)
        a.setGroupedStatus(3, BattleStatus.POISON, 3, 14015, battle.currentRound)

        assertEquals(910, battle.resolveRound().let { a.hp })
        val second = battle.resolveRound()
        assertEquals(820, a.hp)
        assertEquals(20003, second.actionResults.single().skillId)
        val third = battle.resolveRound()
        assertEquals(820, a.hp)
        assertTrue(third.actionResults.isEmpty())
        assertEquals(3, third.statusRestores.single().statusKind)
    }

    @Test
    fun `newer clump and poison variants do not inherit legacy periodic damage`() = runBlocking {
        val a = fighter(1, 2, 0)
        val b = fighter(2, 0, 0)
        val battle = battle(a, b)
        a.setGroupedStatus(1, BattleStatus.CLUMP, 2, 20025, battle.currentRound)
        a.setGroupedStatus(3, BattleStatus.POISON, 3, 20023, battle.currentRound)

        val first = battle.resolveRound()
        assertEquals(1000, a.hp)
        assertTrue(first.actionResults.isEmpty())
        assertEquals(1, a.groupedStatuses[1]?.rounds)
        assertEquals(2, a.groupedStatuses[3]?.rounds)
    }
}
