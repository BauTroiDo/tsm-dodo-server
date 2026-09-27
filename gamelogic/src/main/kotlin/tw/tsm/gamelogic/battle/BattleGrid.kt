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
        val enemyCols = if (attackerIsPlayerSide) intArrayOf(0, 1) else intArrayOf(2, 3)

        return when (fightArea) {
            EFightArea.SINGLE -> {
                // 單體：指定目標（比照 PC 版 BattleAbstract.cs changeTarget）
                val target = getAt(targetCol, targetRow)
                if (target != null && target.isAlive) {
                    listOf(target)
                } else if (target != null && isCombo) {
                    // Combo 情況下允許對目標繼續連擊（對應 PC 版 combo || !death）
                    listOf(target)
                } else {
                    // 目標已死且非 Combo：自動轉移到同側下一個存活目標（對應 PC 版 changeTarget）
                    val fallback = if (attackerIsPlayerSide) getAliveEnemySide().firstOrNull() else getAlivePlayerSide().firstOrNull()
                    if (fallback != null) listOf(fallback) else emptyList()
                }
            }
            EFightArea.COL_TWO -> {
                // 同列二人：目標 + 同列相鄰
                val targets = mutableListOf<BattleFighter>()
                getAliveAt(targetCol, targetRow)?.let { targets.add(it) }
                getAliveAt(targetCol, targetRow - 1)?.let { targets.add(it) }
                if (targets.size < 2) getAliveAt(targetCol, targetRow + 1)?.let { targets.add(it) }
                targets
            }
            EFightArea.ROW_THREE -> {
                // 同行三人：目標行的敵方所有人（最多 3）
                val targets = mutableListOf<BattleFighter>()
                for (c in enemyCols) {
                    getAliveAt(c, targetRow)?.let { targets.add(it) }
                }
                targets.take(3)
            }
            EFightArea.JUMP_THREE -> {
                // 跳三人：目標 + 跳一格
                val targets = mutableListOf<BattleFighter>()
                getAliveAt(targetCol, targetRow)?.let { targets.add(it) }
                getAliveAt(targetCol, targetRow - 2)?.let { targets.add(it) }
                getAliveAt(targetCol, targetRow + 2)?.let { targets.add(it) }
                targets
            }
            EFightArea.CROSS_FIVE -> {
                // 十字五人：目標 + 上下左右
                val targets = mutableListOf<BattleFighter>()
                getAliveAt(targetCol, targetRow)?.let { targets.add(it) }
                getAliveAt(targetCol, targetRow - 1)?.let { targets.add(it) }
                getAliveAt(targetCol, targetRow + 1)?.let { targets.add(it) }
                getAliveAt(targetCol - 1, targetRow)?.let { targets.add(it) }
                getAliveAt(targetCol + 1, targetRow)?.let { targets.add(it) }
                targets
            }
            EFightArea.SIX -> {
                // 六人：同列全部（前排或後排）
                val targets = mutableListOf<BattleFighter>()
                for (r in 0 until BattleConstants.MAX_ROW) {
                    getAliveAt(targetCol, r)?.let { targets.add(it) }
                }
                targets
            }
            EFightArea.ROW_FIVE -> {
                // 同行五人：該行所有列
                val targets = mutableListOf<BattleFighter>()
                for (c in 0 until BattleConstants.MAX_COL) {
                    getAliveAt(c, targetRow)?.let { targets.add(it) }
                }
                targets
            }
            EFightArea.ALL -> {
                // 全體：敵方所有存活
                if (attackerIsPlayerSide) getAliveEnemySide() else getAlivePlayerSide()
            }
            255 -> {
                // Lua FightField.GetEnemyIdx(255): 敵我雙方全體.
                // Preserve every occupied fighter; the client enumerates fightHum,
                // not only living targets. This matters for all-field revive/utility semantics.
                getAll()
            }
            else -> listOfNotNull(getAliveAt(targetCol, targetRow))
        }
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
