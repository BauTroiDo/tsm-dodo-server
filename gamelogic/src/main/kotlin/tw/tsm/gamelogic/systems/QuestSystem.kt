package tw.tsm.gamelogic.systems

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.data.DecMissionResult
import tw.tsm.domain.data.MarkDef
import tw.tsm.domain.interfaces.repositories.MissionFlagRecord
import tw.tsm.domain.interfaces.repositories.MissionRecord
import tw.tsm.domain.interfaces.repositories.QuestRepository

private val logger = KotlinLogging.logger {}

/**
 * Hệ thống nhiệm vụ — Tương ứng với Client MarkManager.lua
 *
 * Chịu trách nhiệm tăng giảm bước nhiệm vụ, xóa, đánh dấu hoàn thành và khôi phục dữ liệu khi đăng nhập.
 *
 * Vòng đời nhiệm vụ:
 * 1. Sự kiện NPC resultClass=2, parameterStyle=1 → [addMissionStep] → Bước +delta
 * 2. Sự kiện NPC resultClass=2, parameterStyle=2 → [decMissionStep] → Bước -delta
 * 3. Bước về 0 → Tự động [removeMission] + [setFlag] → Hoàn thành nhiệm vụ
 * 4. Khi đăng nhập → [getMissions] + [getFlags] → Gửi S:024-006/007 để khôi phục trạng thái
 *
 * @param repo      Tầng truy cập dữ liệu nhiệm vụ
 * @param markDefs  Định nghĩa nhiệm vụ Mark_C.dat (dùng để tra cứu bitId)
 */
class QuestSystem(
    private val repo: QuestRepository,
    private val markDefs: Map<Int, MarkDef>
) {

    /**
     * Thêm bước nhiệm vụ (hoặc tạo nhiệm vụ mới)
     *
     * Tương ứng với Client MarkManager.AddMissionStep():
     * Nhiệm vụ đã tồn tại → Cộng dồn bước; Chưa tồn tại → Dùng delta làm bước khởi tạo để tạo nhiệm vụ mới.
     *
     * @param charId    ID nhân vật
     * @param missionId ID nhiệm vụ
     * @param delta     Lượng bước tăng thêm
     * @return Giá trị bước trước khi thay đổi (0 nghĩa là nhiệm vụ mới)
     */
    suspend fun addMissionStep(charId: Long, missionId: Int, delta: Int): Int {
        val current = repo.getMissions(charId).firstOrNull { it.missionId == missionId }?.step ?: 0
        val newStep = current + delta
        repo.upsertMission(charId, missionId, newStep)
        logger.info { "Thêm bước nhiệm vụ: charId=$charId missionId=$missionId step: $current → $newStep" }
        return current
    }

    /**
     * Giảm bước nhiệm vụ, phát hiện hoàn thành
     *
     * Tương ứng với Client MarkManager.DecMissionStep():
     * Giá trị bước nhỏ nhất là 0; Khi bước về 0 sẽ tự động xóa nhiệm vụ và thiết lập đánh dấu hoàn thành (khi bitId != 0).
     *
     * @param charId    ID nhân vật
     * @param missionId ID nhiệm vụ
     * @param delta     Lượng bước giảm đi
     * @return Kết quả giảm (bao gồm việc đã hoàn thành chưa, bitId)
     */
    suspend fun decMissionStep(charId: Long, missionId: Int, delta: Int): DecMissionResult {
        val current = repo.getMissions(charId).firstOrNull { it.missionId == missionId }?.step ?: 0

        // Nhiệm vụ không tồn tại (current == 0) → Không kích hoạt hoàn thành
        if (current == 0) {
            logger.warn { "Giảm bước của nhiệm vụ không tồn tại: charId=$charId missionId=$missionId" }
            return DecMissionResult(completed = false, bitId = 0)
        }

        val newStep = maxOf(0, current - delta)

        if (newStep == 0) {
            // Bước về 0 → Hoàn thành nhiệm vụ (đối chiếu MarkManager.DecMissionStep dòng 225-227)
            repo.removeMission(charId, missionId)
            val bitId = markDefs[missionId]?.bitId ?: 0
            if (bitId != 0) {
                repo.upsertFlag(charId, bitId, 1)
            }
            logger.info { "Hoàn thành nhiệm vụ: charId=$charId missionId=$missionId (step: $current → 0) bitId=$bitId" }
            return DecMissionResult(completed = true, bitId = bitId)
        } else {
            repo.upsertMission(charId, missionId, newStep)
            logger.info { "Giảm bước nhiệm vụ: charId=$charId missionId=$missionId step: $current → $newStep" }
            return DecMissionResult(completed = false, bitId = 0)
        }
    }

    /**
     * Hoàn thành nhiệm vụ (pStyle=3): Gỡ bỏ nhiệm vụ + Thiết lập cờ hoàn thành
     *
     * Logic giống với khi decMissionStep có bước về 0, nhưng gỡ bỏ trực tiếp mà không qua quá trình giảm bước.
     * Tương ứng với kết quả sự kiện Eve.emg resultClass=2, parameterStyle=3.
     *
     * @param charId    ID nhân vật
     * @param missionId ID nhiệm vụ
     * @return Kết quả hoàn thành (gồm bitId, dùng để gửi S:024-005)
     */
    suspend fun completeMission(charId: Long, missionId: Int): DecMissionResult {
        repo.removeMission(charId, missionId)
        val bitId = markDefs[missionId]?.bitId ?: 0
        if (bitId != 0) {
            repo.upsertFlag(charId, bitId, 1)
        }
        logger.info { "Hoàn thành nhiệm vụ (pStyle=3): charId=$charId missionId=$missionId bitId=$bitId" }
        return DecMissionResult(completed = true, bitId = bitId)
    }

    /**
     * Xóa nhiệm vụ đang tiến hành
     *
     * @param charId    ID nhân vật
     * @param missionId ID nhiệm vụ
     */
    suspend fun removeMission(charId: Long, missionId: Int) {
        repo.removeMission(charId, missionId)
        logger.info { "Xóa nhiệm vụ: charId=$charId missionId=$missionId" }
    }

    /**
     * Thiết lập trực tiếp bước nhiệm vụ (Dùng cho lệnh GM)
     *
     * Không đi qua logic tăng giảm, ghi đè trực tiếp giá trị bước của nhiệm vụ chỉ định.
     * Khi step=0 sẽ xóa bản ghi nhiệm vụ.
     *
     * @param charId    ID nhân vật
     * @param missionId ID nhiệm vụ
     * @param step      Giá trị bước mục tiêu
     */
    suspend fun setMissionStep(charId: Long, missionId: Int, step: Int) {
        if (step <= 0) {
            repo.removeMission(charId, missionId)
            logger.info { "Xóa bước nhiệm vụ (GM): charId=$charId missionId=$missionId" }
        } else {
            repo.upsertMission(charId, missionId, step)
            logger.info { "Thiết lập bước nhiệm vụ (GM): charId=$charId missionId=$missionId step=$step" }
        }
    }

    /**
     * Thiết lập đánh dấu hoàn thành nhiệm vụ
     *
     * @param charId    ID nhân vật
     * @param bitIndex  Chỉ số bit đánh dấu (bitId trong Mark_C.dat)
     * @param bitValue  Giá trị đánh dấu (thường là 1)
     */
    suspend fun setFlag(charId: Long, bitIndex: Int, bitValue: Int) {
        repo.upsertFlag(charId, bitIndex, bitValue)
        logger.debug { "Thiết lập đánh dấu nhiệm vụ: charId=$charId bitIndex=$bitIndex bitValue=$bitValue" }
    }

    /** Kiểm tra nhiệm vụ đã hoàn thành theo missionId, giống MarkManager.GetMissionFlag(). */
    suspend fun hasMissionFlag(charId: Long, missionId: Int): Boolean {
        val bitId = markDefs[missionId]?.bitId ?: return false
        if (bitId == 0) return false
        return repo.getMissionFlags(charId).any { it.bitIndex == bitId && it.bitValue != 0 }
    }

    /**
     * Lấy tất cả nhiệm vụ đang tiến hành của nhân vật (dùng để khởi tạo khi đăng nhập)
     *
     * @param charId ID nhân vật
     * @return Danh sách nhiệm vụ
     */
    suspend fun getMissions(charId: Long): List<MissionRecord> = repo.getMissions(charId)

    /**
     * Lấy tất cả đánh dấu nhiệm vụ đã hoàn thành của nhân vật (dùng để khởi tạo khi đăng nhập)
     *
     * @param charId ID nhân vật
     * @return Danh sách đánh dấu
     */
    suspend fun getFlags(charId: Long): List<MissionFlagRecord> = repo.getMissionFlags(charId)
}