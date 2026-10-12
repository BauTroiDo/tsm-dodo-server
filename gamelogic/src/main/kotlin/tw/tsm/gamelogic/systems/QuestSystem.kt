package tw.tsm.gamelogic.systems

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap
import tw.tsm.domain.data.DecMissionResult
import tw.tsm.domain.data.MarkDef
import tw.tsm.domain.interfaces.repositories.DungeonPeriodResetResult
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
    private val markDefs: Map<Int, MarkDef>,
    dailyDungeonMissionIds: Set<Int> = emptySet(),
    private val today: () -> Int = DispatchSystem::currentDayKey,
    weeklyDungeonMissionIds: Set<Int> = emptySet(),
    weeklyClaimMissionIds: Set<Int> = emptySet(),
    private val thisWeek: () -> Int = { currentWeekKey() },
    // Original IOS_MarkStep_C is required to distinguish one-shot DVT
    // marker missions from genuine multi-step quests on login.
    private val markStepMissionIds: Set<Int> = emptySet(),
) {

    // Only normal kind1/2 dungeon dailyFlag counters proven kind8/bitId0 may be cleared.
    private val dailyDungeonIds = dailyDungeonMissionIds.filterTo(mutableSetOf()) {
        markDefs[it]?.let { mark -> mark.kind == 8 && mark.bitId == 0 } == true
    }

    // DVT current kind3/kind5 stage counters are Mark kind9/bitId0.
    // Claim marks are separately scoped from loaded DungeonData costPoint fields.
    private val weeklyDungeonIds = weeklyDungeonMissionIds.filterTo(mutableSetOf()) {
        markDefs[it]?.let { mark -> mark.kind == 9 && mark.bitId == 0 } == true
    }
    private val weeklyClaimBits = weeklyClaimMissionIds.mapNotNullTo(mutableSetOf()) {
        markDefs[it]?.let { mark ->
            if (mark.kind == 9 && mark.bitId > 0) mark.bitId else null
        }
    }

    private val checkedDays = ConcurrentHashMap<Long, Int>()
    private val checkedWeeks = ConcurrentHashMap<Long, Int>()

    suspend fun refreshDungeonDaily(charId: Long): List<Int> {
        if (dailyDungeonIds.isEmpty() || charId <= 0) return emptyList()
        return mutationLock(charId).withLock {
            val day = today()
            if ((checkedDays[charId] ?: 0) >= day) return@withLock emptyList()
            val removed = repo.refreshDungeonDay(charId, day, dailyDungeonIds)
            checkedDays[charId] = day
            removed
        }
    }

    suspend fun refreshDungeonWeekly(charId: Long): DungeonPeriodResetResult {
        if (charId <= 0 || (weeklyDungeonIds.isEmpty() && weeklyClaimBits.isEmpty())) {
            return DungeonPeriodResetResult()
        }
        return mutationLock(charId).withLock {
            val week = thisWeek()
            if ((checkedWeeks[charId] ?: 0) >= week) {
                return@withLock DungeonPeriodResetResult()
            }
            val reset = repo.refreshDungeonWeek(
                charId,
                week,
                weeklyDungeonIds,
                weeklyClaimBits,
            )
            checkedWeeks[charId] = week
            reset
        }
    }

    // Keep lock identity stable while suspended mutations may still be waiting.
    private val mutationLocks = ConcurrentHashMap<Long, Mutex>()
    private fun mutationLock(charId: Long) = mutationLocks.computeIfAbsent(charId) { Mutex() }

    /**
     * Thêm bước nhiệm vụ (hoặc tạo nhiệm vụ mới)
     *
     * Tương ứng với Client MarkManager.AddMissionStep():
     * Nhiệm vụ đã tồn tại → Cộng dồn bước; Chưa tồn tại → Dùng delta làm bước khởi tạo để tạo nhiệm vụ mới.
     *
     * @param charId    ID nhân vật
     * @param missionId ID nhiệm vụ
     * @param delta     Lượng bước tăng thêm
     * @return Giá trị bước trước khi thay đổi (0 nghĩa là nhiệm vụ mới), null nếu vượt giới hạn byte
     */
    suspend fun addMissionStep(charId: Long, missionId: Int, delta: Int): Int? {
        return mutationLock(charId).withLock {
            val current = repo.getMissions(charId).firstOrNull { it.missionId == missionId }?.step ?: 0
            if (missionId !in 1..65535 || delta !in 0..255 || current !in 0..255 || current.toLong() + delta > 255) {
                logger.warn { "Từ chối bước nhiệm vụ ngoài byte: charId=$charId missionId=$missionId current=$current delta=$delta" }
                return@withLock null
            }
            val newStep = current + delta
            // Verified DVT limits only: mission12280's missing 6..10 and
            // mission10120/10164's terminal stage2. Recheck under the
            // per-character mutation mutex, even if a battle replay races
            // the earlier whole-chain Eve preflight.
            if (DvtQuestStepBoundaryPolicy.isUnsupportedAddDestination(missionId, newStep)) {
                logger.warn {
                    "Chặn bước nhiệm vụ không xác định trong DVT: charId=$charId mission=$missionId " +
                        "current=$current delta=$delta target=$newStep"
                }
                return@withLock null
            }
            repo.upsertMission(charId, missionId, newStep)
            logger.info { "Thêm bước nhiệm vụ: charId=$charId missionId=$missionId step: $current → $newStep" }
            return@withLock current
        }
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
        return mutationLock(charId).withLock {
            require(delta in 0..255) { "Mission delta must fit DVT byte" }
            val current = repo.getMissions(charId).firstOrNull { it.missionId == missionId }?.step ?: 0

            // Nhiệm vụ không tồn tại (current == 0) → Không kích hoạt hoàn thành
            if (current == 0) {
                logger.warn { "Giảm bước của nhiệm vụ không tồn tại: charId=$charId missionId=$missionId" }
                return@withLock DecMissionResult(completed = false, bitId = 0)
            }

            val newStep = maxOf(0, current - delta)

            if (newStep == 0) {
                // Bước về 0 → Hoàn thành nhiệm vụ (đối chiếu MarkManager.DecMissionStep dòng 225-227)
                val bitId = markDefs[missionId]?.bitId ?: 0
                repo.finishMission(charId, missionId, bitId)
                logger.info { "Hoàn thành nhiệm vụ: charId=$charId missionId=$missionId (step: $current → 0) bitId=$bitId" }
                return@withLock DecMissionResult(completed = true, bitId = bitId)
            } else {
                repo.upsertMission(charId, missionId, newStep)
                logger.info { "Giảm bước nhiệm vụ: charId=$charId missionId=$missionId step: $current → $newStep" }
                return@withLock DecMissionResult(completed = false, bitId = 0)
            }
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
        return mutationLock(charId).withLock {
            val bitId = markDefs[missionId]?.bitId ?: 0
            repo.finishMission(charId, missionId, bitId)
            logger.info { "Hoàn thành nhiệm vụ (pStyle=3): charId=$charId missionId=$missionId bitId=$bitId" }
            return@withLock DecMissionResult(completed = true, bitId = bitId)
        }
    }

    /**
     * Xóa nhiệm vụ đang tiến hành
     *
     * @param charId    ID nhân vật
     * @param missionId ID nhiệm vụ
     */
    suspend fun removeMission(charId: Long, missionId: Int) {
        return mutationLock(charId).withLock {
            repo.removeMission(charId, missionId)
            logger.info { "Xóa nhiệm vụ: charId=$charId missionId=$missionId" }
        }
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
        return mutationLock(charId).withLock {
            require(step in 0..255 && missionId in 1..65535) { "Mission ID/step must fit DVT packet" }
            if (step <= 0) {
                repo.removeMission(charId, missionId)
                logger.info { "Xóa bước nhiệm vụ (GM): charId=$charId missionId=$missionId" }
            } else {
                repo.upsertMission(charId, missionId, step)
                logger.info { "Thiết lập bước nhiệm vụ (GM): charId=$charId missionId=$missionId step=$step" }
            }
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
        return mutationLock(charId).withLock {
            repo.upsertFlag(charId, bitIndex, bitValue)
            logger.debug { "Thiết lập đánh dấu nhiệm vụ: charId=$charId bitIndex=$bitIndex bitValue=$bitValue" }
        }
    }

    /** Pure preflight for a weekly all-pass claim Mark: no database mutation. */
    fun completionBitId(missionId: Int): Int = markDefs[missionId]?.bitId ?: 0

    /** Same Asia/Taipei Monday period key used by refreshDungeonWeekly. */
    fun dungeonWeekKey(): Int = thisWeek()

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
    /**
     * PC DVT LimitTimeDungeon_C: missionId controls random battles in MineData.
     * Initialize only when absent; never reset a character's selected stage.
     * Serialize with all other quest mutations for the same character.
     *
     * @return true only if this call actually created step 1.
     */
    suspend fun ensureMissionStarted(charId: Long, missionId: Int): Boolean =
        mutationLock(charId).withLock {
            require(missionId in 1..65535)
            if (repo.getMissions(charId).any { it.missionId == missionId && it.step > 0 }) {
                false
            } else {
                repo.upsertMission(charId, missionId, 1)
                logger.info { "Initial encounter mission: charId=$charId missionId=$missionId step=1" }
                true
            }
        }

    /**
     * DVT C097-002 changes the *selected monster tier*, stored in mission 13137.
     * Return the previous stage so the caller can send S024-001/002 as an
     * incremental delta (client MarkManager does not accept an absolute delta).
     * Serialized with NPC-driven quest changes and the initial step creation.
     */
    suspend fun selectTrainingMissionStage(charId: Long, missionId: Int, newStep: Int): Int =
        mutationLock(charId).withLock {
            require(missionId in 1..65535 && newStep in 1..255)
            val old = repo.getMissions(charId)
                .firstOrNull { it.missionId == missionId }?.step ?: 0
            require(old in 0..255) { "Existing mission step does not fit packet" }
            if (old != newStep) {
                repo.upsertMission(charId, missionId, newStep)
                logger.info { "Selected training mission stage: charId=$charId mission=$missionId $old -> $newStep" }
            }
            old
        }

    suspend fun getMissions(charId: Long): List<MissionRecord> = repo.getMissions(charId)

    /**
     * Lấy tất cả đánh dấu nhiệm vụ đã hoàn thành của nhân vật (dùng để khởi tạo khi đăng nhập)
     *
     * @param charId ID nhân vật
     * @return Danh sách đánh dấu
     */
    suspend fun getFlags(charId: Long): List<MissionFlagRecord> = repo.getMissionFlags(charId)

    /**
     * Repair legacy DVT marker ADD_STEP rows before the login S024-007 flags
     * snapshot. EveStateBuilder repairs these on event access, but login
     * sends its flags earlier, potentially leaving finished tutorial icons
     * locked on relog.
     *
     * Only an existing positive step for a source Mark with a positive bitId
     * and NO real MarkStep qualifies. Never invent a new mission or grant
     * a flag merely because an icon requires it.
     */
    suspend fun reconcileStoredMarkerMissionFlags(charId: Long): List<MissionFlagRecord> {
        if (charId <= 0L || markStepMissionIds.isEmpty()) return emptyList()
        return mutationLock(charId).withLock {
            val missions = repo.getMissions(charId)
            if (missions.isEmpty()) return@withLock emptyList()

            val activeBits = repo.getMissionFlags(charId)
                .filter { it.bitIndex > 0 && it.bitValue != 0 }
                .mapTo(mutableSetOf()) { it.bitIndex }
            val repaired = mutableListOf<MissionFlagRecord>()
            for (mission in missions) {
                if (mission.step <= 0 || mission.missionId in markStepMissionIds) continue
                val bitId = markDefs[mission.missionId]?.bitId ?: continue
                if (bitId !in 1..65535 || bitId in activeBits) continue

                repo.upsertFlag(charId, bitId, 1)
                activeBits += bitId
                repaired += MissionFlagRecord(bitId, 1)
                logger.info {
                    "Repaired DVT marker before login: charId=" + charId +
                        " mission=" + mission.missionId + " step=" + mission.step +
                        " bitId=" + bitId
                }
            }
            repaired
        }
    }

    companion object {
        private val GAME_ZONE: ZoneId = ZoneId.of("Asia/Taipei")

        /** Monday 00:00 calendar key used by DVT weekly dungeons. */
        fun currentWeekKey(): Int {
            val today = LocalDate.now(GAME_ZONE)
            val monday = today.minusDays((today.dayOfWeek.value - DayOfWeek.MONDAY.value).toLong())
            return monday.year * 10000 + monday.monthValue * 100 + monday.dayOfMonth
        }
    }
}
