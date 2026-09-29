package tw.tsm.domain.enums

/**
 * Trạng thái thành viên tổ đội
 *
 * Nguồn: ETeamState trong Team.lua
 */
enum class TeamState(val code: Int) {
    /** Chưa lập tổ đội*/
    NONE(0),
    /** Đội trưởng*/
    LEADER(1),
    /** Quân sư (phó đội trưởng)*/
    ADVISER(2),
    /** Đội viên*/
    MEMBER(3)
}
