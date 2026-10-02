package tw.tsm.domain.constants

/**
 * Định nghĩa Opcode chính của bản mobile
 *
 * Nguồn: cấu trúc protocolTable[mainKind][subKind] của protocal.lua
 * Mỗi mainKind có nhiều subKind, phân phối qua định tuyến hai cấp
 */
object Opcodes {
    /** Thông báo kết nối/mất kết nối*/
    const val DISCONNECT = 0

    /** Hệ thống xác thực đăng nhập*/
    const val LOGIN = 1

    /** Hệ thống trò chuyện*/
    const val CHAT = 2

    /** Hệ thống nhân vật chính (chọn nhân vật/xuất hiện người chơi/số ngày đăng nhập)*/
    const val CHARACTER = 3

    /** Thuộc tính/trạng thái người chơi*/
    const val PLAYER_STATUS = 5

    /** Hệ thống di chuyển*/
    const val MOVE = 6

    /** Bản đồ/cảnh*/
    const val SCENE = 7

    /** Hệ thống thuộc tính chiến đấu (S→C sửa thuộc tính)*/
    const val BATTLE = 8

    /** Quản lý chiến đấu (C→S: PK/tham chiến/khán chiến, S→C: khởi tạo/kết thúc)*/
    const val BATTLE_MANAGE = 11

    /** Lệnh kỹ năng chiến đấu (C→S: ra chiêu/vật phẩm, S→C: kết quả tấn công)*/
    const val BATTLE_COMMAND = 50

    /** Biến đổi thuộc tính chiến đấu (S→C only)*/
    const val BATTLE_ATTR_CHANGE = 51

    /** Đếm ngược chiến đấu/hồi MP (S→C only)*/
    const val BATTLE_COUNTDOWN = 52

    /** Trạng thái/hiệu ứng chiến đấu (S→C only)*/
    const val BATTLE_STATUS = 53

    /** Hệ thống tạo nhân vật (bản mobile protocolTable[9])*/
    const val CREATE_CHAR = 9

    /** Gói heartbeat*/
    const val KEEPALIVE = 10

    /** Chuyển cảnh hoàn tất (C→S 12-001)*/
    const val RELOCATE = 12

    /** Hệ thống tổ đội (protocolTable[13])*/
    const val PARTY = 13

    /** Hệ thống bạn bè (protocolTable[14])*/
    const val FRIEND = 14

    /** Quản lý tướng theo sau (protocolTable[15]: thêm/thả/đổi tên/xe ngựa)*/
    const val FOLLOW_NPC = 15

    /** Hỏi đáp GM/chiến đấu (protocolTable[16])*/
    const val GM_QUIZ = 16

    /** Điều khiển tướng theo sau ra chiến đấu (protocolTable[19])*/
    const val FOLLOW_NPC_DEPLOY = 19

    /** Hệ thống sự kiện NPC (hội thoại/kích hoạt va chạm/cổng dịch chuyển)*/
    const val NPC_EVENT = 20

    /** Dòng MapNpc (S:022-001~010 NPC tuần tra/di chuyển/ẩn thân/khởi tạo)*/
    const val MAP_NPC = 22

    /** Hệ thống cửa hàng NPC*/
    const val SHOP = 23

    /** Hệ thống nhiệm vụ/cờ đánh dấu (MarkManager)*/
    const val MARK = 24

    /** Hệ thống giao dịch người chơi (vật phẩm/tướng/chuyển phát nhanh)*/
    const val TRADE = 25

    /** Hệ thống thành tựu (protocolTable[82])*/
    const val ACHIEVEMENT = 82

    /** Hệ thống thư (protocolTable[83])*/
    const val MAIL = 83

    /** Hệ thống bảng xếp hạng (protocolTable[88])*/
    const val LEADERBOARD = 88

    /** Hệ thống Sưu Tập (protocolTable[95])*/
    const val COLLECTION = 95

    /** Khu luyện công giới hạn (protocolTable[97]) */
    const val LIMIT_TIME_DUNGEON = 97

    /** Hệ thống tiền (tăng/giảm/khởi tạo)*/
    const val MONEY = 26

    /** Mua bán với NPC*/
    const val NPC_TRADE = 27

    /** Nâng cấp kỹ năng (C→S 028-001 người chơi, 028-002 tướng)*/
    const val SKILL_UPGRADE = 28

    /** Tiền Trang/ngân hàng (mainKind=29 là gói điều khiển, mainKind=30 là thao tác vật phẩm)*/
    const val BANK_CONTROL = 29
    /** Thao tác vật phẩm Tiền Trang*/
    const val BANK = 30

    /** Hệ thống lữ quán (protocolTable[31])*/
    const val INN = 31

    /** Hệ thống phím tắt (C:040-002 cài đặt, S:040-001 dữ liệu, S:040-002 kết quả)*/
    const val HOTKEY = 40

    /** Hệ thống chuyển sinh tướng (protocolTable[44])*/
    const val NPC_REINCARNATION = 44

    /** Hệ thống biểu cảm/hành động (C:032-001 biểu cảm, C:032-002 hành động, C:032-003 dừng hành động)*/
    const val EMOTE = 32

    /** Cài đặt công tắc PK (C:033-001 công tắc PK, C:033-002 công tắc tham chiến)*/
    const val PK_SETTING = 33

    /** Hệ thống liên server*/
    const val CROSS_SERVER = 35

    /** Phụ bản/thử thách*/
    const val CHALLENGE = 36

    /** Nhiệm vụ (tuyến chính/tuyến phụ)*/
    const val MISSION = 47

    /** Thất Tinh Mệnh Bàn (protocolTable[78])*/
    const val ASTROLABE = 78

    /** Hệ thống vật cưỡi (protocolTable[79])*/
    const val MOUNT = 79

    /** Cường hoá tướng (C:074-001 bắt đầu, C:074-002 xác nhận, C:074-003 huỷ)*/
    const val NPC_UPGRADE = 74

    /** Đối tượng trong cảnh*/
    const val SCENE_OBJECT = 63

    /** Hệ thống hộp cơ quan (treo trong)*/
    const val MACHINE_BOX = 65

    /** Dịch chuyển Thiên Hành Dị Năng (protocolTable[68])*/
    const val TELEPORT = 68

    /** Cửa hàng / quay thưởng (DVT Mall, protocolTable[66])*/
    const val MALL = 66

    /** Vòng quay Thôn Thực / UISlotMachine (protocolTable[72])*/
    const val SLOT_MACHINE = 72

    /** Hệ thống cờ vĩnh viễn (BitFlag)*/
    const val FOREVER_FLAG = 81

    /** Hệ thống tiêu phí giao diện (C:084-001 truy vấn 介消, C:084-002 sử dụng 介消)*/
    const val UI_SELL = 84

    /** Hệ thống dữ liệu đếm của người chơi (số ô túi đồ, số ô Tiền Trang, v.v.), dùng S:085-001/002*/
    const val ROLE_COUNT_SYSTEM = 85

    /** Hệ thống phái cử (C:086-001 truy vấn, C:086-002 xuất phát, C:086-005 hoàn thành)*/
    const val DISPATCH = 86

    /** Dữ liệu đếm của nhân vật (số ô túi đồ, v.v.)*/
    const val ROLE_COUNT = 100

    /** Kho báu (kho thứ hai song song với Tiền Trang)*/
    const val STORAGE = 102

    /** Kho tướng (protocolTable[122])*/
    const val WARRIOR_REPO = 122

    /** Hệ thống mô hình tiệc hội/sự kiện (protocolTable[124])*/
    const val ACTIVITY_MODEL = 124

    /** Hệ thống tinh linh (DVT UIElf.lua / protocolTable[107])*/
    const val ELF = 107

    /** Mỏ Thôn Thực / UIMine (protocolTable[170]) */
    const val MINING = 170

    /** Hệ thống Thần Thoại/Godskin (DVT UIGodskin.lua / protocolTable[108]) */
    const val GODSKIN = 108

    /** Gỡ lỗi/thử nghiệm*/
    const val DEBUG = 199
}

/** Mã sự kiện giao thức (tầng Socket)*/
object ProtocolEvent {
    /** Thiết lập kết nối TCP*/
    const val CONNECT = 101
    /** Mất kết nối bất thường*/
    const val EXCEPTION = 102
    /** Mất kết nối bình thường*/
    const val DISCONNECT = 103
    /** Nhận được message*/
    const val MESSAGE = 104
    /** Mạng không khả dụng*/
    const val INTERNET_UNAVAILABLE = 105
}

/** Cảnh khởi đầu của nhân vật mới (làng tân thủ, bản đồ 1600×1152 px)*/
const val DEFAULT_START_MAP: Int = 10801
/** Toạ độ pixel X khởi đầu của nhân vật mới*/
const val DEFAULT_START_X: Int = 570
/** Toạ độ pixel Y khởi đầu của nhân vật mới*/
const val DEFAULT_START_Y: Int = 770

/** Số phiên bản client*/
const val CLIENT_VERSION: Int = 258

/** Cổng server mặc định*/
const val DEFAULT_PORT: Int = 6613

/** Chu kỳ heartbeat (giây)*/
const val HEARTBEAT_INTERVAL: Int = 20
