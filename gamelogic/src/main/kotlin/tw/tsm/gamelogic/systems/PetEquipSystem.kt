package tw.tsm.gamelogic.systems

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.data.ItemDef
import tw.tsm.domain.enums.EquipSlot
import tw.tsm.domain.valueobjects.FollowNpcData
import tw.tsm.domain.valueobjects.ThingData

private val logger = KotlinLogging.logger {}

/**
 * Hệ thống trang bị của võ tướng (mainKind=23 subKind=17/18/19)
 *
 * Võ tướng và người chơi dùng chung mô hình trang bị 6 ô (EItemFitType 1-6), trang bị được lưu trong
 * [FollowNpcData.equips], gắn liền với bản thân võ tướng.
 *
 * Việc xác thực mặc trang bị phản chiếu lại `Item.CheckEquip` của client (Item.lua:1703-1894):
 * - Cấp độ ≥ needLv (Số lần chuyển sinh của võ tướng ≥ 3 thì miễn kiểm tra, tương ứng với điều kiện Turn < 3)
 * - Số lần chuyển sinh của võ tướng ≥ itemDef.turn
 * - restrict bit 64 = Chỉ người chơi dùng → Võ tướng không được mặc
 * - TODO: Kiểm tra thuộc tính huy hiệu/Kỹ năng triệu hồi (Trường hợp đặc biệt itemId 23086~23368), Hạn chế mặc/cởi trong lúc chiến đấu
 */
class PetEquipSystem(
    private val petSystem: PetSystem,
    private val inventorySystem: InventorySystem,
    private val itemDefs: Map<Int, ItemDef>
) {

    companion object {
        /** Bit restrict: Chỉ dành cho người chơi (Võ tướng không được mặc, Item.lua:1729) */
        const val RESTRICT_PLAYER_ONLY = 64
        /** Bit restrict: Chỉ dành cho võ tướng (Người chơi không được mặc, Item.lua:1724) */
        const val RESTRICT_PET_ONLY = 128
    }

    /** Kết quả mặc trang bị */
    sealed class EquipResult {
        /**
         * Thành công
         *
         * @property pet Võ tướng sau khi cập nhật
         * @property fitType Vị trí mặc
         * @property oldEquip Trang bị cũ bị thay thế (đã được đặt lại vào ô cũ trong túi đồ), null = vị trí gốc đang trống
         */
        data class Ok(val pet: FollowNpcData, val fitType: Int, val oldEquip: ThingData?) : EquipResult()
        /** Thất bại (Không qua xác thực hoặc dữ liệu không tồn tại), nguyên nhân chỉ ghi log, client đã chặn trước một bước rồi */
        object Fail : EquipResult()
    }

    /** Kết quả cởi trang bị */
    sealed class UnequipResult {
        /**
         * Thành công
         *
         * @property pet Võ tướng sau khi cập nhật
         * @property bagSlot Ô trong túi đồ mà trang bị rơi vào
         * @property thing Trang bị vừa cởi
         */
        data class Ok(val pet: FollowNpcData, val bagSlot: Int, val thing: ThingData) : UnequipResult()
        /** Túi đồ đã đầy */
        object BagFull : UnequipResult()
        /** Thất bại (Dữ liệu không tồn tại) */
        object Fail : UnequipResult()
    }

    /**
     * C:023-017 Võ tướng mặc trang bị
     *
     * Quy trình phản chiếu lại dự đoán của S:023-023 từ client: Hoán đổi ô túi đồ và ô trang bị
     * (Trang bị cũ trở về đúng ô túi đồ đó, nếu ô ban đầu trống thì xóa).
     */
    suspend fun equip(characterId: Long, followSlot: Int, bagSlot: Int): EquipResult {
        val pet = petSystem.getFollowNpc(characterId, followSlot) ?: run {
            logger.warn { "Võ tướng mặc trang bị thất bại: charId=$characterId slot=$followSlot Võ tướng không tồn tại" }
            return EquipResult.Fail
        }
        val bagItem = inventorySystem.getBagItem(characterId, bagSlot) ?: run {
            logger.warn { "Võ tướng mặc trang bị thất bại: charId=$characterId bagSlot=$bagSlot Túi đồ không có vật phẩm" }
            return EquipResult.Fail
        }
        val def = itemDefs[bagItem.itemId] ?: return EquipResult.Fail

        val fitType = def.fitType
        if (!EquipSlot.isBasicEquip(fitType)) {
            logger.warn { "Võ tướng mặc trang bị thất bại: itemId=${bagItem.itemId} fitType=$fitType Không phải trang bị" }
            return EquipResult.Fail
        }

        // Giới hạn cấp độ (Item.lua:1714: Chuyển sinh lần 3 trở lên thì miễn kiểm tra; Turn của võ tướng = turnKind)
        if (def.needLv > 0 && pet.level < def.needLv && pet.turnKind < 3) {
            logger.warn { "Võ tướng mặc trang bị thất bại: lv=${pet.level} < needLv=${def.needLv}" }
            return EquipResult.Fail
        }

        // Điều kiện chuyển sinh (Item.lua:1719)
        if (pet.turnKind < def.turn) {
            logger.warn { "Võ tướng mặc trang bị thất bại: turnKind=${pet.turnKind} < def.turn=${def.turn}" }
            return EquipResult.Fail
        }

        // Trang bị chỉ dành cho người chơi (Item.lua:1729 restrict bit 64)
        if ((def.restrict and RESTRICT_PLAYER_ONLY) != 0) {
            logger.warn { "Võ tướng mặc trang bị thất bại: itemId=${bagItem.itemId} Chỉ dành cho người chơi (restrict=${def.restrict})" }
            return EquipResult.Fail
        }

        val oldEquip = pet.equips[fitType]

        // Võ tướng mặc trang bị mới
        val updated = petSystem.updateFollowNpcEquip(characterId, followSlot, fitType, bagItem)
            ?: return EquipResult.Fail

        // Trang bị cũ đặt lại vào cùng một ô trong túi đồ (Không có trang bị cũ thì làm trống, client S:023-023 cũng đồng bộ logic này)
        inventorySystem.setBagItem(characterId, bagSlot, oldEquip)

        logger.info { "Võ tướng mặc trang bị: charId=$characterId slot=$followSlot itemId=${bagItem.itemId} fitType=$fitType Thay ra=${oldEquip?.itemId ?: 0}" }
        return EquipResult.Ok(updated, fitType, oldEquip)
    }

    /**
     * C:023-018 Võ tướng cởi trang bị (Trang bị → Ô trống trong túi đồ)
     *
     * Ô đích trong túi đồ do server quyết định (Ô đề xuất client gửi lên chỉ mang tính tham khảo).
     */
    suspend fun unequip(characterId: Long, followSlot: Int, fitType: Int): UnequipResult {
        val pet = petSystem.getFollowNpc(characterId, followSlot) ?: return UnequipResult.Fail
        val equipItem = pet.equips[fitType] ?: run {
            logger.warn { "Võ tướng cởi trang bị thất bại: charId=$characterId slot=$followSlot fitType=$fitType Không có trang bị" }
            return UnequipResult.Fail
        }

        val emptySlot = inventorySystem.findEmptyBagSlot(characterId) ?: return UnequipResult.BagFull

        val updated = petSystem.updateFollowNpcEquip(characterId, followSlot, fitType, null)
            ?: return UnequipResult.Fail
        inventorySystem.setBagItem(characterId, emptySlot, equipItem)

        logger.info { "Võ tướng cởi trang bị: charId=$characterId slot=$followSlot fitType=$fitType → bagSlot=$emptySlot" }
        return UnequipResult.Ok(updated, emptySlot, equipItem)
    }

    /**
     * C:023-019 Vứt bỏ trang bị của võ tướng (Gỡ bỏ trực tiếp khỏi bản thân võ tướng)
     *
     * @return Trang bị bị vứt bỏ, null = Thất bại
     */
    fun dropEquip(characterId: Long, followSlot: Int, fitType: Int): ThingData? {
        val pet = petSystem.getFollowNpc(characterId, followSlot) ?: return null
        val equipItem = pet.equips[fitType] ?: return null

        // Trang bị đã khóa không được vứt bỏ (Item.lua:2782)
        if (equipItem.isLocked) {
            logger.warn { "Võ tướng vứt trang bị thất bại: charId=$characterId Trang bị đã bị khóa itemId=${equipItem.itemId}" }
            return null
        }

        petSystem.updateFollowNpcEquip(characterId, followSlot, fitType, null) ?: return null

        logger.info { "Võ tướng vứt trang bị: charId=$characterId slot=$followSlot fitType=$fitType itemId=${equipItem.itemId}" }
        return equipItem
    }
}