package tw.tsm.gamelogic.systems

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.data.EveCondition

private val logger = KotlinLogging.logger {}

/**
 * Ảnh chụp nhanh trạng thái sự kiện người chơi — mọi dữ liệu cần cho đánh giá điều kiện
 *
 * Được thiết kế làm object giá trị bất biến, do bên gọi (NpcEventHandler) dựng rồi truyền vào.
 * Bản thân bộ đánh giá không truy vấn bất kỳ hệ thống bên ngoài nào.
 *
 * @param missionSteps   ID nhiệm vụ → bước hiện tại (dùng cho điều kiện pStyle=1/2)
 * @param missionFlags   ID cờ → giá trị (dùng cho điều kiện pStyle=3: bitId của nhiệm vụ đã hoàn thành)
 * @param bagItems       ID vật phẩm → số lượng sở hữu
 * @param storedItems    ID vật phẩm Tiền Trang/kho báu → số lượng sở hữu (dùng cho conditionClass=16)
 * @param bagUsedSlots   số ô đã dùng trong túi đồ (dùng cho conditionClass=7 param=2)
 * @param bagCapacity    dung lượng túi đồ hiện tại (bao gồm ô mở rộng RoleCount)
 * @param equipItems     tập hợp ID vật phẩm đã trang bị
 * @param sex            giới tính nhân vật (dùng cho conditionClass=7 param=5, tương ứng pStyle=1/2)
 * @param level          cấp độ nhân vật
 * @param serverId       Server ID đã chọn khi đăng nhập (conditionClass=7 param=8)
 * @param realPoint      Point thực khả dụng (Role.GetPoint, conditionClass=7 param=19)
 * @param rebornCount    số lần chuyển sinh
 * @param isMounted      có đang cưỡi vật cưỡi hay không (dùng cho conditionClass=7 param=16)
 * @param lastSurfaceId  ID Surface hiển thị cuối cùng (dùng để so sánh conditionClass=10)
 * @param lastChoiceCode mã lựa chọn cuối cùng (dùng để so sánh conditionClass=10)
 * @param battleResult   kết quả chiến đấu (0=không 1=thắng 2=thua 3=chạy, dùng để so sánh conditionClass=8)
 * @param completedEveCounts số sự kiện đã hoàn thành của cảnh này (eveNo → số lần hoàn thành, dùng để so sánh conditionClass=12)
 * @param currentEveNo  eveNo đang được đánh giá (dùng cho conditionClass=12 param=0)
 * @param followNpcIds   tập hợp NPC ID tướng mang theo (dùng để kiểm tra sở hữu tướng conditionClass=9)
 * @param innNpcIds      tập hợp NPC ID tướng lữ quán (dùng cho conditionClass=7 param=6 pStyle=3)
 * @param cartNpcIds     tập hợp NPC ID tướng xe ngựa (dùng cho conditionClass=7 param=6 pStyle=2)
 * @param roleCountValues số đếm thống kê nhân vật RoleCount (dùng cho một phần chỉ mục conditionClass=18)
 * @param skillLevels    ID kỹ năng → cấp kỹ năng (dùng để so sánh conditionClass=4)
 * @param gold           tiền vàng trên người (dùng cho điều kiện tiền conditionClass=5)
 * @param npcSecondReincarnationStatus mã trạng thái lối vào chuyển sinh lần 2 của tướng (0=có thể mở, -1=không có tướng đủ điều kiện)
 */
data class PlayerEventState(
    val missionSteps: Map<Int, Int> = emptyMap(),
    val missionFlags: Map<Int, Int> = emptyMap(),
    val bagItems: Map<Int, Int> = emptyMap(),
    val storedItems: Map<Int, Int> = emptyMap(),
    val bagUsedSlots: Int = 0,
    val bagCapacity: Int = 0,
    val equipItems: Set<Int> = emptySet(),
    val sex: Int = 0,
    val level: Int = 0,
    val serverId: Int = -1,
    val realPoint: Int = -1,
    val rebornCount: Int = 0,
    val isMounted: Boolean = false,
    val lastSurfaceId: Int = -1,
    val lastChoiceCode: Int = -1,
    val battleResult: Int = 0,
    val completedEveCounts: Map<Int, Int> = emptyMap(),
    val currentEveNo: Int = 0,
    val followNpcIds: Set<Int> = emptySet(),
    val followPetCount: Int = 0,
    val maxFollowPets: Int = 4,
    val innNpcIds: Set<Int> = emptySet(),
    val cartNpcIds: Set<Int> = emptySet(),
    val roleCountValues: Map<Int, Int> = emptyMap(),
    val skillLevels: Map<Int, Int> = emptyMap(),
    val gold: Int = 0,
    val npcSecondReincarnationStatus: Int = -1,
)

/**
 * Bộ đánh giá điều kiện Eve — hàm thuần, không giữ bất kỳ trạng thái nào
 *
 * Nguồn gốc: client không đánh giá điều kiện (toàn bộ do server quyết định),
 * Ngữ nghĩa điều kiện được suy ra từ cách đặt tên trường của Eve_NpcEventData.lua + hành vi trò chơi.
 *
 * Đã xác minh conditionClass: 0, 1, 2, 3(vật thể cảnh), 4, 5(tiền), 7, 8, 9(sở hữu tướng), 10, 11(thu kinh), 12, 15(lối vào chuyển sinh người chơi/tướng), 18(một phần RoleCount)
 * Chưa cài đặt (bảo thủ trả false): 6, 13, 14, 17
 */
object EveConditionEvaluator {

    /**
     * Đánh giá một điều kiện đơn lẻ có thỏa mãn không
     *
     * @param cond  dữ liệu điều kiện Eve.emg
     * @param state ảnh chụp nhanh trạng thái hiện tại của người chơi
     * @return true = điều kiện thỏa mãn
     */
    fun evaluate(cond: EveCondition, state: PlayerEventState): Boolean {
        return when (cond.conditionClass) {
            0 -> true  // Không điều kiện, luôn khớp

            1 -> {
                // Kiểm tra sở hữu vật phẩm túi đồ: param=itemId
                // ops=2, val=N → qty > N (có vật phẩm, ví dụ val=0 → qty > 0, val=2 → qty > 2)
                // ops=1, val=N → qty < N (thiếu vật phẩm, ví dụ val=1 → qty < 1 tức qty == 0)
                // ops=0, val=N → qty == N
                // ops=5, val=0 → qty == 0 (không có vật phẩm)
                val qty = state.bagItems[cond.conditionParameter] ?: 0
                val result = when (cond.conditionOps) {
                    0 -> qty == cond.conditionValue
                    1 -> qty < cond.conditionValue
                    2 -> qty > cond.conditionValue
                    5 -> if (cond.conditionValue == 0) qty == 0 else qty >= cond.conditionValue
                    6 -> qty != cond.conditionValue
                    else -> compareStep(qty, cond.conditionOps, cond.conditionValue)
                }
                logger.debug { "class=1 itemId=${cond.conditionParameter} qty=$qty ops=${cond.conditionOps} val=${cond.conditionValue} → $result" }
                result
            }

            2 -> {
                // Điều kiện nhiệm vụ: param=missionId, ops=so sánh, val=giá trị mục tiêu
                // pStyle=3 → đồng thời kiểm tra bước nhiệm vụ và cờ, lấy giá trị lớn hơn
                //   Nhiệm vụ đang tiến hành: step=1, flag=0 → actual=1
                //   Nhiệm vụ đã hoàn thành: step=0, flag=1 → actual=1
                //   Nhiệm vụ chưa bắt đầu: step=0, flag=0 → actual=0
                // pStyle=1/2 → chỉ kiểm tra bước nhiệm vụ
                val step = state.missionSteps[cond.conditionParameter] ?: 0
                val actual = if (cond.conditionParameterStyle == 3) {
                    val flag = state.missionFlags[cond.conditionParameter] ?: 0
                    maxOf(step, flag)
                } else {
                    step
                }
                val checkType = if (cond.conditionParameterStyle == 3) "step|flag" else "step"
                val result = compareStep(actual, cond.conditionOps, cond.conditionValue)
                logger.debug { "class=2 missionId=${cond.conditionParameter} $checkType=$actual ops=${cond.conditionOps} val=${cond.conditionValue} → $result" }
                result
            }

            3 -> {
                // Trạng thái có thể tương tác của vật thể cảnh: mẫu Eve cố định là pStyle=4/ops=5/value=0/subItem=0.
                val result = cond.conditionParameter > 0 &&
                    cond.conditionParameterStyle == 4 &&
                    cond.conditionOps == 5 &&
                    cond.conditionValue == 0 &&
                    cond.conditionSubItem == 0
                if (!result) {
                    logger.warn {
                        "conditionClass=3 điều kiện vật thể cảnh giới không xác định param=${cond.conditionParameter} " +
                            "pStyle=${cond.conditionParameterStyle} ops=${cond.conditionOps} " +
                            "val=${cond.conditionValue} sub=${cond.conditionSubItem} → false"
                    }
                }
                result
            }

            4 -> {
                val skillLevel = state.skillLevels[cond.conditionParameter] ?: 0
                val result = compareStep(skillLevel, cond.conditionOps, cond.conditionValue)
                logger.debug { "class=4 skillId=${cond.conditionParameter} level=$skillLevel ops=${cond.conditionOps} val=${cond.conditionValue} → $result" }
                result
            }

            5 -> {
                // Điều kiện tiền Eve: mẫu phổ biến là ops=2, value=số tiền-1, biểu thị tiền vàng đạt ngưỡng khấu trừ.
                val result = evaluateMoneyCondition(state.gold, cond.conditionOps, cond.conditionValue)
                logger.debug { "class=5 gold=${state.gold} ops=${cond.conditionOps} val=${cond.conditionValue} → $result" }
                result
            }

            7 -> {
                // Thuộc tính chung: param quyết định kiểu con
                val actual: Int? = when (cond.conditionParameter) {
                    0 -> state.level               // Cấp độ
                    2 -> {
                        // Nhánh không gian túi đồ: pStyle=1 túi đồ đã đầy; pStyle=2 túi đồ còn chỗ; pStyle=4 so sánh số ô còn lại.
                        evaluateBagSpaceCondition(cond, state)
                    }
                    3 -> {
                        // Kiểm tra vật phẩm trang bị: val=itemId, đã trang bị trả về itemId, không trả về 0
                        if (cond.conditionValue in state.equipItems) cond.conditionValue else 0
                    }
                    5 -> {
                        // Nhánh không gian tướng: pStyle=1 tướng mang theo đã đầy (>=4); pStyle=2 tướng mang theo còn chỗ trống (<4); pStyle=4 số ô tướng còn lại
                        evaluatePetSpaceCondition(cond, state)
                    }
                    6 -> {
                        // Kiểm tra vị trí lưu tướng: pStyle=1 mang theo / 2 xe ngựa / 3 lữ quán, val=npcId
                        val npcId = cond.conditionValue
                        val npcSet = when (cond.conditionParameterStyle) {
                            1 -> state.followNpcIds
                            2 -> state.cartNpcIds
                            3 -> state.innNpcIds
                            else -> emptySet()
                        }
                        if (npcId in npcSet) npcId else 0
                    }
                    8 -> {
                        // DVT Server ID condition.
                        // Evidence:
                        // - Login packet stores Network.serverId / session.serverId.
                        // - Migrant.lua defines galaxy servers 90..99 (serverId > 100 => %100).
                        // - All 468 Eve.emg cls=7,param=8 samples use pStyle=1/subItem=0 and
                        //   values 1,2,96..104,196..199; arena scenes 54901..54904 are
                        //   exactly Migrant.IsArenaScene().
                        if (
                            cond.conditionParameterStyle == 1 &&
                            cond.conditionSubItem == 0 &&
                            state.serverId > 0
                        ) state.serverId else null
                    }
                    19 -> {
                        // DVT Point balance condition (Role.GetPoint / ECurrency.Gold).
                        // Toàn bộ Eve.emg chỉ có 6 bản ghi, tất cả ở sòng bạc chính 12180: pStyle=2, subItem=0,
                        // ops=5, ngưỡng 9/45/90 hoặc 10/50/100; cùng chain với cls=7 pStyle=3
                        // Mua trực tiếp 31171 x1/5/10, meanNo chính là giá Point tương ứng.
                        if (
                            cond.conditionParameterStyle == 2 &&
                            cond.conditionSubItem == 0 &&
                            state.realPoint >= 0
                        ) state.realPoint else null
                    }
                    16 -> {
                        // Trạng thái vật cưỡi: mẫu Eve khi subItem=1 cố định sẽ nối hội thoại "mời xuống ngựa".
                        if (
                            cond.conditionParameterStyle == 1 &&
                            cond.conditionOps == 0 &&
                            cond.conditionValue == 0 &&
                            cond.conditionSubItem == 1 &&
                            state.isMounted
                        ) 0 else null
                    }
                    else -> null
                }
                if (actual == null) {
                    logger.warn {
                        "conditionClass=7 chưa triển khai param=${cond.conditionParameter} " +
                            "pStyle=${cond.conditionParameterStyle} ops=${cond.conditionOps} val=${cond.conditionValue} → false"
                    }
                    return false
                }
                // class=7 dùng so sánh thuận: actual [ops] conditionValue.
                val result = compareStep(actual, cond.conditionOps, cond.conditionValue)
                logger.debug { "class=7 param=${cond.conditionParameter} actual=$actual ops=${cond.conditionOps} val=${cond.conditionValue} → $result" }
                result
            }

            8 -> {
                // Kết quả chiến đấu: pStyle=1 thắng / 2 thua / 3 bỏ chạy
                val result = cond.conditionParameterStyle == state.battleResult
                logger.debug { "class=8 pStyle=${cond.conditionParameterStyle} battleResult=${state.battleResult} → $result" }
                result
            }

            9 -> {
                // Kiểm tra sở hữu tướng: conditionValue=npcId
                // Khuôn mẫu giống class=7 param=3 (kiểm tra trang bị): có → trả về npcId, không → trả về 0
                // Ví dụ: cls=9 v=22081 ops=5 → có tướng: 22081>=22081=true / không: 0>=22081=false
                if (cond.conditionParameter != 0) {
                    logger.warn {
                        "conditionClass=9 chưa triển khai điều kiện thăng cấp tướng param=${cond.conditionParameter} " +
                            "pStyle=${cond.conditionParameterStyle} ops=${cond.conditionOps} val=${cond.conditionValue} → false"
                    }
                    return false
                }
                val npcId = cond.conditionValue
                val actual = if (npcId in state.followNpcIds) npcId else 0
                val result = compareStep(actual, cond.conditionOps, npcId)
                logger.debug { "class=9 npcId=$npcId hasPet=${npcId in state.followNpcIds} ops=${cond.conditionOps} → $result" }
                result
            }

            10 -> {
                // Chọn lựa chọn hội thoại: param=surfaceId, pStyle=choiceCode
                val result = state.lastSurfaceId == cond.conditionParameter &&
                             state.lastChoiceCode == cond.conditionParameterStyle
                logger.debug { "class=10 surfaceId=${cond.conditionParameter} code=${cond.conditionParameterStyle} lastSurface=${state.lastSurfaceId} lastCode=${state.lastChoiceCode} → $result" }
                result
            }

            11 -> {
                // Xác nhận luồng bà thu kinh: mẫu Eve cố định là param=0/pStyle=1/ops=0/value=0.
                val result = cond.conditionParameter == 0 &&
                    cond.conditionParameterStyle == 1 &&
                    cond.conditionOps == 0 &&
                    cond.conditionValue == 0 &&
                    cond.conditionSubItem == 0
                if (!result) {
                    logger.warn {
                        "conditionClass=11 điều kiện thu kinh không xác định param=${cond.conditionParameter} " +
                            "pStyle=${cond.conditionParameterStyle} ops=${cond.conditionOps} " +
                            "val=${cond.conditionValue} sub=${cond.conditionSubItem} → false"
                    }
                }
                result
            }

            12 -> {
                // DVT Eve: param=0 biểu thị số lần eveNo hiện tại đã hoàn thành.
                // Các dạng param/pStyle khác vẫn chưa đủ bằng chứng Lua, không được dùng giá trị giả 0 để thả qua nhầm.
                if (cond.conditionParameter != 0) {
                    logger.warn {
                        "conditionClass=12 ngoài param=0 chưa triển khai ngữ nghĩa param=${cond.conditionParameter} " +
                            "pStyle=${cond.conditionParameterStyle} ops=${cond.conditionOps} " +
                            "val=${cond.conditionValue} → false"
                    }
                    return false
                }
                val value = state.completedEveCounts[state.currentEveNo] ?: 0
                val result = compareStep(value, cond.conditionOps, cond.conditionValue)
                logger.debug { "class=12 param=0 eveNo=${state.currentEveNo} count=$value ops=${cond.conditionOps} val=${cond.conditionValue} → $result" }
                result
            }

            14 -> {
                logger.warn { "conditionClass=14 ngữ nghĩa cần kiểm chứng param=${cond.conditionParameter} pStyle=${cond.conditionParameterStyle} ops=${cond.conditionOps} val=${cond.conditionValue} → false" }
                false
            }

            15 -> evaluateReincarnationCondition(cond, state)

            16 -> {
                if (cond.conditionParameterStyle != 1 || cond.conditionSubItem != 0) {
                    logger.warn {
                        "conditionClass=16 hình dạng không xác định param=${cond.conditionParameter} " +
                            "pStyle=${cond.conditionParameterStyle} ops=${cond.conditionOps} " +
                            "val=${cond.conditionValue} sub=${cond.conditionSubItem} → false"
                    }
                    return false
                }
                val qty = state.storedItems[cond.conditionParameter] ?: 0
                val result = compareStep(cond.conditionValue, cond.conditionOps, qty)
                logger.debug {
                    "class=16 storedItemId=${cond.conditionParameter} qty=$qty " +
                        "ops=${cond.conditionOps} val=${cond.conditionValue} → $result"
                }
                result
            }

            18 -> {
                if (!isKnownRoleCountIndex(cond.conditionParameter)) {
                    logger.warn { "conditionClass=18 chỉ số RoleCount không xác định param=${cond.conditionParameter} pStyle=${cond.conditionParameterStyle} ops=${cond.conditionOps} val=${cond.conditionValue} → false" }
                    false
                } else {
                    val actual = state.roleCountValues[cond.conditionParameter] ?: 0
                    val result = compareStep(actual, cond.conditionOps, cond.conditionValue)
                    logger.debug { "class=18 roleCount=${cond.conditionParameter} actual=$actual ops=${cond.conditionOps} val=${cond.conditionValue} → $result" }
                    result
                }
            }

            else -> {
                // conditionClass chưa cài đặt — mức WARN để dễ chẩn đoán
                logger.warn { "Chưa triển khai conditionClass=${cond.conditionClass} param=${cond.conditionParameter} pStyle=${cond.conditionParameterStyle} ops=${cond.conditionOps} val=${cond.conditionValue} → false" }
                false
            }
        }
    }

    private fun isKnownRoleCountIndex(index: Int): Boolean =
        // 1=số đếm hội thoại Đào Viên Kết Nghĩa, 189=số đếm event bài tập hè (đầu ghi resultClass=14 đã xác định, xem docs/eve-gap-analysis.md)
        index in setOf(1, 100, 101, 102, 108, 109, 189, 192, 668, 822)

    private fun evaluateBagSpaceCondition(cond: EveCondition, state: PlayerEventState): Int? {
        if (state.bagCapacity <= 0) return null

        val freeSlots = (state.bagCapacity - state.bagUsedSlots).coerceAtLeast(0)
        return when (cond.conditionParameterStyle) {
            1 -> if (cond.conditionOps == 0 && cond.conditionValue == 0 && freeSlots == 0) 0 else null
            2 -> if (cond.conditionOps == 0 && cond.conditionValue == 0 && freeSlots > 0) 0 else null
            4 -> freeSlots
            else -> null
        }
    }

    private fun evaluatePetSpaceCondition(cond: EveCondition, state: PlayerEventState): Int? {
        val maxPets = if (state.maxFollowPets > 0) state.maxFollowPets else PetSystem.MAX_FOLLOW
        val currentPets = if (state.followPetCount > 0) state.followPetCount else state.followNpcIds.size
        val freeSlots = (maxPets - currentPets).coerceAtLeast(0)
        return when (cond.conditionParameterStyle) {
            1 -> if (cond.conditionOps == 0 && cond.conditionValue == 0 && freeSlots == 0) 0 else null
            2 -> if (cond.conditionOps == 0 && cond.conditionValue == 0 && freeSlots > 0) 0 else null
            4 -> freeSlots
            else -> null
        }
    }

    private fun evaluateMoneyCondition(gold: Int, ops: Int, value: Int): Boolean =
        when (ops) {
            1 -> gold == value
            2 -> gold > value
            4 -> gold >= value
            5 -> gold <= value
            6 -> gold != value
            else -> compareStep(gold, ops, value)
        }

    private fun evaluateReincarnationCondition(cond: EveCondition, state: PlayerEventState): Boolean {
        if (cond.conditionParameterStyle != 0 || cond.conditionSubItem != 0) {
            logger.warn {
                "conditionClass=15 điều kiện chuyển sinh chưa được xác nhận param=${cond.conditionParameter} " +
                    "pStyle=${cond.conditionParameterStyle} ops=${cond.conditionOps} " +
                    "val=${cond.conditionValue} sub=${cond.conditionSubItem} → false"
            }
            return false
        }

        val actual = when (cond.conditionParameter) {
            0 -> state.rebornCount
            1 -> state.npcSecondReincarnationStatus
            else -> null
        }
        if (actual == null) {
            logger.warn {
                "conditionClass=15 chưa triển khai param=${cond.conditionParameter} " +
                    "ops=${cond.conditionOps} val=${cond.conditionValue} → false"
            }
            return false
        }

        val result = compareStep(actual, cond.conditionOps, cond.conditionValue)
        logger.debug { "class=15 param=${cond.conditionParameter} actual=$actual ops=${cond.conditionOps} val=${cond.conditionValue} → $result" }
        return result
    }

    /**
     * Phép toán so sánh
     *
     * DVT Lua DungeonCriteriaData.CompareResult：
     * 1→== / 2→< / 3→<= / 4→> / 5→>= / 6→!=。
     * Eve.emg còn có nhiều dạng đẳng thức ops=0, vì vậy giữ lại 0→== dành riêng cho Eve.
     */
    fun compareStep(actual: Int, ops: Int, expected: Int): Boolean = when (ops) {
        0 -> actual == expected
        1 -> actual == expected
        2 -> actual < expected
        3 -> actual <= expected
        4 -> actual > expected
        5 -> actual >= expected
        6 -> actual != expected
        else -> {
            logger.warn { "ops=$ops không xác định actual=$actual expected=$expected → false" }
            false
        }
    }
}
