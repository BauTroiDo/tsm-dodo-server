package tw.tsm.tests

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import tw.tsm.domain.data.EveCondition
import tw.tsm.domain.data.EveResult
import tw.tsm.gamelogic.systems.EveChainResolver
import tw.tsm.gamelogic.systems.EveConditionEvaluator
import tw.tsm.gamelogic.systems.PlayerEventState

/**
 * EveChainResolver 單元測試
 *
 * 涵蓋：buildChains 分組 + resolve step/data-order 優先級 + chainFilter 過濾
 */
class EveChainResolverTest : DescribeSpec({

    // ── 輔助函數 ──

    fun result(type: Int = 1, meanNo: Int = 1) = EveResult(
        resultGroupNo = 0, resultNo = 1, resultType = type,
        resultClass = 0, parameter = 0, parameterStyle = 0,
        resultValue = 0, resultMeanNo = meanNo
    )

    fun cond(
        no: Int = 1, cls: Int = 0, param: Int = 0, pStyle: Int = 0,
        ops: Int = 0, value: Int = 0, andNum: Int = 1,
        results: List<EveResult> = emptyList()
    ) = EveCondition(
        conditionNo = no, conditionClass = cls,
        conditionParameter = param, conditionParameterStyle = pStyle,
        conditionOps = ops, conditionValue = value,
        conditionSubItem = 0, toResult = 0, andNum = andNum,
        results = results
    )

    // ══════════════════════════════════════════════
    //  buildChains — 分組測試
    // ══════════════════════════════════════════════

    describe("buildChains") {
        it("單條件 andNum=1 → 各自獨立") {
            val conditions = listOf(
                cond(no = 1, andNum = 1),
                cond(no = 2, andNum = 1),
                cond(no = 3, andNum = 1)
            )
            val chains = EveChainResolver.buildChains(conditions)
            chains.size shouldBe 3
            chains.map { it.size } shouldBe listOf(1, 1, 1)
        }

        it("andNum=2 → 兩個一組") {
            val conditions = listOf(
                cond(no = 1, andNum = 2),  // 鏈頭，帶下一個
                cond(no = 2, andNum = 0),  // AND
                cond(no = 3, andNum = 1),  // 獨立
            )
            val chains = EveChainResolver.buildChains(conditions)
            chains.size shouldBe 2
            chains[0].size shouldBe 2
            chains[0].map { it.conditionNo } shouldBe listOf(1, 2)
            chains[1].size shouldBe 1
        }

        it("andNum=3 → 三個一組") {
            val conditions = listOf(
                cond(no = 1, andNum = 3),
                cond(no = 2, andNum = 0),
                cond(no = 3, andNum = 0),
                cond(no = 4, andNum = 1),
            )
            val chains = EveChainResolver.buildChains(conditions)
            chains.size shouldBe 2
            chains[0].size shouldBe 3
            chains[1].size shouldBe 1
        }

        it("空列表 → 空結果") {
            EveChainResolver.buildChains(emptyList()).size shouldBe 0
        }
    }

    // ══════════════════════════════════════════════
    //  resolve — 匹配邏輯
    // ══════════════════════════════════════════════

    describe("resolve") {
        it("無匹配 → null") {
            val conditions = listOf(
                cond(cls = 2, ops = 1, value = 5, results = listOf(result()))
            )
            // 評估器永遠回傳 false
            EveChainResolver.resolve(conditions, evaluator = { false }).shouldBeNull()
        }

        it("鏈頭無 results → 跳過") {
            val conditions = listOf(
                cond(cls = 0, results = emptyList()),  // 無 results
                cond(no = 2, cls = 0, results = listOf(result(meanNo = 99)))  // 有 results
            )
            val resolved = EveChainResolver.resolve(conditions, evaluator = { true })
            resolved.shouldNotBeNull()
            resolved.results shouldBe listOf(result(meanNo = 99))
            resolved.matchedConditionNo shouldBe 2
        }

        it("AND 鏈中一個 false → 整條失敗") {
            val conditions = listOf(
                cond(no = 1, cls = 0, andNum = 2, results = listOf(result())),
                cond(no = 2, cls = 99, andNum = 0),  // 未知 class → false
            )
            val result = EveChainResolver.resolve(
                conditions,
                evaluator = { c -> c.conditionClass != 99 }
            )
            result.shouldBeNull()
        }

        it("四層排序 — stepScore 優先") {
            // 鏈 A：cls=2 val=1（低 stepScore）
            // 鏈 B：cls=2 val=3（高 stepScore）→ 應被選
            val conditions = listOf(
                cond(no = 1, cls = 2, value = 1, results = listOf(result(meanNo = 10))),
                cond(no = 2, cls = 2, value = 3, results = listOf(result(meanNo = 20))),
            )
            val resolved = EveChainResolver.resolve(conditions, evaluator = { true })
            resolved.shouldNotBeNull()
            resolved.results shouldBe listOf(result(meanNo = 20))
            resolved.matchedConditionNo shouldBe 2
        }

        it("stepScore bằng nhau thì giữ chain xuất hiện trước, không ưu tiên chain dài") {
            val conditions = listOf(
                cond(no = 1, cls = 0, andNum = 1, results = listOf(result(meanNo = 10))),
                cond(no = 2, cls = 0, andNum = 2, results = listOf(result(meanNo = 20))),
                cond(no = 3, cls = 0, andNum = 0),
            )
            val resolved = EveChainResolver.resolve(conditions, evaluator = { true })
            resolved.shouldNotBeNull()
            resolved.results shouldBe listOf(result(meanNo = 10))
            resolved.matchedConditionNo shouldBe 1
        }

        it("stepScore bằng nhau thì giữ chain xuất hiện trước, không ưu tiên nhiều result") {
            val conditions = listOf(
                cond(no = 1, cls = 0, results = listOf(result(meanNo = 10))),
                cond(no = 2, cls = 0, results = listOf(result(meanNo = 20), result(meanNo = 21))),
            )
            val resolved = EveChainResolver.resolve(conditions, evaluator = { true })
            resolved.shouldNotBeNull()
            resolved.results shouldBe listOf(result(meanNo = 10))
            resolved.matchedConditionNo shouldBe 1
        }

        it("12180 eve17: initial click phải giữ cond1, không nhảy thẳng tới Talk48728") {
            val initialResults = listOf(
                result(type = 1, meanNo = 22363),
                result(type = 6, meanNo = 1)
            )
            val successResults = listOf(result(type = 1, meanNo = 48728))
            val conditions = listOf(
                // cond1: serverId < 100 — nhánh mở event/menu ban đầu
                cond(
                    no = 1, cls = 7, param = 8, pStyle = 1,
                    ops = 2, value = 100, andNum = 1, results = initialResults
                ),
                // cond10+11: hậu giao dịch — Point >=10 AND serverId <100
                cond(
                    no = 10, cls = 7, param = 19, pStyle = 2,
                    ops = 5, value = 10, andNum = 2, results = successResults
                ),
                cond(
                    no = 11, cls = 7, param = 8, pStyle = 1,
                    ops = 2, value = 100, andNum = 0
                )
            )
            val state = PlayerEventState(serverId = 1, realPoint = 100)
            val resolved = EveChainResolver.resolve(
                conditions,
                evaluator = { EveConditionEvaluator.evaluate(it, state) }
            )

            resolved.shouldNotBeNull()
            resolved.matchedConditionNo shouldBe 1
            resolved.results shouldBe initialResults
        }

        it("四層排序 — 全相同時先出現者優先") {
            val conditions = listOf(
                cond(no = 1, cls = 0, results = listOf(result(meanNo = 10))),
                cond(no = 2, cls = 0, results = listOf(result(meanNo = 20))),
            )
            val resolved = EveChainResolver.resolve(conditions, evaluator = { true })
            resolved.shouldNotBeNull()
            // 都是 stepScore=0, len=1, resultCount=1 → 先出現的 meanNo=10
            resolved.results shouldBe listOf(result(meanNo = 10))
            resolved.matchedConditionNo shouldBe 1
        }
    }

    // ══════════════════════════════════════════════
    //  chainFilter — 重評過濾
    // ══════════════════════════════════════════════

    describe("chainFilter") {
        it("過濾出含 class=10 的鏈") {
            val conditions = listOf(
                cond(no = 1, cls = 0, results = listOf(result(meanNo = 10))),
                cond(no = 2, cls = 10, param = 100, pStyle = 30, andNum = 1, results = listOf(result(meanNo = 20))),
            )
            val resolved = EveChainResolver.resolve(
                conditions,
                evaluator = { true },
                chainFilter = { chain -> chain.any { it.conditionClass == 10 } }
            )
            resolved.shouldNotBeNull()
            resolved.results shouldBe listOf(result(meanNo = 20))
            resolved.matchedConditionNo shouldBe 2
        }

        it("過濾後無匹配 → null") {
            val conditions = listOf(
                cond(no = 1, cls = 0, results = listOf(result(meanNo = 10))),
            )
            val resolved = EveChainResolver.resolve(
                conditions,
                evaluator = { true },
                chainFilter = { chain -> chain.any { it.conditionClass == 8 } }
            )
            resolved.shouldBeNull()
        }
    }
})
