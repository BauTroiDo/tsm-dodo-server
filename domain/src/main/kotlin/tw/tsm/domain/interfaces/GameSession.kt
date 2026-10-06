package tw.tsm.domain.interfaces

import tw.tsm.domain.enums.LoginKind
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.repositories.CharacterRecord

/**
 * Nhận dạng danh tính kết nối — tài khoản, server, phương thức đăng nhập
 */
interface SessionIdentity {
    /** Tên tài khoản*/
    var account: String
    /** Phương thức đăng nhập*/
    var loginKind: LoginKind
    /** ID cơ sở dữ liệu của tài khoản*/
    var accountId: Long
    /** ID server đã chọn*/
    var serverId: Int
    /** ID cơ sở dữ liệu (DBID)*/
    var dbId: Int
    /** Game Server ID */
    var gsId: Int
    /** Số thứ tự kết nối (dùng khi kết nối lại)*/
    var connectCode: Long
    /** Cấp GM (0=người chơi thường, 1+=GM)*/
    var gmLevel: Int
}

/**
 * Quản lý trạng thái kết nối
 */
interface SessionStatus {
    /** Trạng thái kết nối hiện tại*/
    var state: SessionState
    /** Đã hoàn tất chuỗi đăng nhập hay chưa*/
    var loginFinished: Boolean
    /** Mốc bắt đầu phiên online của nhân vật hiện tại, epoch millis. */
    var onlineStartMillis: Long
}

/**
 * Thông tin cơ bản của nhân vật
 */
interface SessionCharacter {
    /** ID người chơi (8 bytes)*/
    var playerId: Long
    /** ID nhân vật*/
    var roleId: Int
    /** Tên nhân vật*/
    var characterName: String
}

/**
 * Vị trí của nhân vật trong thế giới
 */
interface SessionWorld {
    /** ID bản đồ đang ở hiện tại*/
    var mapId: Int
    /** Map thường = 0; dungeon instance = 1..65535. */
    var instanceId: Int
    /** Toạ độ X hiện tại*/
    var x: Int
    /** Toạ độ Y hiện tại*/
    var y: Int
    /** Hướng hiện tại (0-7)*/
    var direction: Int
    /** Timestamp lưu vị trí lần trước (dùng chống rung, ms)*/
    var lastPositionSave: Long
    /** Timestamp lần dịch chuyển Door cuối (dùng cho hồi chiêu MeetDoor, ms)*/
    var lastDoorWarpTime: Long
    /** Bản đồ nguồn của lần dịch chuyển Door cuối (hồi chiêu MeetDoor chỉ có tác dụng trên cùng bản đồ)*/
    var lastDoorWarpMapId: Int
}

/**
 * Dữ liệu ngoại hình nhân vật — dùng cho gói tin người chơi xuất hiện S:003-000
 */
interface SessionAppearance {
    /** Giới tính (0=nam, 1=nữ)*/
    var sex: Int
    /** Thuộc tính nguyên tố*/
    var element: Int
    /** Cấp*/
    var level: Int
    /** Số lần chuyển sinh*/
    var turn: Int
    /** Nghề chuyển sinh 2/3*/
    var career: Int
    /** Nguyên tố sáng/tối chuyển sinh 3*/
    var turn3Element: Int
    /** Kiểu đầu*/
    var hair: Int
    /** Hình mặt*/
    var face: Int
    /** Bảng màu 1 (mũ/tóc/da)*/
    var color1: Int
    /** Bảng màu 2 (quần áo/phụ kiện 1/giày)*/
    var color2: Int
    /** Danh sách item ID trang bị (dùng cho ngoại hình trong sendOtherPlayerAppear)*/
    var equipItemIds: IntArray
    /** ID danh hiệu đang đeo (0 = không đeo)*/
    var titleId: Int
}

/**
 * Khả năng gửi gói tin
 */
interface SessionSender {
    /** Gửi dữ liệu gói tin thô xuống client (bao gồm header mainKind+subKind+compress)*/
    fun send(data: ByteArray)
    /** Đóng kết nối*/
    fun close()
}

/**
 * Trạng thái chiến đấu
 */
interface SessionBattle {
    /** ID trận chiến hiện tại (0=không trong trận chiến)*/
    var battleId: Long
}

/**
 * Cài đặt PK
 */
interface SessionPk {
    /** Công tắc PK (true=cho phép bị PK)*/
    var pkEnabled: Boolean
    /** Công tắc tham chiến (true=cho phép tham chiến)*/
    var joinEnabled: Boolean
}

/**
 * Trạng thái vật cưỡi — dùng cho gói tin người chơi xuất hiện S:003-000 và phát rộng S:079-007
 */
interface SessionMount {
    /** NPC ID của vật cưỡi đang cưỡi (0 = không cưỡi)*/
    var mountNpcId: Int
    /** Cấp vật cưỡi (dùng cho thuộc tính MountsLv trong gói tin người chơi khác xuất hiện)*/
    var mountLevel: Int
}

/**
 * Giai đoạn kết nối trò chơi — kết hợp tất cả interface con
 *
 * Mỗi kết nối TCP tương ứng một thể hiện GameSession.
 * Tách thành nhiều interface nhỏ để mỗi lớp chỉ phụ thuộc phần cần thiết.
 */
interface GameSession : SessionIdentity, SessionStatus, SessionCharacter, SessionWorld, SessionAppearance, SessionBattle, SessionPk, SessionMount, SessionSender

/**
 * Nạp thông tin cơ bản từ bản ghi nhân vật vào session (ID nhân vật, tên, ngoại hình)
 *
 * Dùng chung giữa LoginHandler và SelectCharHandler.
 */
fun GameSession.loadFromRecord(record: CharacterRecord) {
    roleId = record.id.toInt()
    characterName = record.name
    state = SessionState.IN_GAME
    sex = record.sex
    element = record.element
    level = record.level
    turn = record.turn
    career = record.career
    hair = record.hair
    color1 = record.color1
    color2 = record.color2
    titleId = record.titleId
}
