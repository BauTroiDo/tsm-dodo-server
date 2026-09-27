package tw.tsm.domain.enums

/**
 * 八元素列舉
 *
 * 對照 `define.lua` 的元素定義。
 */
enum class Element(val id: Int) {
    EARTH(1),
    WATER(2),
    FIRE(3),
    WIND(4),
    HEART(5),
    NONE(6),
    LIGHT(7),
    DARK(8);

    companion object {
        /** 根據 ID 取得元素，找不到回傳 NONE */
        fun fromId(id: Int): Element = entries.firstOrNull { it.id == id } ?: NONE
    }
}
