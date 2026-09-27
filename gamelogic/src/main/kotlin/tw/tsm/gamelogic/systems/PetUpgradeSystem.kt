package tw.tsm.gamelogic.systems

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.data.NpcDef
import tw.tsm.domain.data.NpcUpgradeDef
import tw.tsm.domain.valueobjects.FollowNpcData
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

private val logger = KotlinLogging.logger {}

/**
 * Dữ liệu tạm thời cường hóa võ tướng — Tương ứng với FNpc_ID + UpgradeAttr của bản PC
 *
 * @property followSlot Vị trí slot võ tướng đang được cường hóa
 * @property npcId ID NPC của võ tướng đang được cường hóa
 * @property gains Giá trị cộng thêm cho 6 chỉ số được quay trước [int, atk, def, agi, hpx, spx]
 * @property targetLv Mức sao mục tiêu
 */
data class PendingUpgrade(
    val followSlot: Int,
    val npcId: Int,
    val gains: IntArray,
    val targetLv: Int
)

/**
 * Kết quả xác nhận cường hóa — Dùng cho Handler để gửi các gói tin trừ tiền/trừ vật phẩm/cập nhật sao
 *
 * @property followSlot Vị trí slot võ tướng
 * @property upgraded Dữ liệu võ tướng sau khi thăng cấp
 * @property moneySpent Tiền game đã trừ (0=chưa trừ)
 * @property debrisAfter Số dư mảnh võ tướng sau khi trừ (-1=chưa trừ)
 * @property debrisMax Giới hạn bộ đếm mảnh võ tướng (Dùng để gửi qua S:085-002)
 * @property consumedSlots Danh sách các ô trong túi đồ bị trừ List<Pair<Ô túi đồ, Số lượng>>
 */
data class UpgradeConfirmResult(
    val followSlot: Int,
    val upgraded: FollowNpcData,
    val moneySpent: Long,
    val debrisAfter: Int,
    val debrisMax: Int,
    val consumedSlots: List<Pair<Int, Int>>
)

/**
 * Kết quả khởi tạo cường hóa
 */
sealed class UpgradeStartResult {
    /** Thành công — Trả về các thuộc tính đã quay trước để xem trước qua S:008-011 */
    data class Ok(val pending: PendingUpgrade) : UpgradeStartResult()

    /** Thất bại (Xác thực không đạt, nguyên nhân được ghi lại trong log) */
    object Fail : UpgradeStartResult()
}

/**
 * Hệ thống cường hóa võ tướng — Tương ứng với quy trình cường hóa mainKind=74 / UIStatus.lua
 *
 * Quy trình 3 bước:
 * 1. C:074-001 Khởi tạo → Xác thực nhu cầu → Quay trước giá trị cộng thêm 6 chỉ số → Xem trước S:008-011
 * 2. C:074-002 Xác nhận → Trừ tài nguyên (Tiền game/Mảnh võ tướng/Sách ảnh) → Áp dụng thuộc tính và cấp sao
 * 3. C:074-003 Hủy bỏ → Xóa bộ nhớ tạm
 *
 * Dữ liệu yêu cầu: NpcUpgrade_C.dat (Cấp sao → Vàng/Mảnh/Sách ảnh/Điểm ngẫu nhiên).
 *
 * @param petSystem Quản lý cache và áp dụng thuộc tính võ tướng
 * @param moneySystem Trừ tiền game
 * @param roleCountSystem Mảnh võ tướng (RoleCount[110])
 * @param inventorySystem Trừ vật phẩm sách ảnh
 * @param npcDefs Định nghĩa NPC (upgradeItemId là ID vật phẩm sách ảnh)
 * @param upgradeDefs Định nghĩa các cấp sao cường hóa
 */
class PetUpgradeSystem(
    private val petSystem: PetSystem,
    private val moneySystem: MoneySystem,
    private val roleCountSystem: RoleCountSystem,
    private val inventorySystem: InventorySystem,
    private val npcDefs: Map<Int, NpcDef>,
    private val upgradeDefs: Map<Int, NpcUpgradeDef>
) {

    /** charId → Bộ nhớ tạm cường hóa (Chỉ cho phép một tiến trình tại một thời điểm, khởi tạo mới sẽ ghi đè cái cũ) */
    private val pendingMap = ConcurrentHashMap<Long, PendingUpgrade>()

    /**
     * C:074-001 Khởi tạo cường hóa
     *
     * Xác thực phản chiếu UIStatus.OnClick_AttributeSubmit:
     * Võ tướng tồn tại và khớp npcId, canGrow có thể trưởng thành, bảng cấp sao tồn tại cấp tiếp theo,
     * Từ 10 sao trở lên yêu cầu chuyển sinh lần 2, Tiền game/Mảnh/Sách ảnh đủ số lượng.
     */
    fun startUpgrade(charId: Long, followSlot: Int, npcId: Int): UpgradeStartResult {
        val pet = petSystem.getFollowNpc(charId, followSlot) ?: run {
            logger.warn { "Khởi tạo cường hóa thất bại: charId=$charId slot=$followSlot Không có võ tướng" }
            return UpgradeStartResult.Fail
        }
        if (pet.npcId != npcId) {
            logger.warn { "Khởi tạo cường hóa thất bại: slot=$followSlot npcId không khớp (client=$npcId server=${pet.npcId})" }
            return UpgradeStartResult.Fail
        }
        // canGrow 0=Không thể trưởng thành, 2=NPC nhiệm vụ → Không thể cường hóa (UIStatus.lua: Contains(canGrow, 0, 2))
        if (pet.canGrow == 0 || pet.canGrow == 2) {
            logger.warn { "Khởi tạo cường hóa thất bại: npcId=$npcId canGrow=${pet.canGrow} Không thể cường hóa" }
            return UpgradeStartResult.Fail
        }

        val targetLv = pet.upgradeLv + 1
        val def = upgradeDefs[targetLv] ?: run {
            logger.warn { "Khởi tạo cường hóa thất bại: npcId=$npcId Đã đạt giới hạn cấp sao tối đa (upgradeLv=${pet.upgradeLv})" }
            return UpgradeStartResult.Fail
        }
        // Cần chuyển sinh lần 2 mới được lên 11-15 sao
        if (pet.upgradeLv >= 10 && pet.turnKind < 2) {
            logger.warn { "Khởi tạo cường hóa thất bại: npcId=$npcId Chưa chuyển sinh lần 2, không thể lên từ 11 sao trở lên" }
            return UpgradeStartResult.Fail
        }

        if (!validateResources(charId, npcId, def)) return UpgradeStartResult.Fail

        // Quay trước 6 chỉ số: Phân phối ngẫu nhiên attributeAdd điểm vào [int, atk, def, agi, hpx, spx]
        val gains = IntArray(6)
        repeat(def.attributeAdd) { gains[Random.nextInt(6)]++ }

        val pending = PendingUpgrade(followSlot, npcId, gains, targetLv)
        pendingMap[charId] = pending
        logger.info { "Khởi tạo cường hóa: charId=$charId npcId=$npcId → ${targetLv} sao gains=${gains.joinToString()}" }
        return UpgradeStartResult.Ok(pending)
    }

    /**
     * C:074-002 Xác nhận cường hóa
     *
     * Xác thực lại tài nguyên → Trừ tài nguyên → Áp dụng thuộc tính và cấp sao.
     *
     * @return Kết quả xác nhận (bao gồm chi tiết trừ tài nguyên để Handler gửi gói tin), null = Thất bại
     */
    suspend fun confirmUpgrade(charId: Long): UpgradeConfirmResult? {
        val pending = pendingMap[charId] ?: run {
            logger.warn { "Xác nhận cường hóa thất bại: charId=$charId Không có dữ liệu tạm thời" }
            return null
        }
        val pet = petSystem.getFollowNpc(charId, pending.followSlot)
        if (pet == null || pet.npcId != pending.npcId || pet.upgradeLv + 1 != pending.targetLv) {
            logger.warn { "Xác nhận cường hóa thất bại: charId=$charId Trạng thái võ tướng đã thay đổi" }
            pendingMap.remove(charId)
            return null
        }
        val def = upgradeDefs[pending.targetLv] ?: return null

        // Xác thực lại tài nguyên (Trong khoảng thời gian từ khởi tạo đến xác nhận có thể đã bị tiêu xài mất)
        if (!validateResources(charId, pending.npcId, def)) {
            pendingMap.remove(charId)
            return null
        }

        // Trừ tiền game
        if (def.requireMoney > 0 && !moneySystem.subtractGold(charId, def.requireMoney.toInt())) {
            logger.warn { "Xác nhận cường hóa thất bại: charId=$charId Trừ tiền game thất bại" }
            pendingMap.remove(charId)
            return null
        }

        // Trừ mảnh võ tướng (RoleCount[110])
        var debrisAfter = -1
        var debrisMax = 0
        if (def.requireDebris > 0) {
            val current = roleCountSystem.getValue(charId, RoleCountSystem.NPC_DEBRIS)
            debrisMax = roleCountSystem.getMaxValue(charId, RoleCountSystem.NPC_DEBRIS)
            debrisAfter = current - def.requireDebris
            roleCountSystem.setValue(charId, RoleCountSystem.NPC_DEBRIS, debrisAfter, debrisMax)
        }

        // Trừ sách ảnh (Có thể trải dài qua nhiều ô trong túi đồ)
        val consumedSlots = mutableListOf<Pair<Int, Int>>()
        if (def.requirePicture > 0) {
            val upgradeItemId = npcDefs[pending.npcId]?.upgradeItemId ?: 0
            var remaining = def.requirePicture
            for ((slot, thing) in inventorySystem.getBag(charId)) {
                if (remaining <= 0) break
                if (thing.itemId != upgradeItemId || thing.quantity <= 0) continue
                val take = minOf(remaining, thing.quantity)
                if (inventorySystem.removeFromBag(charId, slot, take)) {
                    consumedSlots.add(slot to take)
                    remaining -= take
                }
            }
            if (remaining > 0) {
                // Về lý thuyết validateResources đã chặn lại; ghi log phòng thủ
                logger.error { "Xác nhận cường hóa bất thường: charId=$charId Sách ảnh trừ không đủ (Thiếu $remaining)" }
            }
        }

        // Áp dụng thuộc tính và cấp sao
        val upgraded = petSystem.applyUpgrade(charId, pending.followSlot, pending.gains) ?: run {
            pendingMap.remove(charId)
            return null
        }

        // TODO: Mối liên kết giữa cấp độ kỹ năng cường hóa def.skillLv và hệ thống kỹ năng sẽ được hiện thực sau khi xác minh thêm

        pendingMap.remove(charId)
        logger.info { "Cường hóa hoàn tất: charId=$charId npcId=${pending.npcId} → ${upgraded.upgradeLv} sao" }
        return UpgradeConfirmResult(
            followSlot = pending.followSlot,
            upgraded = upgraded,
            moneySpent = def.requireMoney,
            debrisAfter = debrisAfter,
            debrisMax = debrisMax,
            consumedSlots = consumedSlots
        )
    }

    /**
     * C:074-003 Hủy bỏ cường hóa
     *
     * @return true = Có dữ liệu tạm thời bị xóa
     */
    fun cancelUpgrade(charId: Long): Boolean {
        val removed = pendingMap.remove(charId) != null
        if (removed) logger.debug { "Hủy bỏ cường hóa: charId=$charId" }
        return removed
    }

    /**
     * Lấy dữ liệu tạm thời (Dùng cho dọn dẹp khi ngắt kết nối, v.v.)
     */
    fun clear(charId: Long) {
        pendingMap.remove(charId)
    }

    /**
     * Xác thực tài nguyên cường hóa có đủ hay không
     */
    private fun validateResources(charId: Long, npcId: Int, def: NpcUpgradeDef): Boolean {
        val npcDef = npcDefs[npcId] ?: return false
        if (def.requirePicture > 0 && npcDef.upgradeItemId == 0) {
            logger.warn { "Xác thực cường hóa thất bại: npcId=$npcId Không có định nghĩa vật phẩm sách ảnh" }
            return false
        }
        if (moneySystem.getGold(charId) < def.requireMoney) {
            logger.warn { "Xác thực cường hóa thất bại: charId=$charId Không đủ tiền game (Cần ${def.requireMoney})" }
            return false
        }
        if (roleCountSystem.getValue(charId, RoleCountSystem.NPC_DEBRIS) < def.requireDebris) {
            logger.warn { "Xác thực cường hóa thất bại: charId=$charId Không đủ mảnh võ tướng (Cần ${def.requireDebris})" }
            return false
        }
        if (def.requirePicture > 0) {
            val pictureCount = inventorySystem.getBag(charId).values
                .filter { it.itemId == npcDef.upgradeItemId }
                .sumOf { it.quantity }
            if (pictureCount < def.requirePicture) {
                logger.warn { "Xác thực cường hóa thất bại: charId=$charId Không đủ sách ảnh (Cần ${def.requirePicture}, đang có $pictureCount)" }
                return false
            }
        }
        return true
    }
}