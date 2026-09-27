package tw.tsm.tests

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import tw.tsm.domain.data.EveCondition
import tw.tsm.gamelogic.systems.EveConditionEvaluator
import tw.tsm.gamelogic.systems.PlayerEventState

/**
 * EveConditionEvaluator 單元測試
 *
 * 涵蓋：compareStep 7 個 ops + 9 個 conditionClass
 */
class EveConditionEvaluatorTest : DescribeSpec({

    // ── 輔助函數 ──

    fun cond(
        cls: Int = 0, param: Int = 0, pStyle: Int = 0,
        ops: Int = 0, value: Int = 0, andNum: Int = 1
    ) = EveCondition(
        conditionNo = 1, conditionClass = cls,
        conditionParameter = param, conditionParameterStyle = pStyle,
        conditionOps = ops, conditionValue = value,
        conditionSubItem = 0, toResult = 0, andNum = andNum,
        results = emptyList()
    )

    val emptyState = PlayerEventState()

    // ══════════════════════════════════════════════
    //  compareStep — 7 個 ops
    // ══════════════════════════════════════════════

    describe("compareStep") {
        it("ops=0 等於") {
            EveConditionEvaluator.compareStep(5, 0, 5) shouldBe true
            EveConditionEvaluator.compareStep(5, 0, 3) shouldBe false
        }
        it("ops=1 小於") {
            EveConditionEvaluator.compareStep(3, 1, 5) shouldBe true
            EveConditionEvaluator.compareStep(5, 1, 5) shouldBe false
        }
        it("ops=2 大於") {
            EveConditionEvaluator.compareStep(6, 2, 5) shouldBe true
            EveConditionEvaluator.compareStep(5, 2, 5) shouldBe false
        }
        it("ops=3 小等") {
            EveConditionEvaluator.compareStep(5, 3, 5) shouldBe true
            EveConditionEvaluator.compareStep(6, 3, 5) shouldBe false
        }
        it("ops=4 大於") {
            EveConditionEvaluator.compareStep(6, 4, 5) shouldBe true
            EveConditionEvaluator.compareStep(5, 4, 5) shouldBe false
        }
        it("ops=5 大等") {
            EveConditionEvaluator.compareStep(5, 5, 5) shouldBe true
            EveConditionEvaluator.compareStep(4, 5, 5) shouldBe false
        }
        it("ops=6 不等") {
            EveConditionEvaluator.compareStep(4, 6, 5) shouldBe true
            EveConditionEvaluator.compareStep(5, 6, 5) shouldBe false
        }
    }

    // ══════════════════════════════════════════════
    //  conditionClass
    // ══════════════════════════════════════════════

    describe("class=0 無條件") {
        it("永遠 true") {
            EveConditionEvaluator.evaluate(cond(cls = 0), emptyState) shouldBe true
        }
    }

    describe("class=1 物品持有（反向比較）") {
        val state = PlayerEventState(bagItems = mapOf(100 to 3))

        it("val=0 ops=2 → 有此物品（0 < 3）") {
            EveConditionEvaluator.evaluate(
                cond(cls = 1, param = 100, ops = 2, value = 0), state
            ) shouldBe true
        }
        it("val=0 ops=5 → 沒有此物品（0 >= 3 = false）") {
            EveConditionEvaluator.evaluate(
                cond(cls = 1, param = 100, ops = 5, value = 0), state
            ) shouldBe false
        }
        it("沒有的物品 val=0 ops=5 → true（0 >= 0）") {
            EveConditionEvaluator.evaluate(
                cond(cls = 1, param = 999, ops = 5, value = 0), state
            ) shouldBe true
        }
    }

    describe("class=16 非背包倉儲物品") {
        it("只檢查錢莊/寶庫彙總數量，不重用背包數量") {
            val state = PlayerEventState(
                bagItems = mapOf(20059 to 1),
                storedItems = mapOf(20059 to 0, 29348 to 2)
            )

            EveConditionEvaluator.evaluate(
                cond(cls = 16, param = 29348, pStyle = 1, ops = 2, value = 0), state
            ) shouldBe true
            EveConditionEvaluator.evaluate(
                cond(cls = 16, param = 20059, pStyle = 1, ops = 2, value = 0), state
            ) shouldBe false
            EveConditionEvaluator.evaluate(
                cond(cls = 16, param = 20059, pStyle = 1, ops = 5, value = 0), state
            ) shouldBe true
        }
    }

    describe("class=2 任務步驟") {
        val state = PlayerEventState(
            missionSteps = mapOf(10 to 2),
            missionFlags = mapOf(10 to 1)
        )

        it("pStyle=1 只看 step — step=2 ops=5 val=1 → 2>=1 true") {
            EveConditionEvaluator.evaluate(
                cond(cls = 2, param = 10, pStyle = 1, ops = 5, value = 1), state
            ) shouldBe true
        }
        it("pStyle=3 取 max(step, flag) — 已完成任務用旗標值") {
            // mission 20: step=0, flag=1 → actual = max(0,1) = 1
            val s = PlayerEventState(
                missionSteps = emptyMap(),
                missionFlags = mapOf(20 to 1)
            )
            EveConditionEvaluator.evaluate(
                cond(cls = 2, param = 20, pStyle = 3, ops = 5, value = 1), s
            ) shouldBe true
        }
        it("pStyle=3 未開始任務 → actual=0") {
            val s = PlayerEventState()
            EveConditionEvaluator.evaluate(
                cond(cls = 2, param = 99, pStyle = 3, ops = 1, value = 0), s
            ) shouldBe true
        }
    }

    describe("class=7 通用屬性") {
        it("param=0 等級檢查") {
            val state = PlayerEventState(level = 10)
            EveConditionEvaluator.evaluate(
                cond(cls = 7, param = 0, ops = 5, value = 5), state
            ) shouldBe true
            EveConditionEvaluator.evaluate(
                cond(cls = 7, param = 0, ops = 5, value = 15), state
            ) shouldBe false
        }
        it("param=3 裝備檢查 — 有裝備") {
            val state = PlayerEventState(equipItems = setOf(500))
            EveConditionEvaluator.evaluate(
                cond(cls = 7, param = 3, ops = 5, value = 500), state
            ) shouldBe true
        }
        it("param=3 裝備檢查 — 無裝備") {
            val state = PlayerEventState(equipItems = emptySet())
            EveConditionEvaluator.evaluate(
                cond(cls = 7, param = 3, ops = 5, value = 500), state
            ) shouldBe false
        }
        it("param=2 pStyle=1 代表背包已滿，不可因轉生次數為 0 誤通過") {
            val state = PlayerEventState(rebornCount = 0, bagUsedSlots = 49, bagCapacity = 50)

            EveConditionEvaluator.evaluate(
                cond(cls = 7, param = 2, pStyle = 1, ops = 0, value = 0), state
            ) shouldBe false
        }
        it("param=2 pStyle=1 背包滿時通過") {
            val state = PlayerEventState(bagUsedSlots = 50, bagCapacity = 50)

            EveConditionEvaluator.evaluate(
                cond(cls = 7, param = 2, pStyle = 1, ops = 0, value = 0), state
            ) shouldBe true
        }
        it("param=2 pStyle=2 代表背包有空位") {
            val state = PlayerEventState(bagUsedSlots = 49, bagCapacity = 50)

            EveConditionEvaluator.evaluate(
                cond(cls = 7, param = 2, pStyle = 2, ops = 0, value = 0), state
            ) shouldBe true
            EveConditionEvaluator.evaluate(
                cond(cls = 7, param = 2, pStyle = 2, ops = 0, value = 0),
                state.copy(bagUsedSlots = 50)
            ) shouldBe false
        }
        it("param=2 pStyle=4 使用背包剩餘格數比較") {
            val state = PlayerEventState(bagUsedSlots = 47, bagCapacity = 50)

            EveConditionEvaluator.evaluate(
                cond(cls = 7, param = 2, pStyle = 4, ops = 5, value = 3), state
            ) shouldBe true
            EveConditionEvaluator.evaluate(
                cond(cls = 7, param = 2, pStyle = 4, ops = 5, value = 4), state
            ) shouldBe false
        }
        it("未知 param 即使 ops=0/value=0 也不可誤通過") {
            EveConditionEvaluator.evaluate(
                cond(cls = 7, param = 99, ops = 0, value = 0), emptyState
            ) shouldBe false
        }
        it("param=5 使用 pStyle 比對隨身武將空間") {
            val emptyPets = PlayerEventState(followPetCount = 0, maxFollowPets = 4)
            val fullPets = PlayerEventState(followPetCount = 4, maxFollowPets = 4)

            // pStyle=1: 武將已滿
            EveConditionEvaluator.evaluate(
                cond(cls = 7, param = 5, pStyle = 1, ops = 0, value = 0), fullPets
            ) shouldBe true
            EveConditionEvaluator.evaluate(
                cond(cls = 7, param = 5, pStyle = 1, ops = 0, value = 0), emptyPets
            ) shouldBe false

            // pStyle=2: 武將有空位
            EveConditionEvaluator.evaluate(
                cond(cls = 7, param = 5, pStyle = 2, ops = 0, value = 0), emptyPets
            ) shouldBe true
            EveConditionEvaluator.evaluate(
                cond(cls = 7, param = 5, pStyle = 2, ops = 0, value = 0), fullPets
            ) shouldBe false

            // pStyle=4: 比較剩餘格數
            EveConditionEvaluator.evaluate(
                cond(cls = 7, param = 5, pStyle = 4, ops = 5, value = 2),
                PlayerEventState(followPetCount = 2, maxFollowPets = 4)
            ) shouldBe true
        }
        it("param=16 subItem=1 代表騎乘中需要下馬") {
            val mounted = PlayerEventState(isMounted = true)
            val notMounted = PlayerEventState(isMounted = false)

            EveConditionEvaluator.evaluate(
                cond(cls = 7, param = 16, pStyle = 1, ops = 0, value = 0), mounted
            ) shouldBe false
            EveConditionEvaluator.evaluate(
                cond(cls = 7, param = 16, pStyle = 1, ops = 0, value = 0).copy(conditionSubItem = 1),
                mounted
            ) shouldBe true
            EveConditionEvaluator.evaluate(
                cond(cls = 7, param = 16, pStyle = 1, ops = 0, value = 0).copy(conditionSubItem = 1),
                notMounted
            ) shouldBe false
        }
        it("param=16 subItem=2 樣本為空結果條件，暫不當成下馬分支") {
            val mounted = PlayerEventState(isMounted = true)

            EveConditionEvaluator.evaluate(
                cond(cls = 7, param = 16, pStyle = 1, ops = 0, value = 0).copy(conditionSubItem = 2),
                mounted
            ) shouldBe false
        }
    }

    describe("class=8 戰鬥結果") {
        it("pStyle=1 勝利匹配") {
            val state = PlayerEventState(battleResult = 1)
            EveConditionEvaluator.evaluate(
                cond(cls = 8, pStyle = 1), state
            ) shouldBe true
        }
        it("pStyle=2 敗北不匹配勝利") {
            val state = PlayerEventState(battleResult = 1)
            EveConditionEvaluator.evaluate(
                cond(cls = 8, pStyle = 2), state
            ) shouldBe false
        }
    }

    describe("class=9 武將持有") {
        it("有武將 → true") {
            val state = PlayerEventState(followNpcIds = setOf(22081))
            EveConditionEvaluator.evaluate(
                cond(cls = 9, ops = 5, value = 22081), state
            ) shouldBe true
        }
        it("無武將 → false") {
            val state = PlayerEventState(followNpcIds = emptySet())
            EveConditionEvaluator.evaluate(
                cond(cls = 9, ops = 5, value = 22081), state
            ) shouldBe false
        }
        it("param 非 0 的進階武將條件不可用 value=0 誤通過") {
            val state = PlayerEventState(followNpcIds = setOf(15384))

            EveConditionEvaluator.evaluate(
                cond(cls = 9, param = 15384, pStyle = 2, ops = 1, value = 0), state
            ) shouldBe false
        }
    }

    describe("class=10 對話選擇") {
        it("surfaceId + code 都匹配") {
            val state = PlayerEventState(lastSurfaceId = 100, lastChoiceCode = 30)
            EveConditionEvaluator.evaluate(
                cond(cls = 10, param = 100, pStyle = 30), state
            ) shouldBe true
        }
        it("surfaceId 不匹配") {
            val state = PlayerEventState(lastSurfaceId = 999, lastChoiceCode = 30)
            EveConditionEvaluator.evaluate(
                cond(cls = 10, param = 100, pStyle = 30), state
            ) shouldBe false
        }
    }

    describe("class=11 收驚流程確認") {
        it("param=0 pStyle=1 ops=0 value=0 為已觀測的固定收驚確認條件") {
            EveConditionEvaluator.evaluate(
                cond(cls = 11, param = 0, pStyle = 1, ops = 0, value = 0), emptyState
            ) shouldBe true
        }
        it("其他 class=11 形狀保守不通過") {
            EveConditionEvaluator.evaluate(
                cond(cls = 11, param = 0, pStyle = 0, ops = 0, value = 0), emptyState
            ) shouldBe false
            EveConditionEvaluator.evaluate(
                cond(cls = 11, param = 1, pStyle = 1, ops = 0, value = 0), emptyState
            ) shouldBe false
        }
    }

    describe("class=12 事件完成計數（正向比較）") {
        it("val=1 ops=2(<) → 當前 eveNo 完成次數 < 1 才通過") {
            // eveNo=5 完成 0 次 → 0 < 1 = true
            val state0 = PlayerEventState(completedEveCounts = emptyMap(), currentEveNo = 5)
            EveConditionEvaluator.evaluate(
                cond(cls = 12, param = 0, ops = 2, value = 1), state0
            ) shouldBe true

            // eveNo=5 完成 1 次 → 1 < 1 = false
            val state1 = PlayerEventState(completedEveCounts = mapOf(5 to 1), currentEveNo = 5)
            EveConditionEvaluator.evaluate(
                cond(cls = 12, param = 0, ops = 2, value = 1), state1
            ) shouldBe false
        }

        it("val=2 ops=1(==) → 當前 eveNo 完成 2 次才通過") {
            val state2 = PlayerEventState(completedEveCounts = mapOf(1 to 2), currentEveNo = 1)
            EveConditionEvaluator.evaluate(
                cond(cls = 12, param = 0, ops = 1, value = 2), state2
            ) shouldBe true  // 2 == 2

            val state1 = PlayerEventState(completedEveCounts = mapOf(1 to 1), currentEveNo = 1)
            EveConditionEvaluator.evaluate(
                cond(cls = 12, param = 0, ops = 1, value = 2), state1
            ) shouldBe false  // 1 == 2 = false
        }

        it("未知 param 不可因 value=0/ops=0 誤通過") {
            EveConditionEvaluator.evaluate(
                cond(cls = 12, param = 3, ops = 0, value = 0), emptyState
            ) shouldBe false
        }
    }

    describe("class=15 玩家轉生條件") {
        it("param=0 讀取玩家轉生次數並套用 Eve 比較") {
            val notTurned = PlayerEventState(rebornCount = 0)
            val turned = PlayerEventState(rebornCount = 1)

            EveConditionEvaluator.evaluate(
                cond(cls = 15, param = 0, ops = 6, value = 0), notTurned
            ) shouldBe false
            EveConditionEvaluator.evaluate(
                cond(cls = 15, param = 0, ops = 6, value = 0), turned
            ) shouldBe true
            EveConditionEvaluator.evaluate(
                cond(cls = 15, param = 0, ops = 5, value = 0), notTurned
            ) shouldBe true
        }

        it("param=1 以武將二轉入口狀態碼判斷：0 可開啟，-1 顯示說明") {
            val canOpen = PlayerEventState(npcSecondReincarnationStatus = 0)
            val blocked = PlayerEventState(npcSecondReincarnationStatus = -1)

            EveConditionEvaluator.evaluate(
                cond(cls = 15, param = 1, ops = 6, value = 0),
                canOpen
            ) shouldBe false
            EveConditionEvaluator.evaluate(
                cond(cls = 15, param = 1, ops = 5, value = 0),
                canOpen
            ) shouldBe true
            EveConditionEvaluator.evaluate(
                cond(cls = 15, param = 1, ops = 6, value = 0),
                blocked
            ) shouldBe true
            EveConditionEvaluator.evaluate(
                cond(cls = 15, param = 1, ops = 5, value = 0),
                blocked
            ) shouldBe false
        }
    }

    describe("class=3 場景物件可互動狀態") {
        it("Eve 已觀測固定形狀 pStyle=4 ops=5 value=0 通過") {
            EveConditionEvaluator.evaluate(
                cond(cls = 3, param = 1, pStyle = 4, ops = 5, value = 0), emptyState
            ) shouldBe true
        }

        it("其他 class=3 形狀保守不通過") {
            EveConditionEvaluator.evaluate(
                cond(cls = 3, param = 1, pStyle = 3, ops = 5, value = 0), emptyState
            ) shouldBe false
            EveConditionEvaluator.evaluate(
                cond(cls = 3, param = 1, pStyle = 4, ops = 1, value = 0), emptyState
            ) shouldBe false
            EveConditionEvaluator.evaluate(
                cond(cls = 3, param = 1, pStyle = 4, ops = 5, value = 1), emptyState
            ) shouldBe false
        }
    }

    describe("class=4 技能條件") {
        it("會用技能等級比對 conditionValue") {
            val state = PlayerEventState(skillLevels = mapOf(14001 to 2))

            EveConditionEvaluator.evaluate(
                cond(cls = 4, param = 14001, ops = 5, value = 1), state
            ) shouldBe true
            EveConditionEvaluator.evaluate(
                cond(cls = 4, param = 14001, ops = 2, value = 1), state
            ) shouldBe false
        }

        it("未學技能等級視為 0") {
            val state = PlayerEventState(skillLevels = emptyMap())

            EveConditionEvaluator.evaluate(
                cond(cls = 4, param = 14001, ops = 1, value = 0), state
            ) shouldBe true
        }
    }

    describe("class=5 金錢條件") {
        it("ops=2 且 value=金額減一時代表金幣足夠") {
            val state = PlayerEventState(gold = 100)

            EveConditionEvaluator.evaluate(
                cond(cls = 5, pStyle = 1, ops = 2, value = 99), state
            ) shouldBe true
            EveConditionEvaluator.evaluate(
                cond(cls = 5, pStyle = 1, ops = 2, value = 99), state.copy(gold = 99)
            ) shouldBe false
        }

        it("ops=4 出現在扣款鏈時保守視為至少達門檻") {
            val state = PlayerEventState(gold = 50)

            EveConditionEvaluator.evaluate(
                cond(cls = 5, pStyle = 1, ops = 4, value = 50), state
            ) shouldBe true
            EveConditionEvaluator.evaluate(
                cond(cls = 5, pStyle = 1, ops = 4, value = 50), state.copy(gold = 49)
            ) shouldBe false
        }
    }

    describe("RoleCount Eve 條件分流") {
        it("class=18 且參數為已知 RoleCount 索引時會讀 roleCountValues") {
            val state = PlayerEventState(roleCountValues = mapOf(668 to 120))

            EveConditionEvaluator.evaluate(
                cond(cls = 18, param = 668, ops = 5, value = 100), state
            ) shouldBe true
        }

        it("class=18 param=1 桃園結義對話計數已鑑定為 RoleCount（resultClass=14 寫入端證據）") {
            val state = PlayerEventState(roleCountValues = mapOf(1 to 3))

            EveConditionEvaluator.evaluate(
                cond(cls = 18, param = 1, ops = 5, value = 3), state
            ) shouldBe true
        }

        it("class=18 未鑑定的低參數不會誤用 RoleCount") {
            val state = PlayerEventState(roleCountValues = mapOf(2 to 999))

            EveConditionEvaluator.evaluate(
                cond(cls = 18, param = 2, ops = 5, value = 1), state
            ) shouldBe false
        }

        it("class=14 不再標示為已確認 RoleCount") {
            val state = PlayerEventState(roleCountValues = mapOf(0 to 999))

            EveConditionEvaluator.evaluate(
                cond(cls = 14, param = 0, ops = 5, value = 1), state
            ) shouldBe false
        }
    }

    describe("未知 class") {
        it("保守回傳 false") {
            EveConditionEvaluator.evaluate(cond(cls = 99), emptyState) shouldBe false
        }
    }
})
