package tw.tsm.gamelogic.systems

import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

/**
 * Trình quản lý cửa hàng NPC — Theo dõi danh sách hàng hóa hiện tại của mỗi người chơi
 *
 * Khi sự kiện Eve có resultType=6 (Surface) và optionMode=1 (Cửa sổ mua bán),
 * sẽ được [EveFlowController] gọi [setShopItems] để ghi lại danh sách ID hàng hóa.
 * [NpcTradeHandler] khi xử lý C:027-001 sẽ thông qua [getShopItem] để truy vấn.
 * Khi sự kiện kết thúc sẽ được [clearShop] dọn dẹp.
 */
class NpcShopManager {

    /** playerId → Danh sách item ID hàng hóa (thứ tự index khớp với tùy chọn UIShop của client) */
    private val activeShops = ConcurrentHashMap<Long, List<Int>>()

    /**
     * Thiết lập danh sách hàng hóa trong cửa hàng hiện tại của người chơi
     *
     * @param playerId ID người chơi
     * @param itemIds  Danh sách item ID hàng hóa (0 = Placeholder không phải vật phẩm)
     */
    fun setShopItems(playerId: Long, itemIds: List<Int>) {
        activeShops[playerId] = itemIds
        logger.debug { "Mở cửa hàng: playerId=$playerId số lượng=${itemIds.size} itemIds=$itemIds" }
    }

    /**
     * Lấy item ID hàng hóa theo index chỉ định
     *
     * @param playerId ID người chơi
     * @param index    Chỉ số (hệ cơ số 0 / 0-based) của hàng hóa trong danh sách
     * @return item ID, hoặc null (Không có cửa hàng/Index vượt quá giới hạn/Placeholder không phải vật phẩm)
     */
    fun getShopItem(playerId: Long, index: Int): Int? {
        val items = activeShops[playerId] ?: return null
        if (index < 0 || index >= items.size) return null
        val itemId = items[index]
        return if (itemId > 0) itemId else null
    }

    /**
     * Xóa trạng thái cửa hàng của người chơi
     */
    fun clearShop(playerId: Long) {
        if (activeShops.remove(playerId) != null) {
            logger.debug { "Đóng cửa hàng: playerId=$playerId" }
        }
    }
}