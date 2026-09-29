package tw.tsm.domain.enums

/**
 * Định nghĩa kênh trò chuyện
 *
 * Nguồn: tương ứng subKind của protocolTable[2] trong protocal.lua
 */
enum class ChatChannel(val code: Int) {
    /** Tin hệ thống*/
    SYSTEM(0),
    /** Kênh chung (kênh thế giới)*/
    WORLD(1),
    /** Kênh nhẹ (kênh cảnh/khu vực)*/
    SCENE(2),
    /** Kênh mật (tin nhắn riêng)*/
    WHISPER(3),
    /** Kênh GM*/
    GM(4),
    /** Kênh tổ đội*/
    TEAM(5),
    /** Kênh bang (kênh quân đoàn/công hội)*/
    ORGANIZATION(6),
    /** Kênh minh (kênh liên minh)*/
    ALLY(7);

    companion object {
        /** Lấy kênh theo subKind (subKind của S→C = mã kênh)*/
        fun fromCode(code: Int): ChatChannel? = entries.find { it.code == code }
    }
}
