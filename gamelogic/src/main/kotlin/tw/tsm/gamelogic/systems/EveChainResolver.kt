package tw.tsm.gamelogic.systems

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.data.EveCondition
import tw.tsm.domain.data.EveResult

private val logger = KotlinLogging.logger {}

/**
 * Kết quả phân tích chuỗi AND
 *
 * @property results            ResultArray khớp với đầu chuỗi
 * @property matchedConditionNo conditionNo khớp với đầu chuỗi (dùng để phát hiện vòng lặp auto-chain)
 */
data class ChainResolveResult(
    val results: List<EveResult>,
    val matchedConditionNo: Int,
    val stepScore: Int
)

/**
 * Trình phân tích ghép nhóm và khớp chuỗi AND của Eve
 *
 * Chịu trách nhiệm phân nhóm ConditionArray thành các chuỗi AND dựa theo andNum,
 * sau đó tìm ra chuỗi khớp nhất và có results ở đầu chuỗi.
 *
 * **Chiến lược khớp**: Sắp xếp 4 lớp
 * 1. Sắp xếp chính: conditionValue cao nhất trong các điều kiện cls=2 (bước nhiệm vụ) (ngưỡng step càng cao = càng chính xác)
 * 2. Sắp xếp phụ: Độ dài chuỗi (càng dài = càng nhiều ràng buộc = càng chính xác)
 * 3. Sắp xếp thứ 3: Số lượng results ở đầu chuỗi (càng nhiều = handler chính, càng ít = câu chào fallback)
 * 4. Sắp xếp cuối: Xuất hiện trước (Thứ tự dữ liệu = Ưu tiên thiết kế)
 *
 * Nguồn: Eve_NpcEventData.lua — Trường andNum định nghĩa cấu trúc chuỗi AND.
 */
object EveChainResolver {

    /**
     * Phân nhóm danh sách điều kiện thành các chuỗi AND
     *
     * andNum = Tổng độ dài của chuỗi này (bao gồm cả điều kiện bắt đầu):
     * - andNum=0: Thuộc về chuỗi trước (không nên ở điểm bắt đầu, trường hợp ngoại lệ (edge case) xem như là 1)
     * - andNum=1: Điều kiện độc lập
     * - andNum=2: Điều kiện này + AND tiếp theo
     *
     * Ví dụ: [andNum=2, 0, 2, 0, 1, 1] → [[0,1], [2,3], [4], [5]]
     */
    fun buildChains(conditions: List<EveCondition>): List<List<EveCondition>> {
        val chains = mutableListOf<List<EveCondition>>()
        var i = 0
        while (i < conditions.size) {
            val chainLen = conditions[i].andNum.coerceAtLeast(1)
            val chainEnd = minOf(i + chainLen, conditions.size)
            chains.add(conditions.subList(i, chainEnd))
            i = chainEnd
        }
        return chains
    }

    /**
     * Tìm chuỗi AND đầu tiên khớp hoàn toàn trong danh sách điều kiện, trả về results của chuỗi đó
     *
     * @param conditions  Danh sách điều kiện gốc
     * @param evaluator   Hàm đánh giá điều kiện (do người gọi truyền vào, thường là EveConditionEvaluator::evaluate)
     * @param chainFilter Điều kiện lọc bổ sung tùy chọn (ví dụ "chuỗi phải chứa class=8/10")
     * @return [ChainResolveResult] phù hợp (chứa results + conditionNo), null = không khớp
     */
    fun resolve(
        conditions: List<EveCondition>,
        evaluator: (EveCondition) -> Boolean,
        chainFilter: ((List<EveCondition>) -> Boolean)? = null
    ): ChainResolveResult? {
        val chains = buildChains(conditions)
        var bestMatch: List<EveCondition>? = null
        var bestStepScore = -1
        var bestLength = 0
        var bestResultCount = 0

        for (chain in chains) {
            val head = chain.first()
            // Đầu chuỗi phải có results
            if (head.results.isEmpty()) continue
            // Lọc bổ sung tùy chọn (dùng cho đánh giá lại: bắt buộc chứa class=8 hoặc class=10)
            if (chainFilter != null && !chainFilter(chain)) continue
            // Tất cả điều kiện trong chuỗi phải đồng thời thỏa mãn
            if (chain.all { cond -> evaluator(cond) }) {
                // stepScore = conditionValue cao nhất trong các điều kiện cls=2 (ngưỡng step)
                val stepScore = chain
                    .filter { it.conditionClass == 2 }
                    .maxOfOrNull { it.conditionValue } ?: 0
                val resultCount = head.results.size

                // Sắp xếp 4 lớp: stepScore → độ dài chuỗi → số lượng results → xuất hiện trước
                // Số lượng results phân biệt handler chính (hội thoại đầy đủ + action) và fallback (chỉ có câu chào)
                val isBetter = stepScore > bestStepScore ||
                        (stepScore == bestStepScore && chain.size > bestLength) ||
                        (stepScore == bestStepScore && chain.size == bestLength && resultCount > bestResultCount)

                if (bestMatch == null || isBetter) {
                    bestMatch = chain
                    bestStepScore = stepScore
                    bestLength = chain.size
                    bestResultCount = resultCount
                }
            }
        }

        if (bestMatch != null) {
            val head = bestMatch.first()
            logger.debug { "Chuỗi khớp: condNos=${bestMatch.map { it.conditionNo }} → ${head.results.size} result (stepScore=$bestStepScore len=${bestMatch.size})" }
            return ChainResolveResult(head.results, head.conditionNo, bestStepScore)
        }
        return null
    }
}