package tw.tsm.persistence.tables

import org.jetbrains.exposed.v1.core.Table
import tw.tsm.domain.constants.DEFAULT_START_MAP
import tw.tsm.domain.constants.DEFAULT_START_X
import tw.tsm.domain.constants.DEFAULT_START_Y

/**
 * Bảng dữ liệu nhân vật
 *
 * Lưu thông tin cơ bản, tạo hình và phân điểm thuộc tính của nhân vật.
 * Các trường tương ứng định dạng gói tạo nhân vật C:009-001 của game mobile.
 */
object Characters : Table("characters") {
    /** ID tự tăng*/
    val id = long("id").autoIncrement()

    /** ID tài khoản sở hữu (ngoại khoá)*/
    val accountId = long("account_id").index()

    /** Tên nhân vật (Big5 tối đa 14 bytes, UTF-8 tối đa 21)*/
    val name = varchar("name", 32).uniqueIndex()

    /** Giới tính (1=nam, 2=nữ)*/
    val sex = integer("sex").default(1)

    /** Kiểu tóc*/
    val hair = integer("hair").default(0)

    /** Bảng màu 1 (mũ/tóc/da RGB, mã thập phân)*/
    val color1 = integer("color1").default(0)

    /** Bảng màu 2 (quần áo/phụ kiện/giày RGB, mã thập phân)*/
    val color2 = integer("color2").default(0)

    /** Ngũ hành (1=Kim, 2=Mộc, 3=Thủy, 4=Hoả, 5=Thổ)*/
    val element = integer("element").default(1)

    /** Cấp*/
    val level = integer("level").default(1)

    /** ID bản đồ (mặc định làng tân thủ 10801)*/
    val mapId = integer("map_id").default(DEFAULT_START_MAP)

    /** Toạ độ X trên bản đồ*/
    val x = integer("x").default(DEFAULT_START_X)

    /** Toạ độ Y trên bản đồ*/
    val y = integer("y").default(DEFAULT_START_Y)

    // ── Phân điểm 6 chỉ số ban đầu ──

    /** Sức mạnh (STR)*/
    val str = integer("str").default(0)

    /** Nhanh nhẹn (DEX)*/
    val dex = integer("dex").default(0)

    /** Thể lực (VIT)*/
    val vit = integer("vit").default(0)

    /** Trí tuệ (INT)*/
    val int_ = integer("int_").default(0)

    /** Tinh thần (MEN)*/
    val men = integer("men").default(0)

    /** Linh lực (SPR)*/
    val spr = integer("spr").default(0)

    /** Điểm kỹ năng khả dụng*/
    val skillPoint = integer("skill_point").default(0)

    /** Điểm thuộc tính khả dụng (nhận khi lên cấp, dùng để chia sáu chỉ số)*/
    val attrPoint = integer("attr_point").default(0)

    /** EXP hiện tại*/
    val exp = long("exp").default(0)

    /** Số lần chuyển sinh (0/1/2/3)*/
    val turn = integer("turn").default(0)

    /** Nghề nhị chuyển (0=không, 1=Hiệp Khách, 2=Bá Vương, 3=Hiền Sĩ, 4=Trích Tiên)*/
    val career = integer("career").default(0)

    // ── Điểm ghi nhớ (vị trí hồi sinh) ──

    /** ID bản đồ điểm ghi (đặt sau khi nghỉ ở lữ quán, bay về điểm ghi/thua trận sẽ dịch chuyển tới đây)*/
    val recordMapId = integer("record_map_id").default(DEFAULT_START_MAP)

    /** Toạ độ X của điểm ghi*/
    val recordX = integer("record_x").default(DEFAULT_START_X)

    /** Toạ độ Y của điểm ghi*/
    val recordY = integer("record_y").default(DEFAULT_START_Y)

    /** ID NPC tướng xuất chiến (0 = không xuất chiến)*/
    val fightNpcId = integer("fight_npc_id").default(0)

    /** ID danh hiệu đang đeo (0 = không đeo)*/
    val titleId = integer("title_id").default(0)

    // ── Điểm nguyên bảo (S:035-005 điểm thực/điểm ảo) ──

    /** Điểm nguyên bảo thực (nhận khi nạp tiền, TODO: bản gốc share ở cấp tài khoản, private server đơn giản hoá thành cấp nhân vật)*/
    val pointReal = integer("point_real").default(0)

    /** Điểm nguyên bảo ảo (nguyên bảo khoá, bản Đài Loan chưa mở tiêu phí, chỉ giữ trường)*/
    val pointVirtual = integer("point_virtual").default(0)

    /** Thời gian tạo (Unix millis)*/
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)
}
