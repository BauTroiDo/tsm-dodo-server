package tw.tsm.gamelogic.systems

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.constants.ItemConstants
import tw.tsm.domain.interfaces.repositories.RoleCountRepository
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

/**
 * Hệ thống bộ đếm nhân vật — Tương ứng với Client RoleCount.lua
 *
 * Duy trì trạng thái bộ đếm của mỗi nhân vật trực tuyến trong bộ nhớ, kết hợp với DB để lưu trữ cố định.
 * Mục đích: Điều kiện thành tựu (ECondition.RoleCount=14, FriendCount=18),
 * số ô túi đồ, số ô tiền trang (ngân hàng), số ngày đăng nhập, v.v.
 *
 * Các chỉ số ERoleCount thường dùng:
 * - 151=Bắt thành công, 152=Chết, 163=Số trận chiến
 * - 192=Số lượng bạn bè (Dùng cho thành tựu ECondition.FriendCount)
 * - 103/106/124=Số ô túi đồ, 101/125/126=Số ô tiền trang
 *
 * @param repo Tầng truy cập dữ liệu lưu trữ bộ đếm
 */
class RoleCountSystem(
    private val repo: RoleCountRepository
) {

    /** characterId → (countIndex → Pair<value, maxValue>) */
    private val countsMap = ConcurrentHashMap<Long, ConcurrentHashMap<Int, Pair<Int, Int>>>()

    /**
     * Tải tất cả bộ đếm của nhân vật từ DB vào bộ nhớ
     */
    suspend fun loadCounts(characterId: Long) {
        val loaded = repo.loadAll(characterId)
        countsMap[characterId] = ConcurrentHashMap(loaded)
        logger.debug { "Tải RoleCount: characterId=$characterId ${loaded.size} bản ghi" }
    }

    /**
     * Lấy giá trị bộ đếm
     *
     * @param characterId ID nhân vật
     * @param countIndex Chỉ số ERoleCount
     * @return Giá trị hiện tại (Trả về 0 nếu không tồn tại)
     */
    fun getValue(characterId: Long, countIndex: Int): Int {
        val pair = countsMap[characterId]?.get(countIndex) ?: return 0
        return pair.first
    }

    /**
     * Lấy giá trị giới hạn (max) của bộ đếm
     *
     * @param characterId ID nhân vật
     * @param countIndex Chỉ số ERoleCount
     * @return Giá trị giới hạn (Trả về 0 nếu không tồn tại)
     */
    fun getMaxValue(characterId: Long, countIndex: Int): Int {
        val pair = countsMap[characterId]?.get(countIndex) ?: return 0
        return pair.second
    }

    /**
     * Thiết lập giá trị bộ đếm
     *
     * @param characterId ID nhân vật
     * @param countIndex Chỉ số ERoleCount
     * @param value Giá trị mới
     * @param maxValue Giới hạn (max) mới
     */
    fun setValue(characterId: Long, countIndex: Int, value: Int, maxValue: Int = 0) {
        val counts = countsMap.getOrPut(characterId) { ConcurrentHashMap() }
        counts[countIndex] = Pair(value, maxValue)
    }

    /**
     * Tăng giá trị bộ đếm
     *
     * @param characterId ID nhân vật
     * @param countIndex Chỉ số ERoleCount
     * @param delta Lượng tăng (Mặc định là 1)
     * @return Giá trị mới sau khi tăng
     */
    fun increment(characterId: Long, countIndex: Int, delta: Int = 1): Int {
        val counts = countsMap.getOrPut(characterId) { ConcurrentHashMap() }
        val (oldValue, maxValue) = counts[countIndex] ?: Pair(0, 0)
        val newValue = oldValue + delta
        counts[countIndex] = Pair(newValue, maxValue)
        return newValue
    }

    /**
     * Lấy tất cả bộ đếm của nhân vật (Dùng cho gói tin S:085-001 khi đăng nhập)
     *
     * @return Map<countIndex, Pair<value, maxValue>>
     */
    fun getAllCounts(characterId: Long): Map<Int, Pair<Int, Int>> {
        return countsMap[characterId]?.toMap() ?: emptyMap()
    }

    /**
     * Kiểm tra bộ đếm có bản ghi hay không (Phân biệt giữa "giá trị là 0" và "không có bản ghi", dùng để lưu giá trị mặc định)
     */
    fun hasCount(characterId: Long, countIndex: Int): Boolean =
        countsMap[characterId]?.containsKey(countIndex) == true

    // ── Tính toán sức chứa (Tương ứng với Item.RefreshRoleCount) ──

    /**
     * Số ô túi đồ người chơi đã mở
     *
     * Đối chiếu DVT Item.lua: currentThingGrid = InitBagGrid(250) + (Bag_1+Bag_2+Bag_3)×5
     */
    fun getBagCapacity(characterId: Long): Int {
        val expand = getValue(characterId, BAG_1) + getValue(characterId, BAG_2) + getValue(characterId, BAG_3)
        return (ItemConstants.INIT_BAG_SLOTS + expand * ItemConstants.ADD_BAG_SLOTS)
            .coerceAtMost(ItemConstants.MAX_BAG_SLOTS)
    }

    /**
     * Số ô tiền trang (ngân hàng) người chơi đã mở
     *
     * Đối chiếu DVT Item.lua: currentBThingGrid = BaseBThingGrid(250) + (Bank_1+Bank_2+Bank_3)×5
     */
    fun getBankCapacity(characterId: Long): Int {
        val expand = getValue(characterId, BANK_1) + getValue(characterId, BANK_2) + getValue(characterId, BANK_3)
        return (ItemConstants.INIT_BANK_SLOTS + expand * ItemConstants.ADD_BANK_SLOTS)
            .coerceAtMost(ItemConstants.MAX_BANK_SLOTS)
    }

    /**
     * Số ô bảo khố người chơi đã mở
     *
     * Đối chiếu Item.lua: currentStorageGrid = (Storage_1+Storage_2)×5, tối đa 70
     */
    fun getStorageCapacity(characterId: Long): Int {
        val expand = getValue(characterId, STORAGE_1) + getValue(characterId, STORAGE_2)
        return (expand * ItemConstants.ADD_STORAGE_SLOTS)
            .coerceAtMost(ItemConstants.MAX_STORAGE_SLOTS)
    }

    /**
     * Dung lượng kho võ tướng.
     *
     * DVT Inn.lua:
     * - warriorRepositoryMin = 5
     * - warriorRepositoryMax = 150
     * - RefreshWarriorRepositoryCount = RoleCount[822] + 5
     */
    fun getWarriorRepositoryCapacity(characterId: Long): Int =
        (WARRIOR_REPOSITORY_BASE_SLOTS + getValue(characterId, WARRIOR_REPOSITORY))
            .coerceAtMost(WARRIOR_REPOSITORY_MAX_SLOTS)

    /**
     * Lưu bộ đếm nhân vật vào DB
     */
    suspend fun saveCounts(characterId: Long) {
        val counts = countsMap[characterId] ?: return
        repo.saveAll(characterId, counts.toMap())
        logger.debug { "Lưu RoleCount: characterId=$characterId ${counts.size} bản ghi" }
    }

    /**
     * Lưu và hủy nạp (unload) khỏi bộ nhớ
     */
    suspend fun saveAndUnload(characterId: Long) {
        saveCounts(characterId)
        countsMap.remove(characterId)
    }

    /**
     * Hủy nạp khỏi bộ nhớ (không lưu)
     */
    fun unload(characterId: Long) {
        countsMap.remove(characterId)
    }

    companion object {
        // DVT RoleCount.lua marks 190..195 as special counters (wire value >> 16).
        /** DVT RoleCount.lua: lượt Điều phái miễn phí đã dùng trong ngày. */
        const val DISPATCH = 8
        /** DVT RoleCount.lua: lượt Điều phái mua thêm đã dùng trong ngày. */
        const val BUY_DISPATCH = 9
        /** DVT RoleCount.lua: tổng số lần Điều phái tích lũy. */
        const val TOTAL_DISPATCH = 165

        /** DVT CollectCard: số tổ hợp thẻ tối đa được trang bị. */
        const val COLLECTION_CARD_EQUIP_MAX = 127
        const val SKILL_POINT = 190
        const val ATTRIBUTE_POINT = 191
        const val FRIEND_COUNT = 192
        const val SPECIAL_NONE_1 = 193
        const val SPECIAL_NONE_2 = 194
        const val SPECIAL_NONE_3 = 195

        // ── Chỉ số mở rộng ô chứa ERoleCount (RoleCount.lua) ──

        /** Số ô tiền trang mở 1 (Mua thông qua tiêu phí sellId=1) */
        const val BANK_1 = 101

        /** Số ô tiền trang mở 2 */
        const val BANK_2 = 125

        /** Số ô tiền trang mở 3 */
        const val BANK_3 = 126

        /** Mở rộng thêm khách sạn (Mua thông qua tiêu phí sellId=2, mỗi lần +1 ô) */
        const val INN = 102

        /** Số ô túi đồ mở 1 (Mua thông qua tiêu phí sellId=3) */
        const val BAG_1 = 103

        /** Số ô túi đồ mở 2 */
        const val BAG_2 = 106

        /** Số ô túi đồ mở 3 */
        const val BAG_3 = 124

        /** Mở rộng bảo khố bằng Kim Nguyên Bảo (Mua thông qua tiêu phí sellId=56) */
        const val STORAGE_1 = 676

        /** Mở rộng bảo khố qua sự kiện */
        const val STORAGE_2 = 677

        /** Mở rộng thêm kho võ tướng */
        const val WARRIOR_REPOSITORY = 822

        /** DVT Inn.lua warriorRepositoryMin. */
        const val WARRIOR_REPOSITORY_BASE_SLOTS = 5

        /** DVT Inn.lua warriorRepositoryMax. */
        const val WARRIOR_REPOSITORY_MAX_SLOTS = 150

        /** Mỗi lần mua sellId=69 tăng 1 ô: 150 - 5 = 145 lần tối đa. */
        const val WARRIOR_REPOSITORY_MAX_EXPANSIONS =
            WARRIOR_REPOSITORY_MAX_SLOTS - WARRIOR_REPOSITORY_BASE_SLOTS

        /** DVT RoleCount.lua: mảnh võ tướng / Lò. */
        const val NPC_DEBRIS = 110

        /** DVT RoleCount.lua: mảnh trang bị / Lò. */
        const val EQUIP_DEBRIS = 111

        /** DVT RoleCount.lua: mảnh vũ khí chuyên dụng / Lò. */
        const val SWEAPON_DEBRIS = 654
    }
}