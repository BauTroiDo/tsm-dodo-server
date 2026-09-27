package tw.tsm.gamelogic.battle

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.constants.EAttribute
import tw.tsm.domain.constants.EItemUseKind
import tw.tsm.domain.data.ItemDef
import tw.tsm.gamelogic.systems.InventorySystem

private val logger = KotlinLogging.logger {}

/**
 * Kết quả sử dụng vật phẩm trong chiến đấu
 *
 * @property bagSlot Ô túi đồ chứa vật phẩm bị tiêu hao (Dùng để gửi S:023-009 thông báo cho người sở hữu)
 * @property attrChanges Thay đổi thuộc tính có hiệu lực lên mục tiêu (Danh sách attr trong gói tin S:050-001)
 */
data class BattleItemOutcome(
    val bagSlot: Int,
    val attrChanges: List<BattleAttrChange>
)

/**
 * Bộ phân giải (Resolver) vật phẩm chiến đấu — Tương ứng với Client C:050-002 Sử dụng vật phẩm
 *
 * Hoạt ảnh Client: Trình bày thông qua gói tin S:050-001 (skillId=19001 Sử dụng vật phẩm hỗ trợ → UseItem.lua),
 * Chỉ số hồi phục hiển thị được điều khiển bởi danh sách attr trong gói tin.
 *
 * Hiện tại hỗ trợ các loại vật phẩm hồi phục (HP/SP, bao gồm hồi phục toàn phần (full) và định lượng).
 * TODO: Độc dược (kind=19, cần trừ máu mỗi hiệp khi ở trạng thái trúng độc), Hồi sinh (kind=50),
 *       Bùa chú (skillId=20008 + trường thông tin cuối của itemSkillId) chờ thực hiện sau.
 *
 * @param inventorySystem Truy cập túi đồ (Tìm kiếm và tiêu hao đạo cụ)
 * @param itemDefs Bảng định nghĩa vật phẩm
 */
class BattleItemResolver(
    private val inventorySystem: InventorySystem,
    private val itemDefs: Map<Int, ItemDef>
) {

    /**
     * Phân giải và thực thi việc sử dụng vật phẩm trong chiến đấu
     *
     * Xác thực → Tính toán hiệu ứng → Áp dụng lên mục tiêu → Trừ vật phẩm trong túi đồ.
     *
     * @param user Người sử dụng (Người chơi hoặc Võ tướng của họ)
     * @param target Đơn vị chiến đấu mục tiêu
     * @param itemId ID vật phẩm
     * @param ownerCharId ID nhân vật của người sở hữu vật phẩm (characterId của người chơi điều khiển)
     * @return Kết quả sử dụng; Trả về null nếu xác thực thất bại hoặc vật phẩm không được hỗ trợ (không bị tiêu hao)
     */
    suspend fun resolveItemUse(
        user: BattleFighter,
        target: BattleFighter,
        itemId: Int,
        ownerCharId: Long
    ): BattleItemOutcome? {
        val def = itemDefs[itemId] ?: run {
            logger.warn { "Sử dụng vật phẩm chiến đấu thất bại: Không tìm thấy định nghĩa cho itemId=$itemId" }
            return null
        }

        // Xác thực phe phái (Đối chiếu với FightRoleController.lua:
        // Thức ăn(17)/Thuốc bổ(18)/Chức năng auto(38)/Hồi sinh(50)/Vật phẩm tu luyện(52) không thể dùng lên phe địch; Độc dược(19) không thể dùng lên phe mình)
        val sameSide = user.isPlayerSide == target.isPlayerSide
        if (!sameSide && def.kind in FRIENDLY_ONLY_KINDS) {
            logger.warn { "Từ chối vật phẩm chiến đấu: itemId=$itemId kind=${def.kind} không thể sử dụng lên phe địch" }
            return null
        }
        if (sameSide && def.kind == KIND_POISON) {
            logger.warn { "Từ chối vật phẩm chiến đấu: itemId=$itemId Không thể dùng độc dược lên phe mình" }
            return null
        }

        // Tính toán hiệu ứng (Hiện tại chỉ có loại hồi phục; các loại không được hỗ trợ sẽ không tiêu hao vật phẩm)
        val changes = resolveRecoveryEffect(def, target) ?: run {
            logger.warn { "Vật phẩm chiến đấu chưa được hỗ trợ: itemId=$itemId kind=${def.kind} specialAbility=${def.specialAbility} (TODO)" }
            return null
        }

        // Tìm ô trong túi đồ và tiêu hao (Client chỉ gửi itemId, Server sẽ lấy ô đầu tiên phù hợp)
        val bagSlot = inventorySystem.getBag(ownerCharId).entries
            .firstOrNull { it.value.itemId == itemId && it.value.quantity > 0 }
            ?.key
            ?: run {
                logger.warn { "Sử dụng vật phẩm chiến đấu thất bại: charId=$ownerCharId Trong túi không có itemId=$itemId" }
                return null
            }

        if (!inventorySystem.removeFromBag(ownerCharId, bagSlot, 1)) {
            logger.warn { "Sử dụng vật phẩm chiến đấu thất bại: charId=$ownerCharId Trừ itemId=$itemId thất bại" }
            return null
        }

        // Áp dụng hiệu ứng lên mục tiêu
        for (change in changes) {
            when (change.attrKind) {
                EAttribute.HP -> target.hp = (target.hp + change.value).coerceAtMost(target.maxHp)
                EAttribute.SP -> target.sp = (target.sp + change.value).coerceAtMost(target.maxSp)
            }
        }

        logger.info { "Vật phẩm chiến đấu: ${user.name} sử dụng lên ${target.name} itemId=$itemId (${changes.joinToString { "${it.attrKind}+${it.value}" }})" }
        return BattleItemOutcome(bagSlot, changes)
    }

    /**
     * Tính toán hiệu quả của vật phẩm hồi phục
     *
     * Đối chiếu với ngữ nghĩa ngoài trận đấu của ItemUseProcessor:
     * - specialAbility 25/165/54 = Hồi full HP/SP/Cả hai
     * - Còn lại dựa vào attr1Kind/attr2Kind (EAttribute.HP=25, SP=26) để hồi phục lượng cố định
     *
     * @return Danh sách thay đổi thuộc tính (Có thể rỗng = Đã đầy HP/SP nhưng vẫn tiêu hao vật phẩm); Trả về null nếu không phải vật phẩm hồi phục
     */
    private fun resolveRecoveryEffect(def: ItemDef, target: BattleFighter): List<BattleAttrChange>? {
        val changes = mutableListOf<BattleAttrChange>()

        when (def.specialAbility) {
            EItemUseKind.HP_FULL -> {
                addHpChange(changes, target, (target.maxHp - target.hp).toInt())
                return changes
            }
            EItemUseKind.SP_FULL -> {
                addSpChange(changes, target, (target.maxSp - target.sp).toInt())
                return changes
            }
            EItemUseKind.HP_SP_FULL -> {
                addHpChange(changes, target, (target.maxHp - target.hp).toInt())
                addSpChange(changes, target, (target.maxSp - target.sp).toInt())
                return changes
            }
        }

        // Hồi phục lượng cố định: attr1/attr2 phải có ít nhất một mục là HP/SP, nếu không sẽ bị xem là vật phẩm không hồi phục
        var isRecoveryItem = false
        for ((kind, value) in listOf(def.attr1Kind to def.attr1Value, def.attr2Kind to def.attr2Value)) {
            when (kind) {
                EAttribute.HP -> {
                    isRecoveryItem = true
                    addHpChange(changes, target, minOf(value.toLong(), target.maxHp - target.hp).toInt())
                }
                EAttribute.SP -> {
                    isRecoveryItem = true
                    addSpChange(changes, target, minOf(value.toLong(), target.maxSp - target.sp).toInt())
                }
            }
        }

        return if (isRecoveryItem) changes else null
    }

    private fun addHpChange(changes: MutableList<BattleAttrChange>, target: BattleFighter, amount: Int) {
        if (amount > 0) changes.add(BattleAttrChange(EAttribute.HP, amount, true))
    }

    private fun addSpChange(changes: MutableList<BattleAttrChange>, target: BattleFighter, amount: Int) {
        if (amount > 0) changes.add(BattleAttrChange(EAttribute.SP, amount, true))
    }

    companion object {
        /** Loại vật phẩm chỉ dùng được cho phe mình: Thức ăn/Thuốc bổ/Chức năng auto/Hồi sinh/Vật phẩm tu luyện */
        private val FRIENDLY_ONLY_KINDS = setOf(17, 18, 38, 50, 52)

        /** Độc dược (Chỉ dùng được lên phe địch) */
        private const val KIND_POISON = 19
    }
}