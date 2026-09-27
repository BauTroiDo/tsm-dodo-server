package tw.tsm.domain.enums

/**
 * 聊天頻道定義
 *
 * 來源：protocal.lua protocolTable[2] subKind 對應
 */
enum class ChatChannel(val code: Int) {
    /** 系統訊息 */
    SYSTEM(0),
    /** 全頻（世界頻道） */
    WORLD(1),
    /** 輕頻（場景/區域頻道） */
    SCENE(2),
    /** 密頻（私人訊息） */
    WHISPER(3),
    /** GM 頻道 */
    GM(4),
    /** 隊伍頻道 */
    TEAM(5),
    /** 團頻（軍團/公會頻道） */
    ORGANIZATION(6),
    /** 盟頻（同盟頻道） */
    ALLY(7);

    companion object {
        /** 根據 subKind 取得頻道（S→C 的 subKind = 頻道代碼） */
        fun fromCode(code: Int): ChatChannel? = entries.find { it.code == code }
    }
}
