package tw.tsm.domain.constants

/**
 * DVT IOS_LeaderbordInfo_C.dat hiện tại có đúng 19 leaderboard IDs.
 *
 * Không giữ mapping legacy từ các bộ client cũ: ID/ý nghĩa ở đây bám trực tiếp
 * Data/LeaderboardData.lua + IOS_LeaderbordInfo_C.dat của Lua Đa Vũ Trụ.
 */
object LeaderboardConstants {
    const val LEVEL_ALL_ID = 1

    /** leaderboardId -> EElement (Earth=1, Water=2, Fire=3, Wind=4). */
    val ELEMENT_LEVEL_IDS: Map<Int, Int> = mapOf(
        2 to 1,
        3 to 2,
        4 to 3,
        5 to 4,
    )

    /** "Võ tướng" — scoreInfo = element, turn, level. */
    const val WARRIOR_LEVEL_ID = 6

    /** "Bảng vàng". */
    const val GOLD_ID = 7

    /** "Bảng thành tựu". */
    const val ACHIEVEMENT_ID = 20

    /**
     * Event/boss rankings require dedicated persisted scores from their own subsystem.
     * Returning them empty is evidence-safe; never substitute unrelated gold/level data.
     */
    val EVENT_SCORE_IDS: Set<Int> = setOf(
        8, 9, 10, 11, 16, 17, 19, 29, 30, 31, 44,
    )

    /** All IDs present in current DVT IOS_LeaderbordInfo_C.dat. */
    val DVT_IDS: Set<Int> = setOf(
        1, 2, 3, 4, 5, 6, 7, 20,
        44, 29, 30, 31,
        8, 9, 19,
        10, 11,
        17, 16,
    )
}
