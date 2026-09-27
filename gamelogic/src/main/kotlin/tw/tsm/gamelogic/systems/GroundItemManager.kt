package tw.tsm.gamelogic.systems

import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

/**
 * Dữ liệu vật phẩm trên mặt đất
 *
 * Tương ứng với mục `groundThings` của `GdThingManager.lua`.
 */
data class GroundItem(
    /** Chỉ số trong cảnh (1-255, đồng nhất với hệ cơ số 1 / 1-based của FindThingSlot trong Lua) */
    val index: Int,
    /** Loại (EGdThing: 1=Vật phẩm, 2=Tiền) */
    val kind: Int,
    /** Giá trị (ID Vật phẩm hoặc Số tiền) */
    val value: Int,
    /** Tọa độ X */
    val x: Int,
    /** Tọa độ Y */
    val y: Int,
    /** ID người chơi vứt bỏ (0=Do hệ thống tạo) */
    val ownerId: Long = 0,
    /** Dấu thời gian tạo (mili-giây) */
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Trình quản lý vật phẩm trên mặt đất (Phiên bản rút gọn)
 *
 * Mỗi bản đồ duy trì một nhóm vật phẩm trên mặt đất, tối đa 255 cái (GdThingManager.maxThing = 255).
 * Tương ứng với `GdThingManager.lua`.
 */
class GroundItemManager {

    /** mapId → (index → GroundItem) */
    private val mapItems = ConcurrentHashMap<Int, ConcurrentHashMap<Int, GroundItem>>()

    /**
     * Thêm vật phẩm trên mặt đất ở bản đồ chỉ định
     *
     * Chỉ số bắt đầu từ 1 (Đồng nhất với vòng lặp hệ cơ số 1 / 1-based của `GdThingManager.FindThingSlot` trong Lua),
     * đảm bảo chỉ số trả về khi client nhặt có thể tương ứng chính xác với vị trí lưu trữ trên máy chủ.
     *
     * @return Chỉ số được phân bổ (1-255), trả về -1 nếu bản đồ đã đầy
     */
    fun addItem(mapId: Int, kind: Int, value: Int, x: Int, y: Int, ownerId: Long = 0): Int {
        val items = mapItems.getOrPut(mapId) { ConcurrentHashMap() }
        // Bắt đầu từ 1 để tìm ô trống, dùng putIfAbsent để đảm bảo tính nguyên tử (vòng lặp CAS)
        for (index in 1..MAX_GROUND_ITEMS) {
            val item = GroundItem(index = index, kind = kind, value = value, x = x, y = y, ownerId = ownerId)
            if (items.putIfAbsent(index, item) == null) {
                return index
            }
        }
        return -1
    }

    /**
     * Xóa vật phẩm trên mặt đất được chỉ định
     *
     * @return Vật phẩm bị xóa, trả về null nếu không tồn tại
     */
    fun removeItem(mapId: Int, index: Int): GroundItem? =
        mapItems[mapId]?.remove(index)

    /**
     * Lấy vật phẩm trên mặt đất được chỉ định
     */
    fun getItem(mapId: Int, index: Int): GroundItem? =
        mapItems[mapId]?.get(index)

    /**
     * Lấy tất cả vật phẩm trên mặt đất của bản đồ chỉ định
     */
    fun getItems(mapId: Int): List<GroundItem> =
        mapItems[mapId]?.values?.toList() ?: emptyList()

    /**
     * Xóa sạch vật phẩm trên mặt đất của bản đồ chỉ định (Dùng khi reset cảnh)
     */
    fun clearMap(mapId: Int) {
        mapItems.remove(mapId)
    }

    companion object {
        /** Số lượng vật phẩm trên mặt đất tối đa trong một cảnh (GdThingManager.maxThing) */
        const val MAX_GROUND_ITEMS = 255
    }
}