package tw.tsm.gamelogic.systems

import tw.tsm.domain.constants.ItemConstants
import tw.tsm.domain.data.EveResult

/**
 * Fail-closed, read-only projection of gold changes in a selected Eve segment.
 *
 * Kotlin dispatches resultType 0/7 sequentially. MoneySystem.addGold/subtractGold
 * can reject a late operation, after earlier quest/item results were committed.
 * Check the whole deterministic segment up front, before any mutation/packet.
 *
 * Battle (type3) and Surface (type6) change the future branch: never assume
 * results beyond them are already selected.
 *
 * This reduces partial quest completion. It is not a database transaction:
 * concurrent changes must still be checked by MoneySystem at execution time.
 */
object EveQuestGoldPreflightPolicy {
    enum class Failure { INVALID_OPERATION, INSUFFICIENT_GOLD, GOLD_LIMIT }

    data class Rejection(
        val failure: Failure,
        val resultNo: Int,
        val required: Long,
        val available: Long,
    )

    fun validate(
        results: List<EveResult>,
        startingGold: Int,
    ): Rejection? {
        var gold = startingGold.toLong()
        if (gold !in 0..ItemConstants.MAX_GOLD.toLong()) {
            return Rejection(Failure.INVALID_OPERATION, -1, gold, 0L)
        }

        for (result in results.takeWhile { it.resultType !in setOf(3, 6) }) {
            if (result.resultType !in setOf(0, 7)) continue
            val delta: Long = when {
                result.resultClass == 5 -> {
                    val op = EveResultClassPolicy.moneyOperation(
                        result.parameterStyle, result.resultValue
                    ) ?: return Rejection(
                        Failure.INVALID_OPERATION, result.resultNo,
                        result.resultValue.toLong(), gold,
                    )
                    when (op.kind) {
                        EveMoneyResultKind.ADD -> op.amount.toLong()
                        EveMoneyResultKind.SUBTRACT -> -op.amount.toLong()
                    }
                }
                result.resultClass == 7 && result.parameterStyle == 6 &&
                    result.resultValue > 0 -> result.resultValue.toLong()
                else -> continue
            }

            val next = gold + delta
            if (next < 0L) {
                return Rejection(
                    Failure.INSUFFICIENT_GOLD, result.resultNo, -delta, gold,
                )
            }
            if (next > ItemConstants.MAX_GOLD.toLong()) {
                return Rejection(
                    Failure.GOLD_LIMIT, result.resultNo, delta, gold,
                )
            }
            gold = next
        }
        return null
    }
}
