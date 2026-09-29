package tw.tsm.tests

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import tw.tsm.gamelogic.systems.EveResultTypePolicy

class EveResultTypePolicyTest : DescribeSpec({
    describe("Chiến lược phân luồng resultType của Eve") {
        it("0 đến 6 là handler đã định nghĩa trong EventHandler.lua phía client") {
            (0..6).map { EveResultTypePolicy.hasClientEventHandler(it) } shouldBe
                listOf(true, true, true, true, true, true, true)
        }

        it("7 được xử lý đặc biệt phía server, không gửi cho EventHandler phía client") {
            EveResultTypePolicy.isServerOnly(7) shouldBe true
            EveResultTypePolicy.hasClientEventHandler(7) shouldBe false
        }

        it("8, 9, 10 không có handler phía client, phải âm thầm bỏ qua một cách rõ ràng") {
            EveResultTypePolicy.shouldSkipWithoutClientHandler(8) shouldBe true
            EveResultTypePolicy.shouldSkipWithoutClientHandler(9) shouldBe true
            EveResultTypePolicy.shouldSkipWithoutClientHandler(10) shouldBe true
        }

        it("các loại không xác định khác không phải skip đã xác nhận") {
            EveResultTypePolicy.shouldSkipWithoutClientHandler(11) shouldBe false
            EveResultTypePolicy.hasClientEventHandler(11) shouldBe false
            EveResultTypePolicy.isServerOnly(11) shouldBe false
        }
    }
})
