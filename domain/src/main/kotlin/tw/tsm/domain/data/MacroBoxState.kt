package tw.tsm.domain.data

/**
 * Thiết lập khởi động hộp máy — tham số client gửi lên C:065-001
 *
 * Tương ứng các trường gói tin của MachineBox.lua SetAutoFight().
 */
data class MacroBoxSettings(
    /** Ngưỡng dưới kích hoạt dùng dược phẩm HP của người chơi (phần trăm, ví dụ 50 = 50%)*/
    val playerHpPercent: Int,
    /** Ngưỡng dưới kích hoạt dùng dược phẩm SP của người chơi (phần trăm)*/
    val playerSpPercent: Int,
    /** Về thành khi thiếu dược phẩm HP*/
    val hpReturnTown: Boolean,
    /** Về thành khi thiếu dược phẩm SP*/
    val spReturnTown: Boolean,
    /** Về thành khi người chơi chết*/
    val playerDeadReturn: Boolean,
    /** Về thành khi tướng chết*/
    val npcDeadReturn: Boolean,
    /** Tự động dùng vật phẩm kinh nghiệm*/
    val autoExp: Boolean,
    /** Tướng xuất chiến tự đổi sang búa chì*/
    val autoShotput: Boolean
)

/**
 * Khe tinh thạch của hộp máy
 */
data class SparSlot(
    /** ID vật phẩm tinh thạch*/
    val sparId: Int,
    /** Số lần sử dụng tích lũy*/
    var count: Int
)

/**
 * Khe bình kinh nghiệm của hộp máy
 */
data class ExpBottleSlot(
    /** ID vật phẩm bình kinh nghiệm*/
    val bottleId: Int,
    /** Kinh nghiệm tích lũy*/
    var exp: Int
)

/**
 * Trạng thái runtime của hộp máy người chơi
 *
 * Không lưu bền vào DB, khi đăng nhập sẽ khởi tạo lại từ túi đồ.
 */
data class MacroBoxState(
    /** Hộp máy có đang bật hay không*/
    var isActive: Boolean = false,
    /** Thiết lập khởi động*/
    var settings: MacroBoxSettings? = null,
    /** Khe tinh thạch (index → SparSlot), tối đa MaxSparBase + sparExtendNum*/
    val sparSlots: MutableMap<Int, SparSlot> = mutableMapOf(),
    /** Số khe tinh thạch đã mở rộng*/
    var sparExtendNum: Int = 0,
    /** Khe bình kinh nghiệm (index → ExpBottleSlot)*/
    val expBottles: MutableMap<Int, ExpBottleSlot> = mutableMapOf(),
    /** Số khe bình kinh nghiệm đã mở rộng*/
    var expExtendNum: Int = 0,
    /** Chỉ số bình kinh nghiệm đang được ưu tiên dùng*/
    var expPriority: Int = 0
) {
    companion object {
        /** Số khe tinh thạch cơ bản*/
        const val MAX_SPAR_BASE = 3
        /** Số khe tinh thạch tối đa có thể mở rộng*/
        const val MAX_SPAR_EXT = 2
        /** Số lần tích lũy tối đa của cùng loại tinh thạch*/
        const val MAX_SPAR_COUNT = 60000
        /** Số khe bình kinh nghiệm cơ bản*/
        const val BASE_EXP_BOTTLE = 3
        /** Số khe bình kinh nghiệm tối đa có thể mở rộng*/
        const val MAX_EXP_EXT = 5
    }
}
