package tw.tsm.tests

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tw.tsm.domain.constants.EFightArea
import tw.tsm.domain.constants.EHuman
import tw.tsm.gamelogic.battle.BattleFighter
import tw.tsm.gamelogic.battle.BattleGrid

class DvtBattleTargetGeometryTest {
    private fun fighter(id: Long, col: Int, row: Int) = BattleFighter(
        roleId=id, kind=EHuman.PLAYER, name="f$id", col=col, row=row,
        maxHp=1000, maxSp=1000, hp=1000, sp=1000, level=1, element=1,
        atk=100, def=10, agi=100, intelligence=100
    )

    private fun fullSide(vararg cells: Pair<Int, Int>): BattleGrid {
        val grid = BattleGrid()
        cells.forEachIndexed { i, (c, r) -> grid.place(fighter((i + 1).toLong(), c, r)) }
        return grid
    }

    private fun coords(xs: List<BattleFighter>) = xs.map { it.col to it.row }

    @Test
    fun `area two follows lua front back order from selected side`() {
        val grid = fullSide(0 to 2, 1 to 2, 2 to 2, 3 to 2)
        assertEquals(listOf(1 to 2, 0 to 2), coords(grid.resolveTargets(EFightArea.COL_TWO, 0, 2, true)))
        assertEquals(listOf(2 to 2, 3 to 2), coords(grid.resolveTargets(EFightArea.COL_TWO, 3, 2, false)))
    }

    @Test
    fun `area three is vertical around selected column`() {
        val grid = fullSide(0 to 1, 0 to 2, 0 to 3, 1 to 2)
        assertEquals(
            listOf(0 to 1, 0 to 2, 0 to 3),
            coords(grid.resolveTargets(EFightArea.ROW_THREE, 0, 2, true))
        )
    }

    @Test
    fun `area six is two columns by three rows in lua order`() {
        val cells = mutableListOf<Pair<Int,Int>>()
        for (c in listOf(1, 0)) for (r in 1..3) cells += c to r
        val grid = fullSide(*cells.toTypedArray())
        assertEquals(cells, coords(grid.resolveTargets(EFightArea.SIX, 0, 2, true)))
    }

    @Test
    fun `area eight follows selected target side not attacker side`() {
        val cells = mutableListOf<Pair<Int,Int>>()
        for (c in listOf(2, 3)) for (r in 0..4) cells += c to r
        val grid = fullSide(*cells.toTypedArray())
        assertEquals(cells, coords(grid.resolveTargets(EFightArea.ALL, 3, 2, true)))
    }
    @Test
    fun `revive selection accepts dead target and never falls back to living fighter`() {
        val grid = BattleGrid()
        val dead = fighter(1, 2, 2).also { it.hp = 0; it.isDead = true }
        val living = fighter(2, 3, 2)
        grid.place(dead)
        grid.place(living)

        assertEquals(
            listOf(dead),
            grid.resolveTargets(EFightArea.SINGLE, 2, 2, false, deadTargetsOnly = true)
        )
        assertEquals(
            emptyList<BattleFighter>(),
            grid.resolveTargets(EFightArea.SINGLE, 3, 2, false, deadTargetsOnly = true)
        )
    }


    @Test
    fun `all-field revive keeps only dead occupants`() {
        val grid = BattleGrid()
        val deadLeft = fighter(1, 0, 0).also { it.hp = 0; it.isDead = true }
        val liveLeft = fighter(2, 1, 0)
        val deadRight = fighter(3, 2, 0).also { it.hp = 0; it.isDead = true }
        val liveRight = fighter(4, 3, 0)
        listOf(deadLeft, liveLeft, deadRight, liveRight).forEach(grid::place)

        assertEquals(
            listOf(deadLeft, deadRight),
            grid.resolveTargets(255, 2, 0, false, deadTargetsOnly = true)
        )
    }


}
