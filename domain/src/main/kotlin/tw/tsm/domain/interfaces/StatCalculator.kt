package tw.tsm.domain.interfaces

/**
 * 屬性計算器介面
 *
 * domain 層定義介面，gamelogic 的 StatSystem 實作。
 * 讓 PlayerDataDto 可以在不依賴 gamelogic 的情況下使用。
 */
interface StatCalculator {

    /**
     * 計算最大 HP
     */
    fun calculateMaxHp(
        level: Int,
        hpx: Int,
        equipMaxHp: Int = 0,
        extraHp: Int = 0,
        turn: Int = 0,
        career: Int = 0,
        turn3Lv: Int = 0
    ): Long

    /**
     * 計算最大 SP
     */
    fun calculateMaxSp(
        level: Int,
        spx: Int,
        equipMaxSp: Int = 0,
        extraSp: Int = 0,
        turn: Int = 0,
        career: Int = 0,
        turn3Lv: Int = 0
    ): Int
}
