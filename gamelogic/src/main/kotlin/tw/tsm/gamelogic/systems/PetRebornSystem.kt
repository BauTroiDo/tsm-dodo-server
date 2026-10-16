package tw.tsm.gamelogic.systems

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.data.ItemDef
import tw.tsm.domain.interfaces.repositories.InventoryRepository
import tw.tsm.domain.data.NpcDef
import tw.tsm.domain.interfaces.StatCalculator
import tw.tsm.domain.valueobjects.FollowNpcData

private val logger = KotlinLogging.logger {}

/**
 * Kết quả chuyển sinh
 */
sealed class RebornResult {
    /**
     * Thành công
     *
     * @property pet Dữ liệu võ tướng mới sau khi chuyển sinh
     * @property consumedSlot Vị trí ô túi đồ bị trừ tín vật
     */
    data class Ok(val pet: FollowNpcData, val consumedSlot: Int) : RebornResult()

    /** Thất bại — msgKind tương ứng với loại thông báo S:044-001 */
    data class Fail(val msgKind: Int) : RebornResult()
}

/**
 * Hệ thống chuyển sinh võ tướng — Tương ứng với mainKind=44 / UINpcTurn.lua
 *
 * Chuyển sinh lần 1 (C:044-001): Cấp độ ≥30, Trung thành ≥60, Trong túi có tín vật chuyển sinh lần 1 (kind=37, attrKind=65)
 * Chuyển sinh lần 2 (C:044-003): Cấp độ ≥60, Trung thành ≥100, Trong túi có tín vật chuyển sinh lần 2 (kind=51, attrKind=67)
 *
 * Định nghĩa tín vật (UINpcTurn.CheckTurn):
 * attr1.value = ID NPC võ tướng gốc, attr2.value = ID NPC sau khi chuyển sinh
 *
 * Hiệu ứng chuyển sinh (Phản chiếu tính năng xem trước chuyển sinh của UINpcTurn.UpdateUI):
 * ID NPC mới, Cấp độ 1, Trung thành 60, Thuộc tính trở về giá trị cơ bản của NPC mới, Kỹ năng làm mới về Lv1 của NPC mới.
 *
 * @param petSystem Thao tác lưu tạm (cache) và thay thế võ tướng
 * @param inventorySystem Khấu trừ vật phẩm tín vật
 * @param statCalculator Tính toán đầy HP đầy SP sau khi chuyển sinh
 * @param npcDefs Định nghĩa NPC (Nguồn thuộc tính cơ bản sau khi chuyển sinh)
 * @param itemDefs Định nghĩa vật phẩm (Dùng để tìm kiếm tín vật)
 */
class PetRebornSystem(
    private val petSystem: PetSystem,
    private val inventorySystem: InventorySystem,
    private val statCalculator: StatCalculator,
    private val npcDefs: Map<Int, NpcDef>,
    private val itemDefs: Map<Int, ItemDef>,
    private val inventoryRepository: InventoryRepository
) {

    /**
     * Thực thi chuyển sinh võ tướng
     *
     * Thứ tự xác thực phản chiếu lại UINpcTurn.CheckTurn của client và tương ứng với mã lỗi S:044-001.
     *
     * @param charId ID nhân vật
     * @param followSlot Ô võ tướng mang theo (hệ cơ số 1 / 1-based)
     * @param rebornKind 1=Chuyển sinh lần 1, 2=Chuyển sinh lần 2
     */
    suspend fun reborn(charId: Long, followSlot: Int, rebornKind: Int): RebornResult =
        CharacterPersistenceGuard.withLock(charId) {
        val pet = petSystem.getFollowNpc(charId, followSlot)
            ?: return@withLock RebornResult.Fail(MSG_NO_PET)

        // canGrow 0=Không thể trưởng thành, 2=NPC nhiệm vụ → Không thể chuyển sinh
        if (pet.canGrow == 0 || pet.canGrow == 2) {
            return@withLock RebornResult.Fail(MSG_CANNOT_GROW)
        }

        // Ngưỡng cấp độ: Chuyển sinh lần 1 cần 30 cấp / Chuyển sinh lần 2 cần 60 cấp
        // Enforce the correct NPC generation and reject duplicate/replayed turns.
        if (rebornKind !in REBORN_FIRST..REBORN_SECOND ||
            pet.turnKind != rebornKind - 1 ||
            npcDefs[pet.npcId]?.turn != rebornKind - 1
        ) return@withLock RebornResult.Fail(MSG_NO_PET)

        if (rebornKind == REBORN_FIRST && pet.level < 30) return@withLock RebornResult.Fail(MSG_LV_BELOW_30)
        if (rebornKind == REBORN_SECOND && pet.level < 60) return@withLock RebornResult.Fail(MSG_LV_BELOW_60)

        // Ngưỡng độ trung thành: Chuyển sinh lần 1 cần 60 / Chuyển sinh lần 2 cần 100
        if (rebornKind == REBORN_FIRST && pet.loyalty < 60) return@withLock RebornResult.Fail(MSG_FAITH_BELOW_60)
        if (rebornKind == REBORN_SECOND && pet.loyalty < 100) return@withLock RebornResult.Fail(MSG_FAITH_BELOW_100)

        // Ô trang bị phải được làm trống (TextData 21078 "Võ tướng muốn chuyển sinh chưa làm trống ô trang bị")
        if (pet.equips.values.any { !it.isEmpty }) {
            return@withLock RebornResult.Fail(MSG_EQUIP_NOT_EMPTY)
        }

        // Tìm định nghĩa tín vật chuyển sinh: Lần 1 kind=37/attrKind=65, Lần 2 kind=51/attrKind=67
        val itemKind = if (rebornKind == REBORN_FIRST) ITEM_KIND_FIRST_TOKEN else ITEM_KIND_SECOND_TOKEN
        val attrKind = if (rebornKind == REBORN_FIRST) ATTR_KIND_FIRST else ATTR_KIND_SECOND
        // Follow UINpcTurn.lua: choose from the real BAG, not the first DAT mapping.
        // NPC 10001 has two valid first-rebirth tokens with different targets.
        val heldToken = inventorySystem.getBag(charId).entries.sortedBy { it.key }
            .firstOrNull { (_, thing) ->
                val def = itemDefs[thing.itemId]
                thing.quantity > 0 && def?.kind == itemKind &&
                    def.attr1Kind == attrKind && def.attr1Value == pet.npcId &&
                    def.attr2Kind == attrKind
            } ?: return@withLock RebornResult.Fail(MSG_NO_TOKEN)
        val tokenItemId = heldToken.value.itemId
        val bagSlot = heldToken.key
        val targetNpcId = itemDefs.getValue(tokenItemId).attr2Value

        val targetDef = npcDefs[targetNpcId] ?: run {
            logger.error { "Chuyển sinh thất bại: Tín vật $tokenItemId trỏ đến NPC $targetNpcId không tồn tại" }
            return@withLock RebornResult.Fail(MSG_EXCEPTION)
        }

        // Trong túi đồ có tín vật

        // Võ tướng sau khi chuyển sinh không được sở hữu trùng lặp: Trên người (mang theo + xe ngựa) / Khách sạn (bao gồm kho võ tướng)
        if (petSystem.hasNpcOnBody(charId, targetNpcId)) {
            return@withLock RebornResult.Fail(MSG_DUP_ON_BODY)
        }
        if (petSystem.hasNpcInInn(charId, targetNpcId)) {
            return@withLock RebornResult.Fail(if (rebornKind == REBORN_FIRST) MSG_DUP_IN_INN else MSG_DUP_IN_INN_2)
        }

        // Khấu trừ tín vật

        // Tạo võ tướng sau khi chuyển sinh: Cấp độ 1, Trung thành 60, Đầy HP đầy SP, Thuộc tính cơ bản của NPC mới
        // Tham số turn dùng để tính HP tối đa sẽ sử dụng bậc chuyển sinh của NPC mới (Phản chiếu lại Calc.GetMaxHp của bản xem trước trong UINpcTurn)
        val maxHp = statCalculator.calculateMaxHp(level = 1, hpx = targetDef.hpx, turn = targetDef.turn)
        val maxSp = statCalculator.calculateMaxSp(level = 1, spx = targetDef.spx, turn = targetDef.turn)
        val newPet = FollowNpcData(
            instanceId = pet.instanceId,
            slotIndex = followSlot,
            npcId = targetNpcId,
            storageType = PetSystem.STORAGE_FOLLOW,
            name = targetDef.name,
            level = 1,
            hp = maxHp,
            sp = maxSp,
            atk = targetDef.atk,
            def = targetDef.def,
            agi = targetDef.agi,
            intelligence = targetDef.intelligence,
            hpx = targetDef.hpx,
            spx = targetDef.spx,
            loyalty = 60,
            skill1Lv = if (targetDef.skills.getOrElse(0) { 0 } != 0) 1 else 0,
            skill2Lv = if (targetDef.skills.getOrElse(1) { 0 } != 0) 1 else 0,
            skill3Lv = if (targetDef.skills.getOrElse(2) { 0 } != 0) 1 else 0,
            canGrow = pet.canGrow,
            turnKind = rebornKind
        )
        // TODO: Việc bản gốc có tặng điểm kỹ năng/điểm thuộc tính dựa trên cấp độ cũ hay không cần được xác minh thêm (Không sử dụng công thức tự chế level/(2*Số lần chuyển sinh) của Private server PC)

        // SQL commit comes BEFORE cache publication. Failure leaves both intact.
        val commit = try {
            inventoryRepository.commitPetRebirthAtomic(
                charId, bagSlot, tokenItemId, pet, newPet
            )
        } catch (error: Exception) {
            logger.error(error) { "Atomic pet rebirth failed for characterId=$charId" }
            null
        } ?: return@withLock RebornResult.Fail(MSG_EXCEPTION)

        val applied = checkNotNull(petSystem.rebornFollowNpc(charId, followSlot, newPet)) {
            "Pet cache missing after durable rebirth; reconnect to reload"
        }
        inventorySystem.publishRebirthBagAfterCommit(charId, bagSlot, commit.bagAfter)

        logger.info { "${if (rebornKind == REBORN_FIRST) "Chuyển sinh lần 1" else "Chuyển sinh lần 2"} thành công: charId=$charId ${pet.npcId} → $targetNpcId" }
        return@withLock RebornResult.Ok(applied, bagSlot)
    }

    companion object {
        /** Chuyển sinh lần 1 */
        const val REBORN_FIRST = 1

        /** Chuyển sinh lần 2 */
        const val REBORN_SECOND = 2

        /** Loại vật phẩm tín vật chuyển sinh lần 1 (EItemKind) */
        private const val ITEM_KIND_FIRST_TOKEN = 37

        /** Loại vật phẩm tín vật chuyển sinh lần 2 */
        private const val ITEM_KIND_SECOND_TOKEN = 51

        /** Loại thuộc tính tín vật chuyển sinh lần 1 */
        private const val ATTR_KIND_FIRST = 65

        /** Loại thuộc tính tín vật chuyển sinh lần 2 */
        private const val ATTR_KIND_SECOND = 67

        // ── Các loại thông báo S:044-001 ──

        /** Chuyển sinh thành công */
        const val MSG_SUCCESS = 0

        /** Không có tín vật chuyển sinh cần thiết */
        const val MSG_NO_TOKEN = 1

        /** Người chơi không có võ tướng nào có thể chuyển sinh mang theo trên người */
        const val MSG_NO_PET = 2

        /** Võ tướng muốn chuyển sinh có cấp độ nhỏ hơn 30 */
        const val MSG_LV_BELOW_30 = 3

        /** Người chơi không thể sở hữu trùng lặp võ tướng sau khi chuyển sinh trong khách sạn */
        const val MSG_DUP_IN_INN = 4

        /** Người chơi không thể sở hữu trùng lặp võ tướng sau khi chuyển sinh trên người */
        const val MSG_DUP_ON_BODY = 5

        /** Võ tướng được chọn không thể trưởng thành */
        const val MSG_CANNOT_GROW = 6

        /** Võ tướng muốn chuyển sinh có độ trung thành nhỏ hơn 60 */
        const val MSG_FAITH_BELOW_60 = 7

        /** Ô trang bị của võ tướng muốn chuyển sinh chưa được làm trống (Kích hoạt khi bất kỳ ô nào trong FollowNpcData.equips chưa được làm trống) */
        const val MSG_EQUIP_NOT_EMPTY = 8

        /** Người chơi không thể sở hữu trùng lặp võ tướng sau khi chuyển sinh trong khách sạn (Dùng cho chuyển sinh lần 2, văn bản giống mục 4) */
        const val MSG_DUP_IN_INN_2 = 9

        /** Võ tướng muốn chuyển sinh có cấp độ nhỏ hơn 60 (Chuyển sinh lần 2) */
        const val MSG_LV_BELOW_60 = 10

        /** Võ tướng muốn chuyển sinh có độ trung thành nhỏ hơn 100 (Chuyển sinh lần 2) */
        const val MSG_FAITH_BELOW_100 = 11

        /** Lỗi ngoại lệ */
        const val MSG_EXCEPTION = 255
    }
}