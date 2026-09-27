package tw.tsm.domain.enums

/**
 * 隊伍成員狀態
 *
 * 來源：Team.lua ETeamState
 */
enum class TeamState(val code: Int) {
    /** 未組隊 */
    NONE(0),
    /** 隊長 */
    LEADER(1),
    /** 軍師（副隊長） */
    ADVISER(2),
    /** 隊員 */
    MEMBER(3)
}
