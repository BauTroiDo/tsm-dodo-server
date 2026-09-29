package tw.tsm.domain.constants

/**
 * Hằng số thuộc tính — tương ứng `RoleController.lua` EAttribute
 *
 * Dùng cho trường attrKind của gói sửa thuộc tính S:008-001,
 * và biến động thuộc tính trong chiến đấu S:050-001 / S:051-001.
 */
object EAttribute {
    const val NONE = 0

    // ── Thuộc tính cơ bản ──
    const val ELEMENT = 24      // Nguyên tố (1đất 2nước 3lửa 4gió)
    const val HP = 25           // Giá trị thể lực
    const val SP = 26           // Giá trị kỹ lực
    const val INT = 27          // Trí lực
    const val ATK = 28          // Tấn công
    const val DEF = 29          // Phòng thủ
    const val AGI = 30          // Nhanh nhẹn
    const val HPX = 31          // Mức tăng HP
    const val SPX = 32          // Mức tăng SP

    // ── Cấp độ/EXP ──
    const val LV = 35           // Cấp độ
    const val EXP = 36          // EXP
    const val SKILL_POINT = 37  // Điểm kỹ năng
    const val ATTR_POINT = 38   // Điểm thuộc tính
    const val PRAC_EXP = 39     // EXP tu luyện
    const val TURN2_BALL = 40   // Điểm thiên châu

    // ── Phe đảng/Danh dự ──
    const val MORAL = 50        // Phe đảng (1Ngụy 2Thục 3Ngô 4Hoàng Cân 5Du hiệp)
    const val HONOR = 62        // Quan huân
    const val KILL_NUM = 63     // Điểm diệt địch
    const val FAITH = 64        // Lòng trung thành
    const val BATTLE_HONOR = 66 // Quan huân chiến dịch

    // ── Liên quan chuyển sinh ──
    const val TURN = 70         // Trạng thái chuyển sinh

    // ── Thuộc tính BUFF ──
    const val BUFF_INT = 71     // BUFF trí lực
    const val BUFF_ATK = 72     // BUFF tấn công
    const val BUFF_DEF = 73     // BUFF phòng thủ
    const val BUFF_AGI = 74     // BUFF nhanh nhẹn
    const val BUFF_HPX = 75     // BUFF mức tăng HP
    const val BUFF_SPX = 76     // BUFF mức tăng SP

    // ── Thuộc tính tam chuyển ──
    const val TURN3_INT = 81
    const val TURN3_ATK = 82
    const val TURN3_DEF = 83
    const val TURN3_HPX = 84
    const val TURN3_SPX = 85
    const val TURN3_AGI = 86

    // ── Cường hoá/đặc biệt ──
    const val UPGRADE_LV = 87   // Cấp cường hoá
    const val STEAL_HP = 88     // Hồi HP khi tấn công
    const val STEAL_SP = 89     // Hồi SP khi tấn công

    // ── Thuộc tính phần trăm (RoleController.lua L77-86) ──
    const val ATK_PERCENT = 101     // Tấn công(%)
    const val DEF_PERCENT = 102     // Phòng thủ(%)
    const val CRITICAL = 103        // Tỷ lệ chí mạng(%)
    const val CRIT_DAMAGE = 104     // Sát thương chí mạng(%)
    const val DODGE = 105           // Né tránh(1~30%)
    const val HIT = 106             // Chính xác(1~30%)
    const val HP_PERCENT = 107      // HP tối đa(%)
    const val SP_PERCENT = 108      // SP tối đa(%)
    const val INT_PERCENT = 109     // Trí lực(%)

    // ── Kỹ năng ──
    const val SKILL = 201           // Kỹ năng

    // ── Giá trị tối đa (RoleController.lua L96-97) ──
    const val MAX_HP = 205          // Thể lực tối đa
    const val MAX_SP = 206          // Kỹ lực tối đa

    // ── Cộng thêm từ trang bị (RoleController.lua L98-109, đối chiếu chặt chẽ với Lua) ──
    const val EQUIP_MAX_HP = 207    // Trang bị tăng HP tối đa
    const val EQUIP_MAX_SP = 208    // Trang bị tăng SP tối đa
    const val BUFF = 209            // buff tăng ích
    const val EQUIP_ATK = 210       // Tấn công thường từ trang bị
    const val EQUIP_DEF = 211       // Phòng thủ thường từ trang bị
    const val EQUIP_INT = 212       // Trí lực từ trang bị
    const val EQUIP_AGI = 214       // Nhanh nhẹn từ trang bị (lưu ý: bỏ qua 213)
    const val EXTRA_HP = 215        // HP bổ sung (Client không dùng)
    const val EXTRA_SP = 216        // SP bổ sung (Client không dùng)
    const val BOAT_SPEED = 217      // Trang bị tăng tốc thuyền
    const val EQUIP_HPX = 218       // HPX trang bị
    const val EQUIP_SPX = 219       // SPX trang bị

    // ── Trạng thái chiến đấu ──
    const val SEAL = 221        // Phong ấn
    const val VARY = 222        // Biến hình
    const val FAINT = 223       // Hôn mê
    const val SPEC = 224        // Đặc biệt
    const val STRONG = 225      // Cường hóa
    const val EQUIP_CRITICLE = 226  // Trang bị tăng xác suất chí mạng
    const val MOUNTS_LV = 227       // Cấp vật cưỡi
    const val DIVERSITY = 228       // Trạng thái đa dạng

    // ── Thông tin nghề/chuyển sinh ──
    const val CAREER = 251      // Nghề
    const val TURN3_LV = 252    // Cấp chuyển sinh 3
    const val TURN3_EXP = 253   // Kinh nghiệm chuyển sinh 3
    const val TURN3_ELEMENT = 254 // Thuộc tính sáng/tối chuyển sinh 3
    const val MARRIAGE = 255    // Trạng thái hôn nhân
}
