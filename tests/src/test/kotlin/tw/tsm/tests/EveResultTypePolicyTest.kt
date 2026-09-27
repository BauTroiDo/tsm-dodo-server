package tw.tsm.tests

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import tw.tsm.gamelogic.systems.EveResultTypePolicy

class EveResultTypePolicyTest : DescribeSpec({
    describe("Eve resultType 分流策略") {
        it("0 到 6 是客戶端 EventHandler.lua 已定義的 handler") {
            (0..6).map { EveResultTypePolicy.hasClientEventHandler(it) } shouldBe
                listOf(true, true, true, true, true, true, true)
        }

        it("7 是伺服器端特殊處理，不送客戶端 EventHandler") {
            EveResultTypePolicy.isServerOnly(7) shouldBe true
            EveResultTypePolicy.hasClientEventHandler(7) shouldBe false
        }

        it("8、9、10 沒有客戶端 handler，必須明確靜默跳過") {
            EveResultTypePolicy.shouldSkipWithoutClientHandler(8) shouldBe true
            EveResultTypePolicy.shouldSkipWithoutClientHandler(9) shouldBe true
            EveResultTypePolicy.shouldSkipWithoutClientHandler(10) shouldBe true
        }

        it("其他未知類型不是已確認 skip") {
            EveResultTypePolicy.shouldSkipWithoutClientHandler(11) shouldBe false
            EveResultTypePolicy.hasClientEventHandler(11) shouldBe false
            EveResultTypePolicy.isServerOnly(11) shouldBe false
        }
    }
})
