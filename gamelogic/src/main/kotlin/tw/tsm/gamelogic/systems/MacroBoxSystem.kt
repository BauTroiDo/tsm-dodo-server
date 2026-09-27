package tw.tsm.gamelogic.systems

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.data.ExpBottleSlot
import tw.tsm.domain.data.MacroBoxSettings
import tw.tsm.domain.data.MacroBoxState
import tw.tsm.domain.data.SparSlot
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

/**
 * Hệ thống Hộp Cơ Quan (Auto / Nội quải)
 *
 * Quản lý trạng thái runtime Hộp Cơ Quan của mỗi người chơi.
 * Các logic như tự động chiến đấu, tự động di chuyển, tự động sử dụng vật phẩm hồi phục đều do client xử lý,
 * Máy chủ (Server) chỉ chịu trách nhiệm:
 * - Quản lý trạng thái khởi động/tạm dừng
 * - Quản lý dữ liệu ô chứa tinh thạch và bình kinh nghiệm
 * - Gửi gói tin kích hoạt tiếp tế sau khi trận chiến kết thúc
 * - Gửi dữ liệu khởi tạo khi đăng nhập
 */
class MacroBoxSystem {

    /** charId người chơi → Trạng thái Hộp Cơ Quan */
    private val states = ConcurrentHashMap<Long, MacroBoxState>()

    /**
     * Lấy hoặc tạo trạng thái Hộp Cơ Quan của người chơi
     */
    fun getOrCreateState(charId: Long): MacroBoxState {
        return states.getOrPut(charId) { MacroBoxState() }
    }

    /**
     * Lấy trạng thái Hộp Cơ Quan của người chơi (có thể là null)
     */
    fun getState(charId: Long): MacroBoxState? = states[charId]

    /**
     * Khởi động Hộp Cơ Quan
     *
     * @param charId ID nhân vật
     * @param settings Cài đặt khởi động do client gửi lên
     * @return true=Khởi động thành công
     */
    fun activate(charId: Long, settings: MacroBoxSettings): Boolean {
        val state = getOrCreateState(charId)
        state.isActive = true
        state.settings = settings
        logger.debug { "Khởi động Hộp Cơ Quan: charId=$charId hp%=${settings.playerHpPercent} sp%=${settings.playerSpPercent}" }
        return true
    }

    /**
     * Tạm dừng Hộp Cơ Quan
     */
    fun deactivate(charId: Long) {
        val state = states[charId] ?: return
        state.isActive = false
        logger.debug { "Tạm dừng Hộp Cơ Quan: charId=$charId" }
    }

    /**
     * Truy vấn xem Hộp Cơ Quan có đang hoạt động không
     */
    fun isActive(charId: Long): Boolean {
        return states[charId]?.isActive == true
    }

    /**
     * Gỡ bỏ tinh thạch
     *
     * @param charId ID nhân vật
     * @param sparId ID vật phẩm tinh thạch
     * @param index Chỉ số (index) ô chứa tinh thạch
     * @return true=Gỡ bỏ thành công
     */
    fun removeSpar(charId: Long, sparId: Int, index: Int): Boolean {
        val state = states[charId] ?: return false
        val slot = state.sparSlots[index] ?: return false
        if (slot.sparId != sparId) return false
        state.sparSlots.remove(index)
        logger.debug { "Hộp Cơ Quan gỡ bỏ tinh thạch: charId=$charId index=$index sparId=$sparId" }
        return true
    }

    /**
     * Gỡ bỏ bình kinh nghiệm
     *
     * @param charId ID nhân vật
     * @param index Chỉ số ô chứa bình kinh nghiệm
     * @return true=Gỡ bỏ thành công
     */
    fun removeExpBottle(charId: Long, index: Int): Boolean {
        val state = states[charId] ?: return false
        if (!state.expBottles.containsKey(index)) return false
        state.expBottles.remove(index)
        // Nếu bình kinh nghiệm bị gỡ là bình đang được ưu tiên, đặt lại chỉ số ưu tiên
        if (state.expPriority == index) {
            state.expPriority = state.expBottles.keys.firstOrNull() ?: 0
        }
        logger.debug { "Hộp Cơ Quan gỡ bỏ bình kinh nghiệm: charId=$charId index=$index" }
        return true
    }

    /**
     * Thiết lập thứ tự ưu tiên cho bình kinh nghiệm
     *
     * @param charId ID nhân vật
     * @param index Chỉ số ô chứa bình kinh nghiệm
     * @return true=Thiết lập thành công
     */
    fun setExpPriority(charId: Long, index: Int): Boolean {
        val state = states[charId] ?: return false
        if (!state.expBottles.containsKey(index)) return false
        state.expPriority = index
        logger.debug { "Hộp Cơ Quan thiết lập ưu tiên bình kinh nghiệm: charId=$charId priority=$index" }
        return true
    }

    /**
     * Khởi tạo trạng thái Hộp Cơ Quan (được gọi khi đăng nhập)
     *
     * Dữ liệu tinh thạch/bình kinh nghiệm do client quản lý, server chỉ cần khởi tạo số ô mở rộng.
     * Hiện tại mặc định không mở rộng (Private server có thể điều chỉnh).
     */
    fun initState(charId: Long) {
        val state = getOrCreateState(charId)
        // Mặc định Private server: Không mở rộng thêm, sử dụng số ô cơ bản
        state.sparExtendNum = 0
        state.expExtendNum = 0
        logger.debug { "Khởi tạo Hộp Cơ Quan: charId=$charId sparExt=${state.sparExtendNum} expExt=${state.expExtendNum}" }
    }

    /**
     * Dọn dẹp khi ngắt kết nối
     */
    fun cleanup(charId: Long) {
        states.remove(charId)
        logger.debug { "Dọn dẹp Hộp Cơ Quan: charId=$charId" }
    }
}