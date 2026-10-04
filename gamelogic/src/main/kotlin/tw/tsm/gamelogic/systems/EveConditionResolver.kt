package tw.tsm.gamelogic.systems

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.data.EveCondition
import tw.tsm.domain.data.EveGroupData
import tw.tsm.domain.data.EveResult
import tw.tsm.domain.data.NpcEventData
import tw.tsm.domain.data.SceneEveData

private val logger = KotlinLogging.logger {}

/**
 * Kết quả phân tích điều kiện
 *
 * @property results            Danh sách kết quả sau khi lọc
 * @property matchedConditionNo conditionNo đầu tiên của chuỗi AND khớp (dùng để phát hiện vòng lặp auto-chain)
 */
data class ConditionResolveResult(
    val results: List<EveResult>,
    val matchedConditionNo: Int,
    val stepScore: Int
)

/**
 * Bộ phân tích điều kiện Eve (EveConditionResolver)
 *
 * Chức năng:
 * - Đánh giá chuỗi điều kiện sự kiện (resolveEvent) — Chỉ phân tích thuần túy, không tạo Session
 * - Áp dụng lọc xác suất GroupData (applyGroupData)
 *
 * Được tách ra từ EveStateBuilder.resolveAndActivate():
 * Phương thức cũ pha trộn hai trách nhiệm là "phân tích điều kiện" và "khởi động Session",
 * nay được tách thành resolveEvent() (phân tích thuần túy) + người gọi tự chạy stateMachine.activate().
 */
class EveConditionResolver(
    private val eveData: Map<Int, SceneEveData>
) {

    /**
     * Đánh giá chuỗi điều kiện sự kiện, trả về các results sau khi lọc
     *
     * Không khởi động Session, không sửa đổi bất kỳ trạng thái nào — Ngữ nghĩa hàm thuần túy.
     * Người gọi sau khi nhận kết quả sẽ tự quyết định xem có activate hay không.
     *
     * @param eventData Dữ liệu sự kiện
     * @param state     Bản ghi trạng thái của người chơi
     * @param mapId     ID Map / Cảnh (dùng để truy vấn GroupData)
     * @param chainFilter Bộ lọc chuỗi AND (ví dụ: nhánh conditionClass=10/8)
     * @return [ConditionResolveResult] (gồm results + matchedConditionNo), null = không khớp
     */
    fun resolveEvent(
        eventData: NpcEventData,
        state: PlayerEventState,
        mapId: Int,
        chainFilter: ((List<EveCondition>) -> Boolean)? = null,
        excludedConditionNos: Set<Int> = emptySet()
    ): ConditionResolveResult? {
        // Dump chẩn đoán: tất cả chuỗi điều kiện của sự kiện + kết quả
        logger.trace { "Dump sự kiện eveNo=${eventData.eveNo}: số điều kiện=${eventData.conditions.size} " +
                eventData.conditions.joinToString(" | ") { c ->
                    "[cond=${c.conditionNo} cls=${c.conditionClass} p=${c.conditionParameter} ps=${c.conditionParameterStyle} " +
                            "ops=${c.conditionOps} v=${c.conditionValue} and=${c.andNum} res=${c.results.size}" +
                            if (c.results.isNotEmpty()) c.results.joinToString(",", prefix = " ") { r ->
                                "(g${r.resultGroupNo}:n${r.resultNo}:t${r.resultType}:c${r.resultClass}:p${r.parameter}:s${r.parameterStyle}:v${r.resultValue}:m${r.resultMeanNo})"
                            } else "" + "]"
                }
        }

        // Temporary targeted trace for the Zhang Fei quest at map 12004.
        if (mapId == 12004 && eventData.eveNo in setOf(2, 4, 5)) {
            logger.info {
                "Quest diagnostic: map=$mapId eve=${eventData.eveNo} " +
                    eventData.conditions.joinToString(" | ") { c ->
                        "cond=${c.conditionNo}/class=${c.conditionClass}/param=${c.conditionParameter}/" +
                            "style=${c.conditionParameterStyle}/op=${c.conditionOps}/value=${c.conditionValue}/and=${c.andNum}/" +
                            "pass=${EveConditionEvaluator.evaluate(c, state.copy(currentEveNo = eventData.eveNo))}/results=${c.results.joinToString { r -> "class=${r.resultClass},param=${r.parameter},style=${r.parameterStyle},value=${r.resultValue},mean=${r.resultMeanNo}" }}"
                    }
            }
        }
        val stateWithEve = state.copy(currentEveNo = eventData.eveNo)
        val chainResult = EveChainResolver.resolve(
            conditions = eventData.conditions,
            evaluator = { cond -> EveConditionEvaluator.evaluate(cond, stateWithEve) },
            chainFilter = { chain ->
                (chainFilter?.invoke(chain) ?: true) &&
                        chain.first().conditionNo !in excludedConditionNos
            }
        ) ?: return null

        // Áp dụng lọc GroupData
        val groupDatas = eveData[mapId]?.groupDatas ?: emptyMap()
        val filtered = applyGroupData(chainResult.results, groupDatas)
        return ConditionResolveResult(filtered, chainResult.matchedConditionNo, chainResult.stepScore)
    }

    /**
     * Áp dụng GroupData để lọc danh sách kết quả
     *
     * - resultGroupNo == 0: Nhóm ban đầu, chắc chắn được giữ lại
     * - resultGroupNo > 0: Tra bảng GroupData, chọn một memberNo theo xác suất, chỉ giữ lại resultNo được chọn
     */
    fun applyGroupData(
        results: List<EveResult>,
        groupDatas: Map<Int, EveGroupData>
    ): List<EveResult> {
        if (results.all { it.resultGroupNo == 0 }) return results

        val groupSelections = mutableMapOf<Int, Int>()

        for (result in results) {
            val groupNo = result.resultGroupNo
            if (groupNo <= 0 || groupNo in groupSelections) continue

            val groupData = groupDatas[groupNo]
            if (groupData == null) {
                logger.warn { "GroupData[$groupNo] không tồn tại, giữ lại toàn bộ kết quả của nhóm này" }
                groupSelections[groupNo] = -1
                continue
            }

            val selectedNo = selectMember(groupData)
            groupSelections[groupNo] = selectedNo
            if (selectedNo == -1) {
                logger.debug { "GroupData[$groupNo] giữ lại toàn bộ (useMode=${groupData.useMode} probs=${groupData.probabilityRateAy})" }
            } else {
                logger.debug { "GroupData[$groupNo] chọn resultNo=$selectedNo (useMode=${groupData.useMode} members=${groupData.memberNoAy} probs=${groupData.probabilityRateAy})" }
            }
        }

        return results.filter { result ->
            val groupNo = result.resultGroupNo
            if (groupNo <= 0) return@filter true
            val selectedNo = groupSelections[groupNo] ?: return@filter true
            if (selectedNo == -1) return@filter true
            val keep = result.resultNo == selectedNo
            if (!keep && result.resultClass == 2) {
                logger.warn { "GroupData[$groupNo] đã lọc bỏ kết quả tiến trình nhiệm vụ: mission=${result.parameter} " +
                        "resultNo=${result.resultNo} selectedNo=$selectedNo" }
            }
            keep
        }
    }

    /**
     * Chọn một thành viên từ GroupData dựa theo xác suất
     *
     * @return resultNo được chọn; -1 = giữ lại toàn bộ thành viên
     */
    private fun selectMember(groupData: EveGroupData): Int {
        if (groupData.memberNoAy.isEmpty()) return -1
        if (groupData.memberNoAy.size == 1) return groupData.memberNoAy[0]

        /*
         * DVT Eve_GroupData.lua only parses PickMember; selection is server-side.
         * A full Eve.emg audit proves PickMember is NOT a resultNo:
         * - every positive PickMember is 1;
         * - hundreds of groups have members that do not contain resultNo 1;
         * - BaDauYeu / treasure-search groups use PickMember=1 with members such
         *   as [2,3], [4,5], [8..13] and positive ProbabilityRateAy.
         *
         * Therefore PickMember=1 means "pick one member", while MemberNoAy holds
         * the actual result numbers. Weighted groups must select from MemberNoAy.
         */
        val totalWeight = groupData.probabilityRateAy.sum()
        if (totalWeight > 0) {
            var roll = (0 until totalWeight).random()
            for (i in groupData.memberNoAy.indices) {
                roll -= groupData.probabilityRateAy.getOrElse(i) { 0 }
                if (roll < 0) return groupData.memberNoAy[i]
            }
            return groupData.memberNoAy.last()
        }

        // DVT has exactly one PickMember=1 / zero-weight group and it has one
        // member (handled above). Unknown multi-member zero-weight shapes stay
        // fail-open here, preserving all results rather than guessing a member.
        return -1
    }
}

