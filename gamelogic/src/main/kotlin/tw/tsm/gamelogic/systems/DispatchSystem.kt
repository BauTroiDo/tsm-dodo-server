package tw.tsm.gamelogic.systems

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.constants.DispatchCompleteResult
import tw.tsm.domain.constants.DispatchStartResult
import tw.tsm.domain.data.DispatchBonusDef
import tw.tsm.domain.data.DispatchDef
import tw.tsm.domain.data.DispatchSaveData
import tw.tsm.domain.interfaces.repositories.DispatchRepository

private val logger = KotlinLogging.logger {}

/**
 * Hệ thống phái khiển — Quản lý nhiệm vụ phái khiển võ tướng ở khách sạn
 *
 * Nguồn: Dispatch.lua
 * Tối đa 4 thanh tiến trình diễn ra đồng thời (MaxSliderCount=4)
 */
class DispatchSystem(
    private val dispatchRepository: DispatchRepository,
    private val petSystem: PetSystem,
    private val dispatchDefs: Map<Int, Map<Int, DispatchDef>>,
    private val dispatchBonusDefs: Map<Int, DispatchBonusDef>
) {
    companion object {
        /** Số lượng thanh tiến trình tối đa (Dispatch.lua MaxSilderCount=4) */
        const val MAX_SLIDER_COUNT = 4
        /** Thời lượng phái khiển mặc định (giây) — 30 phút */
        const val DEFAULT_DISPATCH_DURATION_SECONDS = 1800.0
        /** Giá trị của một giây trong định dạng OLE Automation Date */
        private const val OA_SECOND = 1.0 / 86400.0
    }

    /**
     * Truy vấn tất cả các nhiệm vụ phái khiển đang diễn ra của nhân vật
     */
    suspend fun queryDispatches(characterId: Long): List<DispatchSaveData> {
        return dispatchRepository.findByCharacterId(characterId)
    }

    /**
     * Bắt đầu phái khiển võ tướng
     *
     * @param characterId ID nhân vật
     * @param innPetIndex Chỉ số (index) võ tướng trong khách sạn
     * @param currentTime Thời gian hiện tại của máy chủ (Định dạng OLE Automation Date)
     * @param maxSlots Số lượng ô phái khiển tối đa
     * @param effectIndex1 Chỉ số hiệu ứng 1
     * @param effectIndex2 Chỉ số hiệu ứng 2
     * @return Pair<Mã kết quả, Dữ liệu phái khiển mới tạo (khi thành công)>
     */
    suspend fun startDispatch(
        characterId: Long,
        innPetIndex: Int,
        currentTime: Double,
        maxSlots: Int = MAX_SLIDER_COUNT,
        effectIndex1: Int = 0,
        effectIndex2: Int = 0
    ): Pair<Int, DispatchSaveData?> {
        // Truy vấn các phái khiển đang diễn ra hiện tại
        val currentDispatches = dispatchRepository.findByCharacterId(characterId)

        // Xác thực: Danh sách (hàng đợi) đã đầy chưa
        val effectiveMax = maxSlots.coerceAtMost(MAX_SLIDER_COUNT)
        if (currentDispatches.size >= effectiveMax) {
            return DispatchStartResult.SLOT_FULL to null
        }

        // Xác thực: Võ tướng trong khách sạn có tồn tại không
        val innNpc = petSystem.getInnNpc(characterId, innPetIndex)
        if (innNpc == null) {
            return DispatchStartResult.INVALID_NPC_INDEX to null
        }

        // Xác thực: Võ tướng đã được phái khiển chưa (cùng một innPetIndex đang được sử dụng)
        if (currentDispatches.any { it.innPetIndex == innPetIndex }) {
            return DispatchStartResult.NPC_BUSY to null
        }

        // Tìm ô (slot) trống đầu tiên (hệ cơ số 1 / 1-based)
        val usedSlots = currentDispatches.map { it.slotIndex }.toSet()
        val emptySlot = (1..MAX_SLIDER_COUNT).firstOrNull { it !in usedSlots }
        if (emptySlot == null) {
            return DispatchStartResult.SLOT_FULL to null
        }

        // Tính toán thời gian phái khiển (mặc định 30 phút)
        val durationOa = DEFAULT_DISPATCH_DURATION_SECONDS * OA_SECOND
        val endTime = currentTime + durationOa

        // Loại phái khiển tạm thời tương ứng với slot (sau này có thể truyền loại được chọn từ giao diện UI)
        val dispatchKind = emptySlot

        val saveData = DispatchSaveData(
            slotIndex = emptySlot,
            startTime = currentTime,
            endTime = endTime,
            dispatchKind = dispatchKind,
            innPetIndex = innPetIndex,
            effectIndex1 = effectIndex1,
            effectIndex2 = effectIndex2
        )

        // Ghi vào DB
        dispatchRepository.insert(characterId, saveData)

        logger.info { "Bắt đầu phái khiển: charId=$characterId slot=$emptySlot innPet=$innPetIndex kind=$dispatchKind" }

        return DispatchStartResult.SUCCESS to saveData
    }

    /**
     * Hoàn thành phái khiển (Nhận phần thưởng)
     *
     * @param characterId ID nhân vật
     * @param slotIndex Chỉ số thanh tiến trình
     * @param currentTime Thời gian hiện tại của máy chủ (Định dạng OLE Automation Date)
     * @return Pair<Mã kết quả, Dữ liệu phái khiển đã hoàn thành (khi thành công)>
     */
    suspend fun completeDispatch(
        characterId: Long,
        slotIndex: Int,
        currentTime: Double
    ): Pair<Int, DispatchSaveData?> {
        val dispatches = dispatchRepository.findByCharacterId(characterId)
        val dispatch = dispatches.find { it.slotIndex == slotIndex }

        // Xác thực: Slot này có phái khiển đang diễn ra hay không
        if (dispatch == null) {
            return DispatchCompleteResult.INVALID_KIND to null
        }

        // Xác thực: Đã đến thời gian hoàn thành chưa
        if (currentTime < dispatch.endTime) {
            return DispatchCompleteResult.NOT_FINISHED to null
        }

        // Xóa bản ghi trong DB
        dispatchRepository.deleteBySlot(characterId, slotIndex)

        logger.info { "Hoàn thành phái khiển: charId=$characterId slot=$slotIndex kind=${dispatch.dispatchKind}" }

        return DispatchCompleteResult.SUCCESS to dispatch
    }

    /**
     * Tạo ngẫu nhiên chỉ số hiệu ứng phái khiển (Chọn 2 cái từ DispatchBonusDefs)
     */
    fun randomEffectIndices(): Pair<Int, Int> {
        if (dispatchBonusDefs.isEmpty()) return 0 to 0
        val keys = dispatchBonusDefs.keys.toList()
        val idx1 = if (keys.isNotEmpty()) keys.random() else 0
        val idx2 = if (keys.size > 1) keys.filter { it != idx1 }.randomOrNull() ?: 0 else 0
        return idx1 to idx2
    }

    /**
     * Lấy định nghĩa phần thưởng phái khiển
     *
     * @param dispatchKind Loại phái khiển
     * @param npcLevel Cấp độ võ tướng
     * @return Định nghĩa phần thưởng phù hợp với cấp độ nhất
     */
    fun getAwardDef(dispatchKind: Int, npcLevel: Int): DispatchDef? {
        val kindDefs = dispatchDefs[1]?.get(dispatchKind) ?: return null
        // Không áp dụng được, vì subKind của dispatchDefs là chỉ số mốc cấp độ
        // Nhưng DispatchDef hiện tại là Map<mainKind, Map<subKind, DispatchDef>>
        // Rút gọn ở đây: Trả về trực tiếp bản ghi đầu tiên của loại này
        return kindDefs
    }
}