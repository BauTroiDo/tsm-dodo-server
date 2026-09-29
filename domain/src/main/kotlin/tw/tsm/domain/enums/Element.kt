package tw.tsm.domain.enums

/**
 * Liệt kê bát nguyên tố
 *
 * Đối chiếu định nghĩa nguyên tố trong `define.lua`.
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
        /** Lấy nguyên tố theo ID, không tìm thấy trả về NONE*/
        fun fromId(id: Int): Element = entries.firstOrNull { it.id == id } ?: NONE
    }
}
