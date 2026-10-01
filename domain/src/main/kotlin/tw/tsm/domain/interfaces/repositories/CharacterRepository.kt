package tw.tsm.domain.interfaces.repositories

/**
 * Interface truy xuất dữ liệu nhân vật
 */
interface CharacterRepository {
    /**
     * Truy vấn mọi nhân vật thuộc tài khoản
     */
    suspend fun findByAccountId(accountId: Long): List<CharacterRecord>

    /**
     * Truy vấn theo ID nhân vật
     */
    suspend fun findById(characterId: Long): CharacterRecord?

    /**
     * Kiểm tra tên nhân vật đã tồn tại chưa
     */
    suspend fun isNameTaken(name: String): Boolean

    /**
     * Tạo nhân vật mới
     *
     * @return ID nhân vật mới
     */
    suspend fun create(accountId: Long, data: CharacterCreateData): Long

    /**
     * Cập nhật cấp độ nhân vật
     */
    suspend fun updateLevel(characterId: Long, level: Int)

    /** Cập nhật số lần chuyển sinh và nghề nhị chuyển.*/
    suspend fun updateTurnCareer(characterId: Long, turn: Int, career: Int)

    /** Cập nhật ngoại hình sau chuyển sinh.*/
    suspend fun updateTurnAppearance(characterId: Long, hair: Int, color1: Int, color2: Int)

    /**
     * Cập nhật vị trí nhân vật (lưu khi mất kết nối/đăng xuất)
     */
    suspend fun updatePosition(characterId: Long, mapId: Int, x: Int, y: Int)

    /**
     * Cập nhật điểm kỹ năng của nhân vật
     */
    suspend fun updateSkillPoint(characterId: Long, skillPoint: Int)

    /**
     * Phân điểm thuộc tính — tăng field lục chỉ số được chỉ định và khấu trừ attrPoint
     *
     * @param characterId ID nhân vật
     * @param column tên field DB cần tăng (str/dex/vit/int_/men/spr)
     * @param addValue số điểm tăng thêm
     * @param newAttrPoint số điểm thuộc tính còn lại sau khấu trừ
     */
    suspend fun addStatPoint(characterId: Long, column: String, addValue: Int, newAttrPoint: Int)

    /** Chỉ cập nhật điểm thuộc tính còn lại (dùng khi stat được lưu ở bảng Turn3). */
    suspend fun updateAttrPoint(characterId: Long, newAttrPoint: Int)

    /**
     * Cập nhật EXP và cấp độ nhân vật (gọi khi thưởng chiến đấu/lưu khi mất kết nối)
     */
    suspend fun updateExpAndLevel(characterId: Long, exp: Long, level: Int)

    /**
     * Cập nhật điểm ghi nhớ (đặt sau khi nghỉ tại lữ quán; bay về điểm ghi nhớ/thua trận sẽ dịch chuyển đến đây)
     */
    suspend fun updateRecordPoint(characterId: Long, mapId: Int, x: Int, y: Int)

    /**
     * Truy vấn điểm ghi nhớ
     *
     * @return (mapId, x, y) hoặc null (khi nhân vật không tồn tại)
     */
    suspend fun getRecordPoint(characterId: Long): Triple<Int, Int, Int>?

    /**
     * Cập nhật toàn bộ trạng thái runtime của nhân vật (lưu khi mất kết nối/đăng xuất)
     *
     * Lưu exp/level + attrPoint/skillPoint + 6 chỉ số, đảm bảo trạng thái sau lên cấp/phân điểm không bị mất.
     * ⚠️ Bảng Characters không có field hp/sp, HP/SP được khởi tạo từ maxHp/maxSp khi đăng nhập.
     */
    suspend fun updatePlayerState(
        characterId: Long, exp: Long, level: Int,
        attrPoint: Int, skillPoint: Int,
        intelligence: Int, atk: Int, def: Int, agi: Int, hpx: Int, spx: Int
    )

    /**
     * Cập nhật NPC ID của tướng xuất chiến (lưu khi mất kết nối/đăng xuất)
     *
     * @param fightNpcId NPC ID tướng xuất chiến, 0 có nghĩa là không xuất chiến
     */
    suspend fun updateFightNpcId(characterId: Long, fightNpcId: Int)

    /**
     * Cập nhật ID danh hiệu đang đeo hiện tại
     *
     * @param titleId ID danh hiệu, 0 có nghĩa là không đeo
     */
    suspend fun updateTitleId(characterId: Long, titleId: Int)

    /**
     * Lưu độ thân thiện một phe DVT.
     * @param faction 1=Ngụy, 2=Thục, 3=Ngô, 4=Khăn Vàng, 5=Du Hiệp
     * @param value giá trị tuyệt đối mới
     */
    suspend fun updateMoral(characterId: Long, faction: Int, value: Int)

    /**
     * Lưu điểm diệt địch DVT (EAttribute.KillNum).
     * Client tự suy ra Honor = Calc.GetHonor(KillNum); hiện tại công thức DVT là 1:1.
     */
    suspend fun updateKillNum(characterId: Long, value: Int)

    /**
     * Truy vấn dữ liệu nền tảng xếp hạng của mọi nhân vật
     *
     * Dùng cho việc làm mới cache định kỳ của bảng xếp hạng, chỉ lấy các field cần thiết.
     */
    suspend fun findAllForLeaderboard(): List<LeaderboardCharRecord>

    /**
     * Truy vấn điểm nguyên bảo
     *
     * @return (điểm thực, điểm ảo), nếu nhân vật không tồn tại trả (0, 0)
     */
    suspend fun loadPoints(characterId: Long): Pair<Int, Int>

    /**
     * Lưu điểm nguyên bảo
     *
     * @param pointReal điểm thực (nguyên bảo nạp)
     * @param pointVirtual điểm ảo (nguyên bảo khoá)
     */
    suspend fun savePoints(characterId: Long, pointReal: Int, pointVirtual: Int)
}

/**
 * Object giá trị ghi nhận của nhân vật
 */
data class CharacterRecord(
    val id: Long,
    val accountId: Long,
    val name: String,
    val sex: Int = 1,
    val hair: Int = 0,
    val color1: Int = 0,
    val color2: Int = 0,
    val element: Int = 1,
    val level: Int = 1,
    val mapId: Int = 0,
    val x: Int = 0,
    val y: Int = 0,
    val str: Int = 0,
    val dex: Int = 0,
    val vit: Int = 0,
    val int_: Int = 0,
    val men: Int = 0,
    val spr: Int = 0,
    val skillPoint: Int = 0,
    val attrPoint: Int = 0,
    val exp: Long = 0,
    val turn: Int = 0,
    val career: Int = 0,
    val fightNpcId: Int = 0,
    val titleId: Int = 0,
    /** Điểm diệt địch; Mobile DVT tính Honor = Calc.GetHonor(killNum), hiện là 1:1. */
    val killNum: Int = 0,
    /** 1 Ngụy, 2 Thục, 3 Ngô, 4 Khăn Vàng, 5 Du Hiệp. */
    val moral: IntArray = intArrayOf(0, 0, 0, 0, 0)
)

/**
 * Dữ liệu cần để tạo nhân vật
 *
 * Tương ứng định dạng gói tin C:009-001:
 * +họ tên(?) +giới tính(1) +0(1) +kiểu tóc(1) +0(1)
 * +bảng màu 1(4) +bảng màu 2(4) +thuộc tính(1) +sáu chỉ số(1)*6 +riêng tư(1)
 */
/**
 * Dữ liệu nhân vật rút gọn dùng cho bảng xếp hạng
 */
data class LeaderboardCharRecord(
    val id: Long,
    val name: String,
    val level: Int,
    val exp: Long,
    val element: Int,
    val turn: Int,
    val sex: Int,
    val hair: Int,
    val color1: Int,
    val color2: Int,
    /** Tiền (từ CharacterMoney)*/
    val gold: Int = 0,
    /** Tiền đấu giá (từ CharacterMoney)*/
    val auctionGold: Int = 0,
    /** Cấp độ tướng cao nhất (từ CharacterPets MAX(level))*/
    val petMaxLevel: Int = 0
)

data class CharacterCreateData(
    /** Tên nhân vật (mã hoá Big5, dài nhất 14 bytes)*/
    val name: String,
    /** Giới tính (1=nam, 2=nữ)*/
    val sex: Int,
    /** Chỉ số kiểu tóc*/
    val hair: Int,
    /** Bảng màu 1 (mã RGB thập phân mũ/tóc/da)*/
    val color1: Int,
    /** Bảng màu 2 (mã RGB thập phân áo/phụ kiện/giày)*/
    val color2: Int,
    /** Thuộc tính ngũ hành (1-5)*/
    val element: Int,
    /** Điểm phân bổ lực lượng*/
    val str: Int,
    /** Điểm phân bổ nhanh nhẹn*/
    val dex: Int,
    /** Điểm phân bổ thể lực*/
    val vit: Int,
    /** Điểm phân bổ trí lực*/
    val int_: Int,
    /** Điểm phân bổ tinh thần*/
    val men: Int,
    /** Điểm phân bổ linh lực*/
    val spr: Int
)
