package tw.tsm.domain.data

/**
 * Định nghĩa NPC — tương ứng `NpcData.New(reader)`
 *
 * Nguồn: `Npc_C.dat`
 * Thứ tự trường đối chiếu nghiêm ngặt dòng 236-305 của NpcData.lua.
 */
data class NpcDef(
    /** [1] Tên (UTF-16LE)*/
    val name: String,
    /** [2] Loại (liệt kê ENpc)*/
    val kind: Int,
    /** [4] Ảnh nhân vật*/
    val picId: Int,
    /** [5] Ảnh mask*/
    val maskId: Int,
    /** [6-9] Bảng màu (mũ/tóc/da / áo/phụ1/phụ2 / phụ3/phụ4/giày / nền vũ khí/phụ1 vũ khí/vòng tay)*/
    val colorTints: IntArray,
    /** [10] Có bắt được hay không (0=bắt được, 1=không bắt được)*/
    val canBeCatch: Boolean,
    /** [11] Thể hình*/
    val bodyKind: Int,
    /** [12] Loại vũ khí*/
    val weaponKind: Int,
    /** [13] Cấp độ*/
    val level: Int,
    /** [14] HP cơ bản*/
    val hpBase: Int,
    /** [15] SP cơ bản*/
    val spBase: Int,
    /** [16] Mức tăng HP*/
    val hpx: Int,
    /** [17] Mức tăng SP*/
    val spx: Int,
    /** [18] Trí lực*/
    val intelligence: Int,
    /** [19] Tấn công*/
    val atk: Int,
    /** [20] Phòng thủ*/
    val def: Int,
    /** [21] Nhanh nhẹn*/
    val agi: Int,
    /** [22] Phe đảng (0=không, 1=Nguei, 2=Thục, 3=Ngô, 4=Hoàng Cân, 5=du hiệp)*/
    val moral: Int,
    /** [23] Giá trị phe đảng*/
    val moralValue: Int,
    /** [24] Nguyên tố (1=đất, 2=nước, 3=lửa, 4=gió)*/
    val element: Int,
    /** [25-27] ID kỹ năng (tối đa 3, Role.maxNpcSkill = 3)*/
    val skills: IntArray,
    /** [35] Kỹ năng đặc hữu của tướng*/
    val specialSkill: Int,
    /** [36] Hạn chế chuyển sinh (0=chưa chuyển sinh, 1=sau chuyển sinh)*/
    val turn: Int,
    /** [38] ID kỹ năng bị động*/
    val passiveSkill: Int,
    /** [39] Cấp kỹ năng bị động*/
    val passiveSkillLv: Int,
    /** [40] Yên ngựa*/
    val saddleKind: Int,
    /** [41] Số hiệu vật phẩm tăng cường*/
    val upgradeItemId: Int,
    /** [42] Kỹ năng tăng cường*/
    val upgradeSkill: Int,
    /** [43] Điều kiện hạn chế (tách low/high byte)*/
    val limits: IntArray,
    /** [44] Dịch chuyển vật cưỡi*/
    val rideOffsetH: Int,
    /** [45] Dịch ảnh theo X*/
    val picOffsetX: Int,
    /** [46] Dịch ảnh theo Y*/
    val picOffsetY: Int,
    /** [47] Dịch HUD*/
    val hudOffsetH: Int,
    /** [48] Loại bóng (0=không dùng, 1=nhỏ, 2=dài, 3=lớn)*/
    val shadowKind: Int,
    /** [49] Cấp độ hiếm (1-3=đồng, 4=bạc, 5=vàng)*/
    val rare: Int
)
