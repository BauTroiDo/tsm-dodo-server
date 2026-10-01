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
 * Kiểm thử đơn vị EveChainResolver
 *
 * Bao gồm: gom nhóm buildChains + độ ưu tiên step/data-order của resolve + lọc chainFilter
 */
class EveChainResolverTest : DescribeSpec({

    // ── Hàm phụ trợ ──

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
    //  buildChains — kiểm thử gom nhóm
    // ══════════════════════════════════════════════

    describe("buildChains") {
        it("điều kiện đơn andNum=1 → mỗi cái độc lập") {
            val conditions = listOf(
                cond(no = 1, andNum = 1),
                cond(no = 2, andNum = 1),
                cond(no = 3, andNum = 1)
            )
            val chains = EveChainResolver.buildChains(conditions)
            chains.size shouldBe 3
            chains.map { it.size } shouldBe listOf(1, 1, 1)
        }

        it("andNum=2 → hai cái một nhóm") {
            val conditions = listOf(
                cond(no = 1, andNum = 2),  // Đầu chuỗi, chỉ tới phần tử kế tiếp
                cond(no = 2, andNum = 0),  // AND
                cond(no = 3, andNum = 1),  // Độc lập
            )
            val chains = EveChainResolver.buildChains(conditions)
            chains.size shouldBe 2
            chains[0].size shouldBe 2
            chains[0].map { it.conditionNo } shouldBe listOf(1, 2)
            chains[1].size shouldBe 1
        }

        it("andNum=3 → ba cái một nhóm") {
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

        it("danh sách trống → kết quả trống") {
            EveChainResolver.buildChains(emptyList()).size shouldBe 0
        }
    }

    // ══════════════════════════════════════════════
    //  resolve — logic so khớp
    // ══════════════════════════════════════════════

    describe("resolve") {
        it("không khớp → null") {
            val conditions = listOf(
                cond(cls = 2, ops = 1, value = 5, results = listOf(result()))
            )
            // Bộ đánh giá luôn trả về false
            EveChainResolver.resolve(conditions, evaluator = { false }).shouldBeNull()
        }

        it("đầu chuỗi không có results → bỏ qua") {
            val conditions = listOf(
                cond(cls = 0, results = emptyList()),  // Không có results
                cond(no = 2, cls = 0, results = listOf(result(meanNo = 99)))  // Có results
            )
            val resolved = EveChainResolver.resolve(conditions, evaluator = { true })
            resolved.shouldNotBeNull()
            resolved.results shouldBe listOf(result(meanNo = 99))
            resolved.matchedConditionNo shouldBe 2
        }

        it("một phần tử false trong chuỗi AND → cả chuỗi thất bại") {
            val conditions = listOf(
                cond(no = 1, cls = 0, andNum = 2, results = listOf(result())),
                cond(no = 2, cls = 99, andNum = 0),  // class không xác định → false
            )
            val result = EveChainResolver.resolve(
                conditions,
                evaluator = { c -> c.conditionClass != 99 }
            )
            result.shouldBeNull()
        }

        it("sắp xếp bốn tầng — stepScore ưu tiên") {
            // Chuỗi A: cls=2 val=1 (stepScore thấp)
            // Chuỗi B: cls=2 val=3 (stepScore cao) → sẽ được chọn
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

        it("sắp xếp bốn tầng — khi giống hết, cái xuất hiện trước ưu tiên") {
            val conditions = listOf(
                cond(no = 1, cls = 0, results = listOf(result(meanNo = 10))),
                cond(no = 2, cls = 0, results = listOf(result(meanNo = 20))),
            )
            val resolved = EveChainResolver.resolve(conditions, evaluator = { true })
            resolved.shouldNotBeNull()
            // Đều là stepScore=0, len=1, resultCount=1 → cái xuất hiện trước có meanNo=10
            resolved.results shouldBe listOf(result(meanNo = 10))
            resolved.matchedConditionNo shouldBe 1
        }
    }

    // ══════════════════════════════════════════════
    //  post-battle Door continuation
    // ══════════════════════════════════════════════

    describe("post-battle Door continuation") {
        it("12421 eve2: sau nhánh cập nhật mission phải nối tiếp Door warp") {
            val updateGold = EveResult(0, 1, 0, 7, 2, 2, -200, 0)
            val updateMission = EveResult(0, 2, 0, 2, 10044, 1, 1, 0)
            val doorWarp = EveResult(0, 1, 2, 7, 2, 0, 0, 0)
            val conditions = listOf(
                cond(
                    no = 2, cls = 8, param = 0, pStyle = 1, andNum = 2,
                    results = listOf(updateGold, updateMission)
                ),
                cond(
                    no = 3, cls = 2, param = 10044, pStyle = 3,
                    ops = 5, value = 0, andNum = 0
                ),
                cond(
                    no = 6, cls = 8, param = 0, pStyle = 1, andNum = 1,
                    results = listOf(doorWarp)
                )
            )
            val state = PlayerEventState(
                missionSteps = mapOf(10044 to 6),
                battleResult = 1
            )
            val evaluator = { c: EveCondition -> EveConditionEvaluator.evaluate(c, state) }

            val resolved = EveChainResolver.resolve(
                conditions, evaluator, chainFilter = { chain -> chain.any { it.conditionClass == 8 } }
            )
            resolved.shouldNotBeNull()
            resolved.matchedConditionNo shouldBe 2
            resolved.results shouldBe listOf(updateGold, updateMission)

            EveChainResolver.findPostBattleDoorContinuation(
                conditions, evaluator, resolved.matchedConditionNo
            ) shouldBe listOf(doorWarp)
        }

        it("không nối Door nếu nhánh đã chọn là class8 đơn lẻ") {
            val doorWarp = EveResult(0, 1, 2, 7, 2, 0, 0, 0)
            val conditions = listOf(
                cond(no = 1, cls = 8, pStyle = 1, results = listOf(doorWarp))
            )
            EveChainResolver.findPostBattleDoorContinuation(
                conditions, evaluator = { true }, matchedConditionNo = 1
            ) shouldBe emptyList()
        }
    }

    // ══════════════════════════════════════════════
    //  chainFilter — lọc đánh giá lại
    // ══════════════════════════════════════════════
    describe("chainFilter") {
        it("lọc ra chuỗi chứa class=10") {
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

        it("lọc xong không khớp → null") {
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
