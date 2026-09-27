package tw.tsm.gamelogic.systems

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.data.AchievementDef
import tw.tsm.domain.valueobjects.ThingData

private val logger = KotlinLogging.logger {}

/**
 * Hệ thống Thành tựu — Tương ứng với Achievement.lua + CheckCondition.lua ở client
 *
 * Chịu trách nhiệm xác thực hoàn thành thành tựu, logic nhận thưởng, tính toán điều kiện.
 * Trạng thái thành tựu được quản lý thông qua hệ thống BitFlag (completeFlag/getFlag), không cần thêm bảng DB.
 *
 * @param bitFlagSystem    Hệ thống BitFlag (Truy vấn/thiết lập trạng thái hoàn thành/nhận thưởng)
 * @param inventorySystem  Hệ thống túi đồ (Phát phần thưởng)
 * @param moneySystem      Hệ thống tiền tệ (Điều kiện Gold=1)
 * @param playerStateManager Thuộc tính người chơi (Điều kiện Lv/Int/Atk/Agi/Def/Hpx/Spx/Honor/Turn)
 * @param questSystem      Hệ thống nhiệm vụ (Điều kiện MissionFlag=15, MissionStep=16)
 * @param roleCountSystem  Đếm số lượng nhân vật (Điều kiện RoleCount=14, FriendCount=18)
 * @param achievementDefs  Bảng định nghĩa thành tựu (Được inject bởi GameDataManager)
 */
class AchievementSystem(
    private val bitFlagSystem: BitFlagSystem,
    private val inventorySystem: InventorySystem,
    private val moneySystem: MoneySystem,
    private val playerStateManager: PlayerStateManager,
    private val questSystem: QuestSystem,
    private val roleCountSystem: RoleCountSystem,
    private val achievementDefs: Map<Int, AchievementDef>,
) {

    /**
     * Hoàn thành thành tựu
     *
     * Tương ứng với xác thực server của client C:082-001.
     * Quy trình: Tra cứu định nghĩa → Kiểm tra xem đã hoàn thành chưa → Xác thực điều kiện → Thiết lập completeFlag.
     *
     * @param charId ID nhân vật
     * @param achievementId ID thành tựu
     * @return Mã kết quả (0=Thành công, 1=Không có dữ liệu, 2=Đã hoàn thành, 3=Không đủ điều kiện)
     */
    suspend fun completeAchievement(charId: Long, achievementId: Int): Int {
        val def = achievementDefs[achievementId]
        if (def == null) {
            logger.warn { "Thành tựu không tồn tại: achievementId=$achievementId" }
            return RESULT_NO_DATA
        }

        val flags = bitFlagSystem.getSetFlagIds(charId)
        if (def.completeFlag in flags) {
            return RESULT_ALREADY_COMPLETED
        }

        if (!evaluateCondition(charId, def, flags)) {
            return RESULT_CONDITION_FAILED
        }

        bitFlagSystem.setFlag(charId, def.completeFlag)
        logger.info { "Thành tựu hoàn thành: charId=$charId achievementId=$achievementId score=${def.score}" }
        return RESULT_SUCCESS
    }

    /**
     * Nhận thưởng thành tựu
     *
     * Tương ứng với client C:082-002.
     * Quy trình: Kiểm tra đã hoàn thành và chưa nhận thưởng → Phát vật phẩm → Thiết lập getFlag.
     *
     * @param charId ID nhân vật
     * @param achievementId ID thành tựu
     * @return (Mã kết quả, Danh sách vật phẩm được thêm vào). Mã kết quả: 0=Thành công, 1=Thất bại
     */
    suspend fun claimReward(charId: Long, achievementId: Int): Pair<Int, List<Pair<Int, ThingData>>> {
        val def = achievementDefs[achievementId]
        if (def == null) {
            logger.warn { "Nhận thưởng thất bại: Thành tựu không tồn tại achievementId=$achievementId" }
            return Pair(REWARD_FAIL, emptyList())
        }

        val flags = bitFlagSystem.getSetFlagIds(charId)
        if (def.completeFlag !in flags) {
            logger.warn { "Nhận thưởng thất bại: Thành tựu chưa hoàn thành charId=$charId achievementId=$achievementId" }
            return Pair(REWARD_FAIL, emptyList())
        }
        if (def.getFlag in flags) {
            logger.warn { "Nhận thưởng thất bại: Đã nhận thưởng rồi charId=$charId achievementId=$achievementId" }
            return Pair(REWARD_FAIL, emptyList())
        }

        // Phát vật phẩm thưởng
        var addedItems: List<Pair<Int, ThingData>> = emptyList()
        if (def.itemId > 0 && def.itemCount > 0) {
            addedItems = inventorySystem.addItemsToBag(charId, def.itemId, def.itemCount)
            if (addedItems.isEmpty()) {
                logger.warn { "Nhận thưởng thất bại: Túi đồ đầy charId=$charId itemId=${def.itemId}" }
                return Pair(REWARD_FAIL, emptyList())
            }
        }

        bitFlagSystem.setFlag(charId, def.getFlag)
        logger.info { "Nhận thưởng thành tựu: charId=$charId achievementId=$achievementId itemId=${def.itemId} x${def.itemCount}" }
        return Pair(REWARD_SUCCESS, addedItems)
    }

    /**
     * Tính tổng điểm thành tựu
     *
     * Duyệt qua tất cả các định nghĩa thành tựu, kiểm tra getFlag đã được thiết lập chưa (Đã nhận thưởng = Đã hoàn thành và được tính điểm).
     *
     * @param charId ID nhân vật
     * @return Tổng điểm
     */
    suspend fun calculateTotalScore(charId: Long): Int {
        val flags = bitFlagSystem.getSetFlagIds(charId)
        return achievementDefs.values
            .filter { it.getFlag in flags }
            .sumOf { it.score }
    }

    /**
     * Server chủ động quét tất cả các điều kiện thành tựu, trả về danh sách ID thành tựu mới hoàn thành
     *
     * Tương ứng với Achievement.CheckAllCompeleteAchievement() ở client.
     * Duyệt qua tất cả các định nghĩa thành tựu, tự động thiết lập completeFlag cho các thành tựu chưa hoàn thành nhưng đã đạt đủ điều kiện.
     *
     * @param charId ID nhân vật
     * @return Danh sách ID thành tựu mới hoàn thành trong lần này
     */
    suspend fun checkAndComplete(charId: Long): List<Int> {
        val flags = bitFlagSystem.getSetFlagIds(charId).toMutableSet()
        val completed = mutableListOf<Int>()
        for ((id, def) in achievementDefs) {
            if (def.completeFlag in flags) continue
            if (!evaluateCondition(charId, def, flags)) continue
            bitFlagSystem.setFlag(charId, def.completeFlag)
            flags.add(def.completeFlag)
            completed.add(id)
            logger.info { "Server chủ động hoàn thành thành tựu: charId=$charId achievementId=$id score=${def.score}" }
        }
        return completed
    }

    /**
     * Lấy định nghĩa thành tựu (Sử dụng bên ngoài như phát thanh)
     */
    fun getDef(achievementId: Int): AchievementDef? = achievementDefs[achievementId]

    // ── Xác thực điều kiện ──

    /**
     * Xác thực điều kiện thành tựu — Tương ứng với CheckCondition.lua
     *
     * @param charId ID nhân vật
     * @param def Định nghĩa thành tựu
     * @param flags Tập hợp BitFlag đã được thiết lập
     * @return true=Điều kiện được thông qua
     */
    private suspend fun evaluateCondition(
        charId: Long,
        def: AchievementDef,
        flags: Set<Int>
    ): Boolean {
        val currentValue = getConditionCurrentValue(charId, def, flags)
        return compareWithOperator(currentValue, def.conditionOpr, def.conditionValue)
    }

    /**
     * Lấy giá trị điều kiện hiện tại — Tương ứng với CheckCondition.CalculationConditionValue
     *
     * @return Giá trị điều kiện hiện tại
     */
    private suspend fun getConditionCurrentValue(
        charId: Long,
        def: AchievementDef,
        flags: Set<Int>
    ): Long {
        val state = playerStateManager.getState(charId)
        val kindValue = def.conditionKindValue

        return when (def.conditionKind) {
            COND_GOLD -> moneySystem.getGold(charId).toLong()
            COND_LV -> {
                // Cấp độ (Số lần chuyển sinh cần thiết == kindValue)
                val turn = state?.turn ?: 0
                if (turn.toLong() == kindValue) (state?.level ?: 0).toLong() else 0L
            }
            COND_ACHIEVEMENT_SCORE -> {
                // Tổng điểm thành tựu
                achievementDefs.values.filter { it.getFlag in flags }.sumOf { it.score }.toLong()
            }
            COND_INT -> (state?.intelligence ?: 0).toLong()
            COND_ATK -> (state?.atk ?: 0).toLong()
            COND_AGI -> (state?.agi ?: 0).toLong()
            COND_DEF -> (state?.def ?: 0).toLong()
            COND_HPX -> (state?.hpx ?: 0).toLong()
            COND_SPX -> (state?.spx ?: 0).toLong()
            COND_HONOR -> 0L // TODO: Hệ thống quan chức (EAttribute.Honor) chưa được thực hiện
            COND_ROLE_COUNT -> {
                // Bộ đếm nhân vật (kindValue = chỉ mục ERoleCount)
                roleCountSystem.getValue(charId, kindValue.toInt()).toLong()
            }
            COND_MISSION_FLAG -> {
                // BitFlag nhiệm vụ (kindValue = missionFlag ID)
                val missionFlags = questSystem.getFlags(charId)
                if (missionFlags.any { it.bitIndex == kindValue.toInt() && it.bitValue > 0 }) 1L else 0L
            }
            COND_MISSION_STEP -> {
                // Bước nhiệm vụ (kindValue = missionId)
                val missions = questSystem.getMissions(charId)
                missions.firstOrNull { it.missionId == kindValue.toInt() }?.step?.toLong() ?: 0L
            }
            COND_FLAG -> {
                // BitFlag (kindValue = flagId)
                if (kindValue.toInt() in flags) 1L else 0L
            }
            COND_FRIEND_COUNT -> {
                // Giá trị bạn bè tối đa trong lịch sử (Tồn tại ở chỉ mục RoleCount 192)
                roleCountSystem.getValue(charId, RoleCountSystem.FRIEND_COUNT).toLong()
            }
            COND_TURN -> {
                // Cấp độ tích lũy (turn * maxLv + level, yêu cầu turn >= kindValue)
                val turn = state?.turn ?: 0
                if (turn.toLong() >= kindValue) {
                    ((state?.level ?: 0) + turn * MAX_LV).toLong()
                } else 0L
            }
            COND_NOW_LEVEL -> {
                // Cấp độ hiện tại (không tính chuyển sinh)
                (state?.level ?: 0).toLong()
            }
            else -> {
                logger.warn { "Loại điều kiện thành tựu không được hỗ trợ: kind=${def.conditionKind}" }
                0L
            }
        }
    }

    /**
     * So sánh toán tử — Tương ứng với AchievementData.IsComplete()
     *
     * @param current Giá trị hiện tại
     * @param opr Toán tử (1=Bằng, 2=Lớn hơn, 3=Lớn hơn hoặc bằng, 4=Nhỏ hơn, 5=Nhỏ hơn hoặc bằng, 6=Không bằng)
     * @param target Giá trị mục tiêu
     * @return Kết quả so sánh
     */
    private fun compareWithOperator(current: Long, opr: Int, target: Long): Boolean {
        return when (opr) {
            OPR_EQ -> current == target
            OPR_GT -> current > target
            OPR_GE -> current >= target
            OPR_LT -> current < target
            OPR_LE -> current <= target
            OPR_NE -> current != target
            else -> false
        }
    }

    companion object {
        // ── Mã kết quả hoàn thành thành tựu ──
        const val RESULT_SUCCESS = 0
        const val RESULT_NO_DATA = 1
        const val RESULT_ALREADY_COMPLETED = 2
        const val RESULT_CONDITION_FAILED = 3

        // ── Mã kết quả nhận thưởng ──
        const val REWARD_SUCCESS = 0
        const val REWARD_FAIL = 1

        // ── Enum ECondition (Đối chiếu với CheckCondition.lua) ──
        private const val COND_GOLD = 1
        private const val COND_LV = 2
        private const val COND_ACHIEVEMENT_SCORE = 6
        private const val COND_INT = 7
        private const val COND_ATK = 8
        private const val COND_AGI = 9
        private const val COND_DEF = 10
        private const val COND_HPX = 11
        private const val COND_SPX = 12
        private const val COND_HONOR = 13
        private const val COND_ROLE_COUNT = 14
        private const val COND_MISSION_FLAG = 15
        private const val COND_MISSION_STEP = 16
        private const val COND_FLAG = 17
        private const val COND_FRIEND_COUNT = 18
        private const val COND_TURN = 19
        private const val COND_NOW_LEVEL = 20

        // ── Toán tử (Đối chiếu với AchievementData.IsComplete) ──
        private const val OPR_EQ = 1
        private const val OPR_GT = 2
        private const val OPR_GE = 3
        private const val OPR_LT = 4
        private const val OPR_LE = 5
        private const val OPR_NE = 6

        // ── Giới hạn cấp độ (Đối chiếu với Role.playerMaxLv) ──
        private const val MAX_LV = 100
    }
}