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
 * EveAutoChainEngine 單元測試
 *
 * 涵蓋：shouldAttemptAutoChain / shouldSkipEvent / 4 層防護
 */
class EveAutoChainEngineTest : DescribeSpec({

    // ── 輔助函數 ──

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

        // 建立最小實例（shouldAttemptAutoChain 是純函數）
        val engine = EveAutoChainEngine(
            stateMachine = io.mockk.mockk(relaxed = true),
            stateBuilder = io.mockk.mockk(relaxed = true),
            conditionResolver = io.mockk.mockk(relaxed = true),
            eveData = emptyMap()
        )

        it("ClickNpc + eveNo>0 + depth<10 → 允許") {
            engine.shouldAttemptAutoChain(
                session(triggerKind = 1, eveNo = 4, chainDepth = 0)
            ) shouldBe true
        }

        it("MeetDoor + eveNo>0 + depth<10 → 允許") {
            engine.shouldAttemptAutoChain(
                session(triggerKind = 8, eveNo = 2, chainDepth = 5)
            ) shouldBe true
        }

        it("eveNo=0（fallback Talk）→ 不允許") {
            engine.shouldAttemptAutoChain(
                session(triggerKind = 1, eveNo = 0, chainDepth = 0)
            ) shouldBe false
        }

        it("深度達上限 → 不允許") {
            engine.shouldAttemptAutoChain(
                session(triggerKind = 1, eveNo = 4, chainDepth = 10)
            ) shouldBe false
        }

        it("非允許觸發類型 → 不允許") {
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

        it("已完成且無 conditionClass=12 → 跳過") {
            engine.shouldSkipEvent(
                events, 1, eventData(1), state(completedEveCounts = mapOf(1 to 1))
            ) shouldBe true
        }

        it("已完成但有 conditionClass=12 → 不跳過") {
            engine.shouldSkipEvent(
                events, 1, eventData(1, listOf(cond(cls = 12))),
                state(completedEveCounts = mapOf(1 to 1))
            ) shouldBe false
        }

        it("未完成且後面有已完成事件（進度水位線）→ 跳過") {
            engine.shouldSkipEvent(
                events, 1, eventData(1), state(completedEveCounts = mapOf(2 to 1))
            ) shouldBe true
        }

        it("未完成且後面無已完成事件 → 不跳過") {
            engine.shouldSkipEvent(
                events, 2, eventData(2), state(completedEveCounts = mapOf(1 to 1))
            ) shouldBe false
        }
    }

    // ══════════════════════════════════════════════
    //  #1 detectSameChain — 迴圈偵測
    // ══════════════════════════════════════════════

    describe("detectSameChain") {

        val engine = EveAutoChainEngine(
            stateMachine = io.mockk.mockk(relaxed = true),
            stateBuilder = io.mockk.mockk(relaxed = true),
            conditionResolver = io.mockk.mockk(relaxed = true),
            eveData = emptyMap()
        )

        it("conditionNo=0 相同 → 偵測為迴圈（修復前會漏判）") {
            engine.detectSameChain(
                session(condNo = 0), session(condNo = 0)
            ) shouldBe true
        }

        it("conditionNo=1 相同 → 偵測為迴圈") {
            engine.detectSameChain(
                session(condNo = 1), session(condNo = 1)
            ) shouldBe true
        }

        it("conditionNo 不同 → 非迴圈（合法接續）") {
            engine.detectSameChain(
                session(condNo = 0), session(condNo = 1)
            ) shouldBe false
        }
    }

    // ══════════════════════════════════════════════
    //  #2 detectReQuestion — 答題重問防護
    // ══════════════════════════════════════════════

    describe("detectReQuestion") {

        val engine = EveAutoChainEngine(
            stateMachine = io.mockk.mockk(relaxed = true),
            stateBuilder = io.mockk.mockk(relaxed = true),
            conditionResolver = io.mockk.mockk(relaxed = true),
            eveData = emptyMap()
        )

        it("上次有選擇 + 新事件有 Surface → 重問") {
            engine.detectReQuestion(
                session(results = listOf(result(type = 6))),
                session(choiceCode = 20)
            ) shouldBe true
        }

        it("上次無選擇 → 非重問") {
            engine.detectReQuestion(
                session(results = listOf(result(type = 6))),
                session(choiceCode = -1)
            ) shouldBe false
        }
    }

    // ══════════════════════════════════════════════
    //  #3 detectReBattle — 戰鬥重打防護
    // ══════════════════════════════════════════════

    describe("detectReBattle") {

        val engine = EveAutoChainEngine(
            stateMachine = io.mockk.mockk(relaxed = true),
            stateBuilder = io.mockk.mockk(relaxed = true),
            conditionResolver = io.mockk.mockk(relaxed = true),
            eveData = emptyMap()
        )

        it("上次有戰鬥 + 新事件有 Battle → 重打") {
            engine.detectReBattle(
                session(results = listOf(result(type = 3))),
                session(battleResult = 1)
            ) shouldBe true
        }

        it("上次無戰鬥 → 非重打") {
            engine.detectReBattle(
                session(results = listOf(result(type = 3))),
                session(battleResult = 0)
            ) shouldBe false
        }
    }

    // ══════════════════════════════════════════════
    //  #4 detectDuplicateItems — 物品重複防護
    // ══════════════════════════════════════════════

    describe("detectDuplicateItems") {

        val engine = EveAutoChainEngine(
            stateMachine = io.mockk.mockk(relaxed = true),
            stateBuilder = io.mockk.mockk(relaxed = true),
            conditionResolver = io.mockk.mockk(relaxed = true),
            eveData = emptyMap()
        )

        it("相同物品 → 重複") {
            engine.detectDuplicateItems(
                session(results = listOf(result(cls = 1, pStyle = 1, param = 100))),
                session(results = listOf(result(cls = 1, pStyle = 1, param = 100)))
            ) shouldBe true
        }

        it("不同物品 → 非重複") {
            engine.detectDuplicateItems(
                session(results = listOf(result(cls = 1, pStyle = 1, param = 200))),
                session(results = listOf(result(cls = 1, pStyle = 1, param = 100)))
            ) shouldBe false
        }

        it("完成事件無給予物品 → 非重複") {
            engine.detectDuplicateItems(
                session(results = listOf(result(cls = 1, pStyle = 1, param = 100))),
                session(results = listOf(result()))
            ) shouldBe false
        }
    }
})
