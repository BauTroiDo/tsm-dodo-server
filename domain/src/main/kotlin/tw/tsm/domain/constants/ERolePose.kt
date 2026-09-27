package tw.tsm.domain.constants

/**
 * 角色動作 ID 常數
 *
 * 來源：RoleController.lua ERolePose 列舉（第 198-221 行）
 * UIAction.lua 14 個可用動作按鈕
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

    /** UIAction.lua 定義的 14 個可用動作 ID 集合 */
    val USABLE_POSES = setOf(
        SIT, WAVE, HIT, PREPARE, LIE, JUMP, MAGIC, ATTACK,
        WALK, STAND, SPECIAL, ARCHERY, MEDITATION, KISS
    )

    /** 溫泉/家園場景限定動作 */
    val HOT_SPRING_ONLY = setOf(SIT, LIE)
}

/** 溫泉場景 ID（UIAction.lua:73-74） */
val HOT_SPRING_MAPS = setOf(57401, 60441)
