package tw.tsm.gamelogic.systems

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.constants.EAttribute
import tw.tsm.domain.constants.EItemUseKind
import tw.tsm.domain.data.BlissBagDef
import tw.tsm.domain.data.ItemDef

private val logger = KotlinLogging.logger {}

/**
 * Kết quả hiệu ứng sử dụng vật phẩm
 *
 * @param success Có sử dụng thành công hay không
 * @param attrChanges Danh sách thay đổi thuộc tính (attrKind, value, isPositive)
 * @param consumed Có tiêu hao vật phẩm hay không (một số hiệu ứng có thể thất bại nhưng không bị tiêu hao)
 */
/**
 * Vật phẩm thưởng từ túi phúc
 */
data class RewardItem(val itemId: Int, val count: Int)

data class ItemUseResult(
    val success: Boolean,
    val attrChanges: List<AttrChange> = emptyList(),
    val consumed: Boolean = true,
    val rewardItems: List<RewardItem> = emptyList(),
    /** true = Sử dụng bùa về thành, ItemHandler chịu trách nhiệm dịch chuyển về điểm lưu (save point) */
    val returnToSavePoint: Boolean = false
)

/**
 * Thay đổi thuộc tính đơn lẻ
 */
data class AttrChange(
    val attrKind: Int,
    val value: Int,
    val isPositive: Boolean
)

/**
 * Trình xử lý hiệu ứng sử dụng vật phẩm
 *
 * Quyết định hiệu ứng dựa trên ItemDef.specialAbility (EItemUseKind) và attr1Kind/attr2Kind.
 * Tương ứng với logic sử dụng vật phẩm của `Item.lua`.
 *
 * @param playerStateManager Quản lý trạng thái runtime của người chơi
 * @param itemDefs Bảng định nghĩa vật phẩm
 * @param blissBagDefs Bảng định nghĩa túi phúc (dùng khi specialAbility=48 để mở túi)
 */
class ItemUseProcessor(
    private val playerStateManager: PlayerStateManager,
    private val petSystem: PetSystem,
    private val itemDefs: Map<Int, ItemDef>,
    private val blissBagDefs: Map<Int, BlissBagDef> = emptyMap()
) {
    /**
     * Xử lý hiệu ứng sử dụng vật phẩm
     *
     * @param characterId ID nhân vật của người sử dụng
     * @param itemId ID vật phẩm
     * @param quantity Số lượng sử dụng
     * @param followIndex Chỉ số võ tướng (0=Nhân vật chính)
     * @param useType Loại sử dụng
     * @return Kết quả sử dụng (bao gồm thay đổi thuộc tính)
     */
    fun processUseItem(
        characterId: Long,
        itemId: Int,
        quantity: Int,
        followIndex: Int,
        useType: Int
    ): ItemUseResult {
        val def = itemDefs[itemId] ?: run {
            logger.warn { "Sử dụng vật phẩm thất bại: Không tìm thấy định nghĩa của itemId=$itemId" }
            return ItemUseResult(success = false, consumed = false)
        }

        // All 114 original DVT Treasure cards (Item_C.kind=98, specialAbility
        // 245, directUse=1) use a separate persistent C/S123 entitlement,
        // NOT generic attribute item use. Until an authoritative atomic
        // bag-to-Treasure banking transaction exists, never consume or claim
        // success for any kind98 card. This also covers malicious pet-use
        // requests before processPetItem can spend the card.
        if (def.kind == 98) {
            logger.warn { "Treasure card use blocked before ownership transfer: itemId=$itemId" }
            return ItemUseResult(success = false, consumed = false)
        }

        // DVT kind 89 is a Gia Vien furnishing entitlement, not a consumable
        // attribute item. Its source-backed quantity is checked by C106-006.
        // Generic direct-use must never destroy it, including forged pet-use
        // packets. Kind-38 gift boxes (ability 48) remain redeemable normally.
        if (def.kind == 89) {
            logger.info { "Gia Vien furniture use blocked: itemId=$itemId characterId=$characterId" }
            return ItemUseResult(success = false, consumed = false)
        }

        val state = playerStateManager.getState(characterId) ?: run {
            logger.warn { "Sử dụng vật phẩm thất bại: Không tìm thấy trạng thái runtime của nhân vật $characterId" }
            return ItemUseResult(success = false, consumed = false)
        }

        val specialAbility = def.specialAbility
        logger.debug { "Xử lý sử dụng vật phẩm: itemId=$itemId specialAbility=$specialAbility followIndex=$followIndex attr1Kind=${def.attr1Kind} attr1Value=${def.attr1Value} attr2Kind=${def.attr2Kind} attr2Value=${def.attr2Value}" }

        // Sử dụng vật phẩm cho võ tướng (followIndex > 0)
        if (followIndex > 0) {
            return processPetItem(characterId, followIndex, def, quantity)
        }

        return when (specialAbility) {
            EItemUseKind.RETURN_SAVE_POINT -> processReturnSavePoint()
            EItemUseKind.HP_FULL -> processFullHp(characterId)
            EItemUseKind.SP_FULL -> processFullSp(characterId)
            EItemUseKind.HP_SP_FULL -> processFullHpSp(characterId)
            EItemUseKind.EXP_EQUIP_150 -> processExpBuff(characterId, 1.5)
            EItemUseKind.EXP_EQUIP_200 -> processExpBuff(characterId, 2.0)
            EItemUseKind.EXP_EQUIP_ARG -> processExpBuff(characterId, def.attr1Value.toDouble() / 100.0)
            EItemUseKind.EXP_GOD -> {
                // Explicit C023-015 path must commit persistent godMission before consuming.
                // Never treat skillLink battle charges as a mutable runtime EXP multiplier.
                logger.warn { "EXP_GOD needs ItemHandler persistent redemption: itemId=$itemId" }
                ItemUseResult(success = false, consumed = false)
            }
            EItemUseKind.NO_ENCOUNTER -> processNoEncounter(characterId)
            EItemUseKind.GIFT_BOX -> processGiftBox(itemId, quantity)
            EItemUseKind.ELF_TOKEN -> {
                // Must be handled by ElfTokenUnlockService; no generic consumption.
                logger.warn { "Elf token rejected by generic item use: itemId=$itemId" }
                ItemUseResult(success = false, consumed = false)
            }
            EItemUseKind.SOUL -> {
                // DVT item 240 is the Soul acquisition/use path. The client only deserializes
                // a fully generated SoulThing; authoritative initial stat/skill generation is
                // server-side and is still evidence-blocked. Never consume the item as a generic
                // no-op while that generator is unavailable.
                logger.warn { "Tạm hoãn dùng vật phẩm Hồn: itemId=$itemId - chưa khóa generator Soul ĐVT" }
                ItemUseResult(success = false, consumed = false)
            }
            else -> processGenericAttrItem(characterId, def, quantity)
        }
    }

    /**
     * Hồi đầy HP
     */
    private fun processFullHp(characterId: Long): ItemUseResult {
        val recovered = playerStateManager.fullHp(characterId)
        if (recovered <= 0) return ItemUseResult(success = true)
        val hp = playerStateManager.getState(characterId)?.hp ?: return ItemUseResult(success = false, consumed = false)
        return ItemUseResult(
            success = true,
            // S:008-001 is an absolute SetAttribute packet, not a delta packet.
            attrChanges = listOf(AttrChange(EAttribute.HP, hp.toInt(), true))
        )
    }

    /**
     * Hồi đầy SP
     */
    private fun processFullSp(characterId: Long): ItemUseResult {
        val recovered = playerStateManager.fullSp(characterId)
        if (recovered <= 0) return ItemUseResult(success = true)
        val sp = playerStateManager.getState(characterId)?.sp ?: return ItemUseResult(success = false, consumed = false)
        return ItemUseResult(
            success = true,
            // S:008-001 is an absolute SetAttribute packet, not a delta packet.
            attrChanges = listOf(AttrChange(EAttribute.SP, sp, true))
        )
    }

    /**
     * Hồi đầy HP + SP
     */
    private fun processFullHpSp(characterId: Long): ItemUseResult {
        val hpRecovered = playerStateManager.fullHp(characterId)
        val spRecovered = playerStateManager.fullSp(characterId)
        val state = playerStateManager.getState(characterId) ?: return ItemUseResult(success = false, consumed = false)
        val changes = mutableListOf<AttrChange>()
        if (hpRecovered > 0) changes.add(AttrChange(EAttribute.HP, state.hp.toInt(), true))
        if (spRecovered > 0) changes.add(AttrChange(EAttribute.SP, state.sp, true))
        return ItemUseResult(success = true, attrChanges = changes)
    }

    /**
     * Buff hệ số nhân kinh nghiệm
     */
    private fun processExpBuff(characterId: Long, multiplier: Double): ItemUseResult {
        val state = playerStateManager.getState(characterId) ?: return ItemUseResult(success = false, consumed = false)
        state.expMultiplier = multiplier
        logger.info { "Thiết lập hệ số kinh nghiệm: characterId=$characterId multiplier=$multiplier" }
        return ItemUseResult(success = true)
    }

    /**
     * Không gặp địch (20 giây)
     */
    private fun processNoEncounter(characterId: Long): ItemUseResult {
        val state = playerStateManager.getState(characterId) ?: return ItemUseResult(success = false, consumed = false)
        state.noEncounterUntil = System.currentTimeMillis() + 20_000
        logger.info { "Thiết lập không gặp địch: characterId=$characterId kéo dài 20 giây" }
        return ItemUseResult(success = true)
    }

    /**
     * Bùa về thành — specialAbility=2
     *
     * Tham khảo logic về thành useItemChar() của tskero.
     * Việc dịch chuyển thực tế do ItemHandler thực thi dựa trên cờ returnToSavePoint.
     */
    private fun processReturnSavePoint(): ItemUseResult {
        logger.info { "Sử dụng bùa về thành" }
        return ItemUseResult(success = true, returnToSavePoint = true)
    }

    /**
     * Sử dụng vật phẩm cho võ tướng
     *
     * Tham khảo logic useItemPet(qty, pet) của tskero:
     * - attr1Kind=25(HP): Hồi phục HP võ tướng
     * - attr1Kind=26(SP): Hồi phục SP võ tướng
     * - attr1Kind=64(FAITH): Tăng độ trung thành của võ tướng (TODO)
     *
     * @param followSlot Ô chứa võ tướng đi cùng (hệ cơ số 1 / 1-based)
     */
    private fun processPetItem(
        characterId: Long,
        followSlot: Int,
        def: ItemDef,
        quantity: Int
    ): ItemUseResult {
        val pet = petSystem.getFollowNpc(characterId, followSlot)
        if (pet == null) {
            logger.warn { "Sử dụng vật phẩm cho võ tướng thất bại: Không tìm thấy followSlot=$followSlot" }
            return ItemUseResult(success = false, consumed = false)
        }

        val changes = mutableListOf<AttrChange>()

        // Xử lý attr1
        if (def.attr1Kind != 0 && def.attr1Value != 0) {
            val change = applyPetAttribute(characterId, followSlot, def.attr1Kind, def.attr1Value * quantity)
            if (change != null) changes.add(change)
        }

        // Xử lý attr2
        if (def.attr2Kind != 0 && def.attr2Value != 0) {
            val change = applyPetAttribute(characterId, followSlot, def.attr2Kind, def.attr2Value * quantity)
            if (change != null) changes.add(change)
        }

        logger.trace { "Sử dụng vật phẩm cho võ tướng: charId=$characterId followSlot=$followSlot hiệu ứng=${changes.size} mục" }
        return ItemUseResult(success = true, attrChanges = changes)
    }

    /**
     * Áp dụng thay đổi thuộc tính đơn lẻ cho võ tướng
     */
    private fun applyPetAttribute(characterId: Long, followSlot: Int, attrKind: Int, value: Int): AttrChange? {
        when (attrKind) {
            EAttribute.HP -> {
                val actual = petSystem.modifyFollowNpcHp(characterId, followSlot, value.toLong())
                if (actual == 0L) return null
                val hp = petSystem.getFollowNpc(characterId, followSlot)?.hp ?: return null
                return AttrChange(EAttribute.HP, hp.toInt(), true)
            }
            EAttribute.SP -> {
                val actual = petSystem.modifyFollowNpcSp(characterId, followSlot, value)
                if (actual == 0) return null
                val sp = petSystem.getFollowNpc(characterId, followSlot)?.sp ?: return null
                return AttrChange(EAttribute.SP, sp, true)
            }
            EAttribute.FAITH -> {
                // TODO: Hệ thống độ trung thành (cần thêm trường loyalty vào FollowNpcData)
                logger.debug { "Thay đổi độ trung thành của võ tướng: Tạm thời chưa được hiện thực value=$value" }
                return null
            }
            else -> {
                logger.debug { "Thay đổi thuộc tính võ tướng: attrKind=$attrKind tạm thời không hỗ trợ" }
                return null
            }
        }
    }

    /**
     * Mở túi phúc — specialAbility=48
     *
     * Nguồn: Item.lua:2298, BlissBagData.lua
     * Nhóm theo kind (1..kindCount), mỗi nhóm sẽ chọn ngẫu nhiên một vật phẩm thưởng dựa trên trọng số xác suất (pr).
     * Trả về danh sách phần thưởng, ItemHandler sẽ chịu trách nhiệm cho vào túi đồ thực tế.
     */
    private fun processGiftBox(itemId: Int, quantity: Int): ItemUseResult {
        val bagDef = blissBagDefs[itemId]
        if (bagDef == null) {
            logger.warn { "Mở túi phúc thất bại: Không tìm thấy blissBagDef itemId=$itemId" }
            return ItemUseResult(success = false, consumed = false)
        }

        val rewards = mutableListOf<RewardItem>()
        // Mỗi túi quà quay thưởng độc lập
        repeat(quantity) {
            for (k in 1..bagDef.kindCount) {
                val candidates = bagDef.items.filter { it.kind == k }
                if (candidates.isEmpty()) continue

                // Chọn ngẫu nhiên có trọng số
                val totalWeight = candidates.sumOf { it.pr }
                if (totalWeight <= 0) continue

                var roll = (1..totalWeight).random()
                for (c in candidates) {
                    roll -= c.pr
                    if (roll <= 0) {
                        rewards.add(RewardItem(c.itemId, c.count))
                        break
                    }
                }
            }
        }

        // Gộp các phần thưởng có cùng itemId (Giảm số lượng gói tin)
        val merged = rewards.groupBy { it.itemId }
            .map { (id, items) -> RewardItem(id, items.sumOf { it.count }) }

        logger.info { "Mở túi phúc: itemId=$itemId x$quantity → ${merged.size} loại phần thưởng ${merged.map { "${it.itemId}x${it.count}" }}" }
        return ItemUseResult(success = true, rewardItems = merged)
    }

    /**
     * Vật phẩm hồi phục thuộc tính chung (quyết định hiệu ứng dựa trên attr1Kind/attr2Kind)
     *
     * attr1Kind/attr2Kind tương ứng với EAttribute:
     * - 25(HP): Hồi phục attr1Value điểm HP
     * - 26(SP): Hồi phục attr1Value điểm SP
     * - Các thuộc tính khác: cộng trực tiếp giá trị
     */
    private fun processGenericAttrItem(
        characterId: Long,
        def: ItemDef,
        quantity: Int
    ): ItemUseResult {
        val changes = mutableListOf<AttrChange>()

        // Xử lý attr1
        if (def.attr1Kind != 0 && def.attr1Value != 0) {
            val change = applyAttribute(characterId, def.attr1Kind, def.attr1Value * quantity)
            if (change != null) changes.add(change)
        }

        // Xử lý attr2
        if (def.attr2Kind != 0 && def.attr2Value != 0) {
            val change = applyAttribute(characterId, def.attr2Kind, def.attr2Value * quantity)
            if (change != null) changes.add(change)
        }

        return ItemUseResult(success = true, attrChanges = changes)
    }

    /**
     * Áp dụng thay đổi thuộc tính đơn lẻ
     */
    private fun applyAttribute(characterId: Long, attrKind: Int, value: Int): AttrChange? {
        when (attrKind) {
            EAttribute.HP -> {
                val actual = playerStateManager.modifyHp(characterId, value.toLong())
                if (actual == 0L) return null
                val hp = playerStateManager.getState(characterId)?.hp ?: return null
                return AttrChange(EAttribute.HP, hp.toInt(), true)
            }
            EAttribute.SP -> {
                val actual = playerStateManager.modifySp(characterId, value)
                if (actual == 0) return null
                val sp = playerStateManager.getState(characterId)?.sp ?: return null
                return AttrChange(EAttribute.SP, sp, true)
            }
            else -> {
                // Các thuộc tính khác trả về sự thay đổi trực tiếp (Máy chủ không theo dõi các thuộc tính chi tiết, client tự áp dụng)
                return if (value != 0) AttrChange(attrKind, kotlin.math.abs(value), value > 0) else null
            }
        }
    }
}