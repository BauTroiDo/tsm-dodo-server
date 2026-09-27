package tw.tsm.gamelogic.systems

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.constants.MountConstants
import tw.tsm.domain.data.ItemDef
import tw.tsm.domain.data.MountDef
import tw.tsm.domain.data.MountGrowDef
import tw.tsm.domain.interfaces.repositories.MountRepository
import tw.tsm.domain.valueobjects.MountData
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

/** Kết quả thăng cấp */
data class LevelUpResult(val newLevel: Int, val itemConsumed: Int, val moneySpent: Int)

/** Kết quả cộng điểm */
data class AttributeUpResult(val newPoints: Int, val itemConsumed: Int)

/**
 * Hệ thống thú cưỡi — Quản lý dữ liệu thú cưỡi, lên/xuống ngựa, thăng cấp, cộng điểm thuộc tính, trang bị, thu thập
 *
 * Vòng đời: Tải khi đăng nhập → Thao tác trong bộ nhớ → Lưu khi ngắt kết nối
 * Nguồn: Mounts.lua
 */
class MountSystem(
    private val mountRepository: MountRepository,
    private val mountDefs: Map<Int, MountDef>,
    private val mountGrowDefs: Map<Int, MountGrowDef>,
    private val inventorySystem: InventorySystem,
    private val moneySystem: MoneySystem,
    private val bitFlagSystem: BitFlagSystem,
    private val itemDefs: Map<Int, ItemDef>
) {

    private val cache = ConcurrentHashMap<Long, MountData>()

    /**
     * Tải dữ liệu thú cưỡi khi đăng nhập
     *
     * Cơ chế khôi phục: Nếu DB không có bản ghi thú cưỡi hoặc currentNpcId=0, nhưng BitFlag cho thấy đã thu thập thú cưỡi,
     * tự động chọn thú cưỡi đã thu thập. Ngăn chặn việc khởi động lại máy chủ dẫn đến mất dữ liệu thú cưỡi.
     *
     * @return MountData (Tạo giá trị mặc định nếu DB không có dữ liệu)
     */
    suspend fun load(characterId: Long): MountData {
        val existing = mountRepository.findByCharacterId(characterId)
        val data = existing ?: MountData()

        // Khôi phục: Nếu không có thú cưỡi hiện tại nhưng BitFlag hiển thị đã thu thập, tự động chọn
        if (data.currentNpcId == 0) {
            val flags = bitFlagSystem.getSetFlagIds(characterId)
            val collected = mountDefs.entries.firstOrNull { (_, def) -> def.flagId in flags }
            if (collected != null) {
                data.currentNpcId = collected.key
                logger.info { "Tự động khôi phục thú cưỡi: charId=$characterId → npcId=${data.currentNpcId}" }
            }
        }

        cache[characterId] = data
        logger.debug { "Tải thú cưỡi: charId=$characterId level=${data.level} npcId=${data.currentNpcId} equips=${data.equips.size}" }
        return data
    }

    /** Lấy dữ liệu thú cưỡi trong bộ nhớ đệm */
    fun getCache(characterId: Long): MountData? = cache[characterId]

    /**
     * Lên ngựa (C:079-001)
     *
     * @return ID NPC cưỡi, 0=Thất bại (Không có thú cưỡi/Đang cưỡi/Đang chiến đấu)
     */
    fun mount(characterId: Long): Int {
        val data = cache[characterId] ?: return 0
        if (data.isRiding) return 0
        if (data.currentNpcId == 0) return 0
        if (mountDefs[data.currentNpcId] == null) return 0

        data.isRiding = true
        return data.currentNpcId
    }

    /**
     * Xuống ngựa (C:079-002)
     *
     * @return true=Thành công
     */
    fun dismount(characterId: Long): Boolean {
        val data = cache[characterId] ?: return false
        if (!data.isRiding) return false

        data.isRiding = false
        return true
    }

    /**
     * Thăng cấp (C:079-003)
     *
     * Đối chiếu Mounts.lua LevelUp():
     * 1. mountGrowDefs[level] tồn tại
     * 2. mountGrowDefs[level+1] tồn tại (Nếu không là đã đạt cấp tối đa)
     * 3. Trong túi đồ có đủ vật phẩm thăng cấp
     * 4. Đủ tiền
     *
     * @return LevelUpResult (Chứa thông tin tiêu hao), null=Thất bại
     */
    suspend fun levelUp(characterId: Long, bagIndex: Int): LevelUpResult? {
        val data = cache[characterId] ?: return null
        val growData = mountGrowDefs[data.level] ?: return null

        // Kiểm tra cấp tiếp theo có tồn tại không
        if (mountGrowDefs[data.level + 1] == null) return null

        // ① Xác thực vật phẩm trước (Chưa tiêu hao)
        var itemConsumed = 0
        if (growData.upItemId > 0 && growData.upItemCount > 0) {
            val thing = inventorySystem.getBagItem(characterId, bagIndex) ?: return null
            if (thing.itemId != growData.upItemId || thing.quantity < growData.upItemCount) return null
            itemConsumed = growData.upItemCount
        }

        // ② Xác thực tiền tệ trước (Chưa trừ)
        var moneySpent = 0
        if (growData.upMoney > 0) {
            val gold = moneySystem.getGold(characterId)
            if (gold < growData.upMoney) return null
            moneySpent = growData.upMoney.toInt()
        }

        // ③ Qua hết xác thực → Tiến hành tiêu hao
        if (itemConsumed > 0) {
            inventorySystem.removeFromBag(characterId, bagIndex, itemConsumed)
        }
        if (moneySpent > 0) {
            moneySystem.subtractGold(characterId, moneySpent)
        }

        data.level++
        logger.info { "Thú cưỡi thăng cấp: charId=$characterId → level=${data.level}" }
        return LevelUpResult(data.level, itemConsumed, moneySpent)
    }

    /**
     * Cộng điểm (C:079-004)
     *
     * Đối chiếu Mounts.lua AttributeUp():
     * 1. Phạm vi kind 1-5
     * 2. Cấp độ thuộc tính < Cấp độ thú cưỡi
     * 3. Trong túi đồ có vật phẩm cộng điểm
     *
     * @return AttributeUpResult (Chứa thông tin tiêu hao), null=Thất bại
     */
    suspend fun addAttributePoint(characterId: Long, kind: Int, bagIndex: Int): AttributeUpResult? {
        if (kind !in 1..MountConstants.MAX_ATTRIBUTE_KINDS) return null
        val data = cache[characterId] ?: return null

        // Tính cấp độ thuộc tính hiện tại
        val currentPoints = data.attributePoints[kind]
        val attrLevel = getAttributeLevel(kind, currentPoints)

        // Cấp độ thuộc tính không thể vượt qua cấp độ thú cưỡi
        if (attrLevel >= data.level) return null

        // Lấy dữ liệu tăng trưởng của cấp tiếp theo
        val nextGrow = mountGrowDefs[attrLevel + 1] ?: return null
        val attrGrow = nextGrow.attributes.getOrNull(kind - 1) ?: return null

        // Tiêu hao vật phẩm
        var itemConsumed = 0
        if (attrGrow.upItemId > 0 && attrGrow.upItemCount > 0) {
            val thing = inventorySystem.getBagItem(characterId, bagIndex) ?: return null
            if (thing.itemId != attrGrow.upItemId || thing.quantity < attrGrow.upItemCount) return null
            itemConsumed = attrGrow.upItemCount
            inventorySystem.removeFromBag(characterId, bagIndex, itemConsumed)
        }

        // Cộng 1 điểm
        data.attributePoints[kind] = currentPoints + 1
        logger.info { "Thú cưỡi cộng điểm: charId=$characterId kind=$kind → points=${data.attributePoints[kind]}" }
        return AttributeUpResult(data.attributePoints[kind], itemConsumed)
    }

    /**
     * Mặc trang bị (C:079-005)
     *
     * @return fitType, -1=Thất bại
     */
    suspend fun equipItem(characterId: Long, bagIndex: Int): Int {
        val data = cache[characterId] ?: return -1
        val thing = inventorySystem.getBagItem(characterId, bagIndex) ?: return -1
        if (thing.isEmpty) return -1

        val itemDef = itemDefs[thing.itemId] ?: return -1
        val fitType = itemDef.fitType
        if (fitType !in 1..MountConstants.MAX_EQUIP_SLOTS) return -1

        // Nếu đã có trang bị, bỏ lại vào túi; nếu không làm trống ô
        val oldEquip = data.equips[fitType]
        if (oldEquip != null && !oldEquip.isEmpty) {
            inventorySystem.setBagItem(characterId, bagIndex, oldEquip)
        } else {
            inventorySystem.setBagItem(characterId, bagIndex, null)
        }

        data.equips[fitType] = thing
        logger.info { "Thú cưỡi mặc trang bị: charId=$characterId fitType=$fitType itemId=${thing.itemId}" }
        return fitType
    }

    /**
     * Tháo trang bị (C:079-006)
     *
     * @return (fitType, bagIndex) hoặc null=Thất bại
     */
    suspend fun unequipItem(characterId: Long, fitType: Int, bagIndex: Int): Pair<Int, Int>? {
        val data = cache[characterId] ?: return null
        val equip = data.equips[fitType] ?: return null
        if (equip.isEmpty) return null

        // Xác nhận ô túi đồ đích trống
        val existing = inventorySystem.getBagItem(characterId, bagIndex)
        if (existing != null && !existing.isEmpty) return null

        // Chuyển vào vị trí chỉ định trong túi đồ
        inventorySystem.setBagItem(characterId, bagIndex, equip)

        data.equips.remove(fitType)
        logger.info { "Thú cưỡi tháo trang bị: charId=$characterId fitType=$fitType → bagIndex=$bagIndex" }
        return fitType to bagIndex
    }

    /**
     * Đổi thú cưỡi (C:079-007)
     *
     * @return true=Thành công
     */
    suspend fun switchMount(characterId: Long, npcId: Int): Boolean {
        val data = cache[characterId] ?: return false

        // Xác thực định nghĩa thú cưỡi có tồn tại
        val mountDef = mountDefs[npcId] ?: return false

        // Xác thực đã thu thập (BitFlag)
        val flags = bitFlagSystem.getSetFlagIds(characterId)
        if (mountDef.flagId !in flags) return false

        data.currentNpcId = npcId
        logger.info { "Đổi thú cưỡi: charId=$characterId → npcId=$npcId" }
        return true
    }

    /**
     * Sử dụng sách ảnh thú cưỡi để mở khóa bộ sưu tập (Được gọi khi sử dụng vật phẩm C:023-015)
     *
     * Đối chiếu Item.lua L2311-2316:
     * Client xác thực `Mounts.HaveCollection(spare3)` sau đó gửi gói tin sử dụng,
     * Server thiết lập BitFlag → S:081-001 → Client `Mounts.OnGetCollection(flagId)`.
     *
     * @param characterId ID nhân vật
     * @param npcId ID NPC thú cưỡi (Từ ItemDef.spare3)
     * @return flagId (Thành công), null=Thất bại (Chưa định nghĩa/Đã thu thập)
     */
    suspend fun useCollectionItem(characterId: Long, npcId: Int): Int? {
        val mountDef = mountDefs[npcId] ?: return null
        val flags = bitFlagSystem.getSetFlagIds(characterId)
        if (mountDef.flagId in flags) return null  // Đã thu thập

        bitFlagSystem.setFlag(characterId, mountDef.flagId)

        // Nếu hiện tại không có thú cưỡi, tự động chọn thú cưỡi này
        val data = cache[characterId]
        if (data != null && data.currentNpcId == 0) {
            data.currentNpcId = npcId
        }

        logger.info { "Thu thập thú cưỡi: charId=$characterId npcId=$npcId flagId=${mountDef.flagId}" }
        return mountDef.flagId
    }

    /** Phán đoán xem có đang cưỡi không */
    fun isRiding(characterId: Long): Boolean = cache[characterId]?.isRiding == true

    /**
     * Lấy bonus tốc độ của thú cưỡi
     *
     * Đối chiếu Mounts.lua GetSpeed(level): mountsGrowDatas[level].speed
     */
    fun getSpeedBonus(characterId: Long): Int {
        val data = cache[characterId] ?: return 0
        return mountGrowDefs[data.level]?.speed ?: 0
    }

    /**
     * Tính cấp độ thuộc tính
     *
     * Đối chiếu Mounts.lua GetAttributeProgress():
     * Cộng dồn số điểm cần thiết mỗi cấp, dừng lại khi tổng số điểm không đủ
     */
    private fun getAttributeLevel(kind: Int, totalPoints: Int): Int {
        var remaining = totalPoints
        var level = 0

        for ((k, growDef) in mountGrowDefs) {
            val attrGrow = growDef.attributes.getOrNull(kind - 1) ?: break
            if (remaining < attrGrow.upItemCount) break
            level = k
            remaining -= attrGrow.upItemCount
        }

        return level
    }

    /**
     * Lưu thú cưỡi vào DB (Không dỡ, dùng cho tự động lưu định kỳ)
     */
    suspend fun save(characterId: Long) {
        val data = cache[characterId] ?: return
        try {
            mountRepository.save(characterId, data)
        } catch (e: Exception) {
            logger.error(e) { "Tự động lưu thú cưỡi thất bại: charId=$characterId" }
        }
    }

    /**
     * Lưu và dỡ khỏi bộ nhớ đệm
     */
    suspend fun saveAndUnload(characterId: Long) {
        val data = cache.remove(characterId) ?: return
        try {
            mountRepository.save(characterId, data)
            logger.debug { "Lưu thú cưỡi: charId=$characterId level=${data.level}" }
        } catch (e: Exception) {
            logger.error(e) { "Lưu thú cưỡi thất bại: charId=$characterId" }
        }
    }
}