package tw.tsm.gamelogic.systems

data class PvpLobbyBattlePlan(
    val rightIndices: List<Int>,
    val leftIndices: List<Int>,
    val rightBots: Int,
    val leftBots: Int,
)

/**
 * Pure lobby policy for the offline server. The five-slot UIDungeon room is only
 * a staging UI; actual battle sides are still created by BattleManager.
 */
object PvpLobbyPolicy {
    fun visibleBotCount(mode: PvpBotMode, realCount: Int, roomMaxPlayers: Int): Int {
        if (realCount <= 0 || roomMaxPlayers <= realCount) return 0
        val targetVisible = when (mode) {
            PvpBotMode.VS -> 2
            PvpBotMode.MIX, PvpBotMode.CHAOS -> minOf(roomMaxPlayers, mode.sideSize)
        }
        return (targetVisible - realCount).coerceAtLeast(0)
    }

    fun battlePlan(mode: PvpBotMode, realCount: Int): PvpLobbyBattlePlan? {
        if (realCount <= 0) return null
        return when (mode) {
            PvpBotMode.VS -> {
                if (realCount > 2) return null
                val leftReal = if (realCount >= 2) listOf(1) else emptyList()
                PvpLobbyBattlePlan(
                    rightIndices = listOf(0),
                    leftIndices = leftReal,
                    rightBots = 0,
                    leftBots = 1 - leftReal.size,
                )
            }
            PvpBotMode.MIX -> {
                if (realCount > mode.sideSize) return null
                PvpLobbyBattlePlan(
                    rightIndices = (0 until realCount).toList(),
                    leftIndices = emptyList(),
                    rightBots = mode.sideSize - realCount,
                    leftBots = mode.sideSize,
                )
            }
            PvpBotMode.CHAOS -> {
                if (realCount > mode.sideSize * 2) return null
                val right = (0 until realCount).filter { it % 2 == 0 }.take(mode.sideSize)
                val left = (0 until realCount).filter { it % 2 == 1 }.take(mode.sideSize)
                PvpLobbyBattlePlan(
                    rightIndices = right,
                    leftIndices = left,
                    rightBots = mode.sideSize - right.size,
                    leftBots = mode.sideSize - left.size,
                )
            }
        }
    }
}
