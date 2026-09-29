package tw.tsm.domain.constants

/**
 * Hằng số ID hành động nhân vật
 *
 * Nguồn: liệt kê ERolePose trong RoleController.lua (dòng 198-221)
 * 14 nút hành động khả dụng trong UIAction.lua
 */
object ERolePose {
    const val WALK = 0
    const val PET_WALK = 3
    const val STAND = 8
    const val PREPARE = 16
    const val RIDING = 18
    const val LIE = 26
    const val DEFEND = 28
    const val HIT = 30
    const val JUMP = 32
    const val ATTACK = 34
    const val LONG_ATTACK = 36
    const val SPECIAL = 38
    const val LONG_SPECIAL = 40
    const val MAGIC = 42
    const val ARCHERY = 44
    const val SIT = 46
    const val WAVE = 50
    const val KISS = 58
    const val MEDITATION = 59
    const val BATH = 60
    const val TOWEL_BATH = 61
    const val NONE = -1

    /** Tập hợp 14 ID hành động khả dụng do UIAction.lua định nghĩa*/
    val USABLE_POSES = setOf(
        SIT, WAVE, HIT, PREPARE, LIE, JUMP, MAGIC, ATTACK,
        WALK, STAND, SPECIAL, ARCHERY, MEDITATION, KISS
    )

    /** Hành động chỉ dùng trong cảnh suối nước nóng/gia viên*/
    val HOT_SPRING_ONLY = setOf(SIT, LIE)
}

/** ID cảnh suối nước nóng (UIAction.lua:73-74)*/
val HOT_SPRING_MAPS = setOf(57401, 60441)
