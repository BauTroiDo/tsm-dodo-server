package tw.tsm.domain.interfaces

/**
 * Giao diện bộ tính thuộc tính
 *
 * Tầng domain định nghĩa interface, StatSystem của gamelogic hiện thực.
 * Để PlayerDataDto dùng được mà không phụ thuộc gamelogic.
 */
interface StatCalculator {

    /**
     * Tính HP tối đa
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
     * Tính SP tối đa
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
