package tw.tsm.gamelogic.battle

import tw.tsm.domain.constants.BattleConstants
import tw.tsm.domain.constants.EFightArea

/**
 * 戰場棋盤 — 對應 `FightField.lua`
 *
 * 4 列 × 5 行 = 20 格位置
 * - 列 0-1：敵方（左側，0=前排, 1=後排）
 * - 列 2-3：玩家方（右側，2=前排, 3=後排）
 */
class BattleGrid {

    /** 棋盤格：grid[col][row] → BattleFighter? */
    private val grid = Array(BattleConstants.MAX_COL) { arrayOfNulls<BattleFighter>(BattleConstants.MAX_ROW) }

    /**
     * 放置角色到棋盤
     */
    fun place(fighter: BattleFighter) {
        grid[fighter.col][fighter.row] = fighter
    }

    /**
     * 取得指定位置的角色
     */
    fun getAt(col: Int, row: Int): BattleFighter? {
        if (col < 0 || col >= BattleConstants.MAX_COL) return null
        if (row < 0 || row >= BattleConstants.MAX_ROW) return null
        return grid[col][row]
    }

    /**
     * 移除角色（死亡/逃跑）
     */
    fun remove(col: Int, row: Int) {
        if (col in 0 until BattleConstants.MAX_COL && row in 0 until BattleConstants.MAX_ROW) {
            grid[col][row] = null
        }
    }

    /**
     * 取得所有存活的玩家方角色（列 2-3，右側）
     */
    fun getAlivePlayerSide(): List<BattleFighter> {
        val result = mutableListOf<BattleFighter>()
        for (col in 2..3) {
            for (row in 0 until BattleConstants.MAX_ROW) {
                val f = grid[col][row]
                if (f != null && f.isAlive) result.add(f)
            }
        }
        return result
    }

    /**
     * 取得所有存活的敵方角色（列 0-1，左側）
     */
    fun getAliveEnemySide(): List<BattleFighter> {
        val result = mutableListOf<BattleFighter>()
        for (col in 0..1) {
            for (row in 0 until BattleConstants.MAX_ROW) {
                val f = grid[col][row]
                if (f != null && f.isAlive) result.add(f)
            }
        }
        return result
    }

    /** 取得棋盤上所有角色（包含死亡但尚未 FlyOut 的角色）。 */
    fun getAll(): List<BattleFighter> {
        val result = mutableListOf<BattleFighter>()
        for (col in 0 until BattleConstants.MAX_COL) {
            for (row in 0 until BattleConstants.MAX_ROW) {
                val f = grid[col][row]
                if (f != null && !f.isFlyOut) result.add(f)
            }
        }
        return result
    }

    /**
     * 取得所有存活角色
     */
    fun getAllAlive(): List<BattleFighter> {
        val result = mutableListOf<BattleFighter>()
        for (col in 0 until BattleConstants.MAX_COL) {
            for (row in 0 until BattleConstants.MAX_ROW) {
                val f = grid[col][row]
                if (f != null && f.isAlive) result.add(f)
            }
        }
        return result
    }

    /**
     * 根據技能範圍計算目標列表
     *
     * @param fightArea 技能範圍（EFightArea）
     * @param targetCol 指定目標列
     * @param targetRow 指定目標行
     * @param attackerIsPlayerSide 攻擊者是否為玩家方
     * @return 所有被影響的存活角色
     */
    fun resolveTargets(
        fightArea: Int,
        targetCol: Int,
        targetRow: Int,
        attackerIsPlayerSide: Boolean,
        isCombo: Boolean = false
    ): List<BattleFighter> {
        fun occupiedAt(col: Int, row: Int): BattleFighter? =
            if (col in 0 until BattleConstants.MAX_COL && row in 0 until BattleConstants.MAX_ROW) getAt(col, row) else null

        // Lua FightField.GetEnemyIdx expands every normal fightArea from the selected
        // target's coordinates/side. It does not derive the area from the attacker's side.
        val targetSideCols = when (targetCol) {
            0, 1 -> intArrayOf(1, 0)
            2, 3 -> intArrayOf(2, 3)
            else -> intArrayOf()
        }

        val targets = mutableListOf<BattleFighter>()
        fun add(col: Int, row: Int) {
            occupiedAt(col, row)?.let { if (it.isAlive || isCombo) targets.add(it) }
        }

        when (fightArea) {
            EFightArea.SINGLE -> {
                val target = occupiedAt(targetCol, targetRow)
                if (target != null && (target.isAlive || isCombo)) return listOf(target)

                // Preserve existing server retarget behavior only for a stale single target.
                val fallback = if (attackerIsPlayerSide) getAliveEnemySide().firstOrNull() else getAlivePlayerSide().firstOrNull()
                return listOfNotNull(fallback)
            }
            EFightArea.COL_TWO -> {
                // Lua kind=2: front/back pair on the selected target row.
                for (col in targetSideCols) add(col, targetRow)
            }
            EFightArea.ROW_THREE -> {
                // Lua kind=3: selected column, target row -1 .. +1.
                for (row in targetRow - 1..targetRow + 1) add(targetCol, row)
            }
            EFightArea.JUMP_THREE -> {
                // kind=4 is not implemented by GetEnemyIdx in this DVT Lua snapshot.
                // Keep the historical server shape rather than inventing a new Lua contract.
                add(targetCol, targetRow)
                add(targetCol, targetRow - 2)
                add(targetCol, targetRow + 2)
            }
            EFightArea.CROSS_FIVE -> {
                // Lua kind=5: front/back pair first, then vertical neighbors.
                for (col in targetSideCols) add(col, targetRow)
                add(targetCol, targetRow - 1)
                add(targetCol, targetRow + 1)
            }
            EFightArea.SIX -> {
                // Lua kind=6: both columns of selected side x rows target-1..target+1.
                for (col in targetSideCols) {
                    for (row in targetRow - 1..targetRow + 1) add(col, row)
                }
            }
            EFightArea.ROW_FIVE -> {
                // Lua kind=7: all five rows in the selected column.
                for (row in 0 until BattleConstants.MAX_ROW) add(targetCol, row)
            }
            EFightArea.ALL -> {
                // Lua kind=8: both columns x five rows on the selected target side.
                for (col in targetSideCols) {
                    for (row in 0 until BattleConstants.MAX_ROW) add(col, row)
                }
            }
            255 -> {
                // Lua GetEnemyIdx(255): both parties/all occupied fighters.
                return getAll()
            }
            else -> add(targetCol, targetRow)
        }
        return targets
    }

    /**
     * 在指定兩列中找空格位（前排優先，居中排列）
     *
     * @param frontCol 前排列號
     * @param backCol 後排列號
     * @return (col, row) 或 null（滿了）
     */
    fun findEmptySlot(frontCol: Int, backCol: Int): Pair<Int, Int>? {
        // 前排優先
        for (col in intArrayOf(frontCol, backCol)) {
            // 優先居中（row 2 → 1,3 → 0,4）
            val order = intArrayOf(2, 1, 3, 0, 4)
            for (row in order) {
                if (row < BattleConstants.MAX_ROW && grid[col][row] == null) {
                    return col to row
                }
            }
        }
        return null
    }

    /**
     * 計算指定兩列中已佔用的格位數
     */
    fun countOccupied(col1: Int, col2: Int): Int {
        var count = 0
        for (col in intArrayOf(col1, col2)) {
            for (row in 0 until BattleConstants.MAX_ROW) {
                if (grid[col][row] != null) count++
            }
        }
        return count
    }

    /** 取得指定位置的存活角色 */
    private fun getAliveAt(col: Int, row: Int): BattleFighter? {
        val f = getAt(col, row) ?: return null
        return if (f.isAlive) f else null
    }
}
