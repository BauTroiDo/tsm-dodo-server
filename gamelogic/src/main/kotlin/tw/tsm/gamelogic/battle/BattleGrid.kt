package tw.tsm.gamelogic.battle

import tw.tsm.domain.constants.BattleConstants
import tw.tsm.domain.constants.EFightArea

/**
 * Bàn cờ chiến trường — tương ứng `FightField.lua`
 *
 * 4 cột × 5 hàng = 20 vị trí ô
 * - Cột 0-1: phe địch (bên trái, 0=hàng trước, 1=hàng sau)
 * - Cột 2-3: phe người chơi (bên phải, 2=hàng trước, 3=hàng sau)
 */
class BattleGrid {

    /** Ô bàn cờ: grid[col][row] → BattleFighter?*/
    private val grid = Array(BattleConstants.MAX_COL) { arrayOfNulls<BattleFighter>(BattleConstants.MAX_ROW) }

    /**
     * Đặt nhân vật lên bàn cờ
     */
    fun place(fighter: BattleFighter) {
        grid[fighter.col][fighter.row] = fighter
    }

    /**
     * Lấy nhân vật ở vị trí chỉ định
     */
    fun getAt(col: Int, row: Int): BattleFighter? {
        if (col < 0 || col >= BattleConstants.MAX_COL) return null
        if (row < 0 || row >= BattleConstants.MAX_ROW) return null
        return grid[col][row]
    }

    /**
     * Xoá nhân vật (chết/bỏ chạy)
     */
    fun remove(col: Int, row: Int) {
        if (col in 0 until BattleConstants.MAX_COL && row in 0 until BattleConstants.MAX_ROW) {
            grid[col][row] = null
        }
    }

    /**
     * Lấy tất cả nhân vật phe người chơi còn sống (cột 2-3, bên phải)
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
     * Lấy tất cả nhân vật địch còn sống (cột 0-1, bên trái)
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

    /** Xác chết phe người chơi vẫn nằm trên ô để fightWay=8 có thể hồi sinh. */
    fun getDeadPlayerSide(): List<BattleFighter> = getDeadSide(2..3)

    /** Xác chết phe địch vẫn nằm trên ô để NPC fightWay=8 có thể hồi sinh. */
    fun getDeadEnemySide(): List<BattleFighter> = getDeadSide(0..1)

    private fun getDeadSide(cols: IntRange): List<BattleFighter> {
        val result = mutableListOf<BattleFighter>()
        for (col in cols) {
            for (row in 0 until BattleConstants.MAX_ROW) {
                val f = grid[col][row]
                if (f != null && !f.isAlive && !f.isFlyOut) result.add(f)
            }
        }
        return result
    }

    /** Lấy tất cả nhân vật trên bàn cờ (bao gồm nhân vật đã chết nhưng chưa FlyOut).*/
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
     * Lấy tất cả nhân vật còn sống
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
     * Tính danh sách mục tiêu theo phạm vi kỹ năng
     *
     * @param fightArea phạm vi kỹ năng (EFightArea)
     * @param targetCol chỉ định cột mục tiêu
     * @param targetRow chỉ định hàng mục tiêu
     * @param attackerIsPlayerSide người tấn công có thuộc phe người chơi không
     * @return tất cả nhân vật còn sống bị ảnh hưởng
     */
    fun resolveTargets(
        fightArea: Int,
        targetCol: Int,
        targetRow: Int,
        attackerIsPlayerSide: Boolean,
        isCombo: Boolean = false,
        deadTargetsOnly: Boolean = false
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
        fun eligible(f: BattleFighter): Boolean =
            if (deadTargetsOnly) !f.isAlive else f.isAlive || isCombo

        fun add(col: Int, row: Int) {
            occupiedAt(col, row)?.let { if (eligible(it)) targets.add(it) }
        }

        when (fightArea) {
            EFightArea.SINGLE -> {
                val target = occupiedAt(targetCol, targetRow)
                if (target != null && eligible(target)) return listOf(target)

                // Revive selection is explicit in DVT Lua (fightWay 8): never retarget
                // a dead-target action to a living fighter.
                if (deadTargetsOnly) return emptyList()

                // Preserve existing server retarget behavior only for a stale normal single target.
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
                // Lua GetEnemyIdx(255): both parties/all occupied fighters. Revive
                // still obeys fightWay 8 dead-target eligibility.
                return getAll().filter(::eligible)
            }
            else -> add(targetCol, targetRow)
        }
        return targets
    }

    /**
     * Tìm ô trống trong hai cột chỉ định (ưu tiên hàng trước, xếp giữa)
     *
     * @param frontCol số cột hàng trước
     * @param backCol số cột hàng sau
     * @return (col, row) hoặc null (đã đầy)
     */
    fun findEmptySlot(frontCol: Int, backCol: Int): Pair<Int, Int>? {
        // Ưu tiên hàng trước
        for (col in intArrayOf(frontCol, backCol)) {
            // Ưu tiên giữa (row 2 → 1,3 → 0,4)
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
     * Tính số ô đã chiếm trong hai cột chỉ định
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

    /** Lấy nhân vật còn sống ở vị trí chỉ định*/
    private fun getAliveAt(col: Int, row: Int): BattleFighter? {
        val f = getAt(col, row) ?: return null
        return if (f.isAlive) f else null
    }
}
