package tw.tsm.tests

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import tw.tsm.domain.data.EveCondition
import tw.tsm.domain.data.EveResult
import tw.tsm.domain.data.NpcEventData
import tw.tsm.gamelogic.systems.EveAutoChainEngine
import tw.tsm.gamelogic.systems.EventSession
import tw.tsm.gamelogic.systems.PlayerEventState

/**
 * Kiểm thử đơn vị EveAutoChainEngine
 *
 * Bao gồm: shouldAttemptAutoChain / shouldSkipEvent / 4 lớp phòng hộ
 */
class EveAutoChainEngineTest : DescribeSpec({

    // ── Hàm phụ trợ ──

    fun result(type: Int = 1, cls: Int = 0, param: Int = 0, pStyle: Int = 0) = EveResult(
        resultGroupNo = 0, resultNo = 1, resultType = type,
        resultClass = cls, parameter = param, parameterStyle = pStyle,
        resultValue = 0, resultMeanNo = 1
    )

    fun session(
        eveNo: Int = 1,
        triggerKind: Int = 1,
        chainDepth: Int = 0,
        condNo: Int = 0,
        choiceCode: Int = -1,
        battleResult: Int = 0,
        results: List<EveResult> = listOf(result())
    ) = EventSession(
        mapId = 10701, eveNo = eveNo, npcClickId = 1,
        triggerKind = triggerKind, chainDepth = chainDepth,
        matchedConditionNo = condNo, lastChoiceCode = choiceCode,
        battleResult = battleResult, results = results
    )

    fun cond(no: Int = 1, cls: Int = 0) = EveCondition(
        conditionNo = no, conditionClass = cls,
        conditionParameter = 0, conditionParameterStyle = 0,
        conditionOps = 0, conditionValue = 0,
        conditionSubItem = 0, toResult = 0, andNum = 1,
        results = emptyList()
    )

    fun eventData(eveNo: Int, conditions: List<EveCondition> = listOf(cond())) =
        NpcEventData(eveNo = eveNo, whenHappen = emptyList(), conditions = conditions)

    fun state(completedEveCounts: Map<Int, Int> = emptyMap()) = PlayerEventState(
        completedEveCounts = completedEveCounts
    )

    // ══════════════════════════════════════════════
    //  shouldAttemptAutoChain
    // ══════════════════════════════════════════════

    describe("shouldAttemptAutoChain") {

        //  Tạo instance tối thiểu (shouldAttemptAutoChain là hàm thuần)
        val engine = EveAutoChainEngine(
            stateMachine = io.mockk.mockk(relaxed = true),
            stateBuilder = io.mockk.mockk(relaxed = true),
            conditionResolver = io.mockk.mockk(relaxed = true),
            eveData = emptyMap()
        )

        it("ClickNpc + eveNo>0 + depth<10 → cho phép") {
            engine.shouldAttemptAutoChain(
                session(triggerKind = 1, eveNo = 4, chainDepth = 0)
            ) shouldBe true
        }

        it("MeetDoor + eveNo>0 + depth<10 → cho phép") {
            engine.shouldAttemptAutoChain(
                session(triggerKind = 8, eveNo = 2, chainDepth = 5)
            ) shouldBe true
        }

        it("eveNo=0 (fallback Talk) → không cho phép") {
            engine.shouldAttemptAutoChain(
                session(triggerKind = 1, eveNo = 0, chainDepth = 0)
            ) shouldBe false
        }

        it("độ sâu đạt giới hạn → không cho phép") {
            engine.shouldAttemptAutoChain(
                session(triggerKind = 1, eveNo = 4, chainDepth = 10)
            ) shouldBe false
        }

        it("loại kích hoạt không nằm trong danh sách cho phép → không cho phép") {
            engine.shouldAttemptAutoChain(
                session(triggerKind = 99, eveNo = 4, chainDepth = 0)
            ) shouldBe false
        }
    }

    // ══════════════════════════════════════════════
    //  shouldSkipEvent
    // ══════════════════════════════════════════════

    describe("shouldSkipEvent") {

        val engine = EveAutoChainEngine(
            stateMachine = io.mockk.mockk(relaxed = true),
            stateBuilder = io.mockk.mockk(relaxed = true),
            conditionResolver = io.mockk.mockk(relaxed = true),
            eveData = emptyMap()
        )

        val events = listOf(1, 2, 3)

        it("đã hoàn thành và không có conditionClass=12 → bỏ qua") {
            engine.shouldSkipEvent(
                events, 1, eventData(1), state(completedEveCounts = mapOf(1 to 1))
            ) shouldBe true
        }

        it("đã hoàn thành nhưng có conditionClass=12 → không bỏ qua") {
            engine.shouldSkipEvent(
                events, 1, eventData(1, listOf(cond(cls = 12))),
                state(completedEveCounts = mapOf(1 to 1))
            ) shouldBe false
        }

        it("chưa hoàn thành và phía sau có sự kiện đã xong (mức tiến độ) → bỏ qua") {
            engine.shouldSkipEvent(
                events, 1, eventData(1), state(completedEveCounts = mapOf(2 to 1))
            ) shouldBe true
        }

        it("chưa hoàn thành và phía sau không có sự kiện đã xong → không bỏ qua") {
            engine.shouldSkipEvent(
                events, 2, eventData(2), state(completedEveCounts = mapOf(1 to 1))
            ) shouldBe false
        }
    }

    // ══════════════════════════════════════════════
    //  #1 detectSameChain — phát hiện vòng lặp
    // ══════════════════════════════════════════════

    describe("detectSameChain") {

        val engine = EveAutoChainEngine(
            stateMachine = io.mockk.mockk(relaxed = true),
            stateBuilder = io.mockk.mockk(relaxed = true),
            conditionResolver = io.mockk.mockk(relaxed = true),
            eveData = emptyMap()
        )

        it("conditionNo=0 giống nhau → phát hiện vòng lặp (trước khi sửa sẽ bỏ sót)") {
            engine.detectSameChain(
                session(condNo = 0), session(condNo = 0)
            ) shouldBe true
        }

        it("conditionNo=1 giống nhau → phát hiện vòng lặp") {
            engine.detectSameChain(
                session(condNo = 1), session(condNo = 1)
            ) shouldBe true
        }

        it("conditionNo khác nhau → không phải vòng lặp (nối hợp lệ)") {
            engine.detectSameChain(
                session(condNo = 0), session(condNo = 1)
            ) shouldBe false
        }
    }

    // ══════════════════════════════════════════════
    //  #2 detectReQuestion — phòng việc hỏi lại đề
    // ══════════════════════════════════════════════

    describe("detectReQuestion") {

        val engine = EveAutoChainEngine(
            stateMachine = io.mockk.mockk(relaxed = true),
            stateBuilder = io.mockk.mockk(relaxed = true),
            conditionResolver = io.mockk.mockk(relaxed = true),
            eveData = emptyMap()
        )

        it("lần trước có lựa chọn + sự kiện mới có Surface → hỏi lại") {
            engine.detectReQuestion(
                session(results = listOf(result(type = 6))),
                session(choiceCode = 20)
            ) shouldBe true
        }

        it("lần trước không có lựa chọn → không phải hỏi lại") {
            engine.detectReQuestion(
                session(results = listOf(result(type = 6))),
                session(choiceCode = -1)
            ) shouldBe false
        }
    }

    // ══════════════════════════════════════════════
    //  #3 detectReBattle — phòng đánh lại trận chiến
    // ══════════════════════════════════════════════

    describe("detectReBattle") {

        val engine = EveAutoChainEngine(
            stateMachine = io.mockk.mockk(relaxed = true),
            stateBuilder = io.mockk.mockk(relaxed = true),
            conditionResolver = io.mockk.mockk(relaxed = true),
            eveData = emptyMap()
        )

        it("lần trước có chiến đấu + sự kiện mới có Battle → đánh lại") {
            engine.detectReBattle(
                session(results = listOf(result(type = 3))),
                session(battleResult = 1)
            ) shouldBe true
        }

        it("lần trước không chiến đấu → không phải đánh lại") {
            engine.detectReBattle(
                session(results = listOf(result(type = 3))),
                session(battleResult = 0)
            ) shouldBe false
        }
    }

    // ══════════════════════════════════════════════
    //  #4 detectDuplicateItems — phòng trùng lặp vật phẩm
    // ══════════════════════════════════════════════

    describe("detectDuplicateItems") {

        val engine = EveAutoChainEngine(
            stateMachine = io.mockk.mockk(relaxed = true),
            stateBuilder = io.mockk.mockk(relaxed = true),
            conditionResolver = io.mockk.mockk(relaxed = true),
            eveData = emptyMap()
        )

        it("vật phẩm giống nhau → trùng lặp") {
            engine.detectDuplicateItems(
                session(results = listOf(result(cls = 1, pStyle = 1, param = 100))),
                session(results = listOf(result(cls = 1, pStyle = 1, param = 100)))
            ) shouldBe true
        }

        it("vật phẩm khác nhau → không trùng") {
            engine.detectDuplicateItems(
                session(results = listOf(result(cls = 1, pStyle = 1, param = 200))),
                session(results = listOf(result(cls = 1, pStyle = 1, param = 100)))
            ) shouldBe false
        }

        it("sự kiện hoàn thành không thưởng vật phẩm → không trùng") {
            engine.detectDuplicateItems(
                session(results = listOf(result(cls = 1, pStyle = 1, param = 100))),
                session(results = listOf(result()))
            ) shouldBe false
        }
    }
})
