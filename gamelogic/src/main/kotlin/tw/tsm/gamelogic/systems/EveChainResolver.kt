package tw.tsm.gamelogic.systems

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.data.EveCondition
import tw.tsm.domain.data.EveResult

private val logger = KotlinLogging.logger {}

data class ChainResolveResult(
    val results: List<EveResult>,
    val matchedConditionNo: Int,
    val stepScore: Int
)

/**
 * Trình phân tích ghép nhóm và khớp chuỗi AND của Eve.
 *
 * Quy tắc chọn chain:
 * 1. Ưu tiên conditionValue cao nhất trong các điều kiện class=2 (bước nhiệm vụ).
 *    Dữ liệu DVT có event lưu các step không theo thứ tự, nên không thể chỉ first-match toàn cục.
 * 2. Khi stepScore bằng nhau, giữ chain khớp xuất hiện đầu tiên trong Eve.emg.
 *    Không tự ưu tiên chain dài hơn hoặc có nhiều result hơn. Scene 12180 eve=17 chứng minh
 *    heuristic đó làm nhánh hậu giao dịch param19 thắng nhánh mở menu ban đầu.
 */
object EveChainResolver {

    /**
     * andNum = tổng độ dài của chain, tính cả condition đầu.
     * andNum=0 ở condition đầu được xử lý như chain dài 1 để bảo thủ.
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
     * Tìm chain khớp tốt nhất theo step nhiệm vụ; nếu hòa giữ thứ tự Eve.emg.
     */
    fun resolve(
        conditions: List<EveCondition>,
        evaluator: (EveCondition) -> Boolean,
        chainFilter: ((List<EveCondition>) -> Boolean)? = null
    ): ChainResolveResult? {
        val chains = buildChains(conditions)
        var bestMatch: List<EveCondition>? = null
        var bestStepScore = -1

        for (chain in chains) {
            val head = chain.first()
            if (head.results.isEmpty()) continue
            if (chainFilter != null && !chainFilter(chain)) continue

            if (chain.all { cond -> evaluator(cond) }) {
                val stepScore = chain
                    .filter { it.conditionClass == 2 }
                    .maxOfOrNull { it.conditionValue } ?: 0

                // Chỉ thay thế khi step nhiệm vụ cao hơn.
                // Khi bằng nhau, chain gặp trước trong Eve.emg giữ nguyên ưu tiên.
                if (bestMatch == null || stepScore > bestStepScore) {
                    bestMatch = chain
                    bestStepScore = stepScore
                }
            }
        }

        val matched = bestMatch ?: return null
        val head = matched.first()
        logger.debug {
            "Chuỗi khớp: condNos=${matched.map { it.conditionNo }} → " +
                "${head.results.size} result (stepScore=$bestStepScore; tie=data-order)"
        }
        return ChainResolveResult(head.results, head.conditionNo, bestStepScore)
    }
}
