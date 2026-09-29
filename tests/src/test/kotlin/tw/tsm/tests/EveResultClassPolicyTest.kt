package tw.tsm.tests

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import tw.tsm.gamelogic.systems.EveMissionResultKind
import tw.tsm.gamelogic.systems.EveMoneyResultKind
import tw.tsm.gamelogic.systems.EvePlayerResultKind
import tw.tsm.gamelogic.systems.EveResultClassPolicy

class EveResultClassPolicyTest : DescribeSpec({
    describe("Chiến lược phân luồng resultClass của Eve") {
        it("resultClass=10 pStyle=1/2 là marker no-op rõ ràng") {
            EveResultClassPolicy.isExplicitNoOp(10, 1) shouldBe true
            EveResultClassPolicy.isExplicitNoOp(10, 2) shouldBe true
        }

        it("resultClass=10 với pStyle khác không nên bị âm thầm nuốt bỏ") {
            EveResultClassPolicy.isExplicitNoOp(10, 0) shouldBe false
            EveResultClassPolicy.isExplicitNoOp(10, 3) shouldBe false
        }

        it("các resultClass khác không phải marker no-op") {
            EveResultClassPolicy.isExplicitNoOp(1, 1) shouldBe false
            EveResultClassPolicy.isExplicitNoOp(2, 1) shouldBe false
            EveResultClassPolicy.isExplicitNoOp(7, 2) shouldBe false
        }

        it("resultClass=1 pStyle=0 dùng dấu của resultValue để biểu thị thêm/bớt vật phẩm") {
            EveResultClassPolicy.itemDelta(0, 1) shouldBe 1
            EveResultClassPolicy.itemDelta(0, -1) shouldBe -1
        }

        it("resultClass=1 pStyle=1/2 chuyển thành lượng thêm/bớt vật phẩm rõ ràng") {
            EveResultClassPolicy.itemDelta(1, 3) shouldBe 3
            EveResultClassPolicy.itemDelta(1, -3) shouldBe -3
            EveResultClassPolicy.itemDelta(2, 3) shouldBe -3
            EveResultClassPolicy.itemDelta(2, -3) shouldBe -3
        }

        it("resultClass=1 với pStyle chưa xác nhận không được sinh lượng thêm/bớt vật phẩm") {
            EveResultClassPolicy.itemDelta(3, 1) shouldBe null
        }

        it("resultClass=2 pStyle=1/2/3 chuyển thành bước nhiệm vụ và thao tác hoàn thành") {
            EveResultClassPolicy.missionOperation(1, 2)?.kind shouldBe EveMissionResultKind.ADD_STEP
            EveResultClassPolicy.missionOperation(1, 2)?.value shouldBe 2
            EveResultClassPolicy.missionOperation(2, 2)?.kind shouldBe EveMissionResultKind.DEC_STEP
            EveResultClassPolicy.missionOperation(2, 2)?.value shouldBe 2
            EveResultClassPolicy.missionOperation(3, 0)?.kind shouldBe EveMissionResultKind.COMPLETE_MISSION
            // DVT MarkManager.AddMissionStep only changes mission progress; it must never be treated as completion.
            (EveResultClassPolicy.missionOperation(1, 1)?.kind == EveMissionResultKind.COMPLETE_MISSION) shouldBe false
        }

        it("resultClass=2 pStyle=4 dùng resultValue làm bitId cờ trực tiếp") {
            EveResultClassPolicy.missionOperation(4, 1001)?.kind shouldBe EveMissionResultKind.SET_FLAG
            EveResultClassPolicy.missionOperation(4, 1001)?.value shouldBe 1001
        }

        it("resultClass=2 pStyle=5 chỉ xóa nhiệm vụ, không đặt cờ hoàn thành") {
            EveResultClassPolicy.missionOperation(5, 0)?.kind shouldBe EveMissionResultKind.REMOVE_MISSION
            EveResultClassPolicy.missionOperation(5, 0)?.value shouldBe 0
        }

        it("resultClass=2 với pStyle chưa xác nhận không được sinh thao tác nhiệm vụ") {
            EveResultClassPolicy.missionOperation(0, 11432) shouldBe null
        }

        it("resultClass=7 type=7 pStyle=1..5 là luồng UI đặc biệt phía server") {
            EveResultClassPolicy.playerOperation(7, 1)?.kind shouldBe EvePlayerResultKind.OPEN_STORAGE
            EveResultClassPolicy.playerOperation(7, 2)?.kind shouldBe EvePlayerResultKind.OPEN_BANK
            EveResultClassPolicy.playerOperation(7, 3)?.kind shouldBe EvePlayerResultKind.OPEN_NPC_STORE
            EveResultClassPolicy.playerOperation(7, 4)?.kind shouldBe EvePlayerResultKind.OPEN_INN
            EveResultClassPolicy.playerOperation(7, 5)?.kind shouldBe EvePlayerResultKind.INN_REST_CONFIRM
        }

        it("resultClass=7 pStyle=1/3 biến thể có giá trị không được nhầm sang NPC UI phục vụ") {
            EveResultClassPolicy.isRepeatableServicePlayerOperation(1, 0) shouldBe true
            EveResultClassPolicy.isRepeatableServicePlayerOperation(3, 0) shouldBe true
            EveResultClassPolicy.isRepeatableServicePlayerOperation(2, 100) shouldBe true
            EveResultClassPolicy.isRepeatableServicePlayerOperation(4, 100) shouldBe true
            EveResultClassPolicy.isRepeatableServicePlayerOperation(5, 100) shouldBe true

            // Eve.emg: pStyle=1 + value is teleport vô giới đấu trận;
            // pStyle=3 + value is premium-currency direct purchase.
            EveResultClassPolicy.isRepeatableServicePlayerOperation(1, 99) shouldBe false
            EveResultClassPolicy.isRepeatableServicePlayerOperation(1, 199) shouldBe false
            EveResultClassPolicy.isRepeatableServicePlayerOperation(3, 131171) shouldBe false
        }

        it("resultClass=7 pStyle=3 giải mã đúng direct Point purchase đã chứng minh") {
            EveResultClassPolicy.directPointPurchase(3, 0, 131171, 10) shouldBe
                tw.tsm.gamelogic.systems.EvePointPurchase(itemId = 31171, quantity = 1, price = 10)
            EveResultClassPolicy.directPointPurchase(3, 0, 531171, 50) shouldBe
                tw.tsm.gamelogic.systems.EvePointPurchase(itemId = 31171, quantity = 5, price = 50)
            EveResultClassPolicy.directPointPurchase(3, 0, 1031171, 100) shouldBe
                tw.tsm.gamelogic.systems.EvePointPurchase(itemId = 31171, quantity = 10, price = 100)
        }

        it("direct Point purchase không được nuốt các pStyle=3 semantics khác") {
            EveResultClassPolicy.directPointPurchase(3, 1, 1, 0) shouldBe null
            EveResultClassPolicy.directPointPurchase(3, 2, 5000, 0) shouldBe null
            EveResultClassPolicy.directPointPurchase(3, 0, 131171, 0) shouldBe null
            EveResultClassPolicy.directPointPurchase(2, 0, 131171, 10) shouldBe null
        }

        it("resultClass=7 type=0 pStyle=1..5 không nên nhầm kích hoạt UI đặc biệt") {
            EveResultClassPolicy.playerOperation(0, 1) shouldBe null
            EveResultClassPolicy.playerOperation(0, 2) shouldBe null
            EveResultClassPolicy.playerOperation(0, 3) shouldBe null
            EveResultClassPolicy.playerOperation(0, 4) shouldBe null
            EveResultClassPolicy.playerOperation(0, 5) shouldBe null
        }

        it("resultClass=7 pStyle=6 là thưởng tiền") {
            EveResultClassPolicy.playerOperation(0, 6)?.kind shouldBe EvePlayerResultKind.ADD_MONEY
            EveResultClassPolicy.playerOperation(7, 6)?.kind shouldBe EvePlayerResultKind.ADD_MONEY
        }

        it("resultClass=7 type=7 pStyle=12 là giao diện chuyển sinh người chơi") {
            EveResultClassPolicy.playerOperation(7, 12)?.kind shouldBe EvePlayerResultKind.OPEN_PLAYER_REINCARNATION
        }

        it("resultClass=7 type=7 pStyle=13 là cửa hàng đổi điểm diệt địch") {
            EveResultClassPolicy.playerOperation(7, 13)?.kind shouldBe EvePlayerResultKind.OPEN_KILL_POINT_SHOP
            EveResultClassPolicy.playerOperation(0, 13) shouldBe null
        }

        it("resultClass=7 type=7 pStyle=15 là cửa tiếp tục Phụng Ấp thủy chiến") {
            EveResultClassPolicy.playerOperation(7, 15)?.kind shouldBe EvePlayerResultKind.OPEN_WATER_WAR_ORG_MANAGE
            EveResultClassPolicy.playerOperation(0, 15) shouldBe null
        }

        it("resultClass=7 type=7 pStyle=21 là giao diện nhị chuyển sinh tướng") {
            EveResultClassPolicy.playerOperation(7, 21)?.kind shouldBe EvePlayerResultKind.OPEN_NPC_SECOND_REINCARNATION
        }

        it("resultClass=7 type=7 pStyle=27/29 là thông báo hoạt động Ba Đậu Yêu") {
            EveResultClassPolicy.playerOperation(7, 27)?.kind shouldBe EvePlayerResultKind.SEND_BADO_ERROR_MESSAGE
            EveResultClassPolicy.playerOperation(7, 29)?.kind shouldBe EvePlayerResultKind.SEND_BADO_START_MESSAGE
            EveResultClassPolicy.playerOperation(8, 27) shouldBe null
            EveResultClassPolicy.playerOperation(10, 29) shouldBe null
        }

        it("resultClass=7 type=7 pStyle=10/30/32/35/39 là cửa hoạt động không payload") {
            EveResultClassPolicy.playerOperation(7, 10)?.kind shouldBe EvePlayerResultKind.OPEN_GALAXY_LEADERBOARD
            EveResultClassPolicy.playerOperation(7, 30)?.kind shouldBe EvePlayerResultKind.SEND_CHRISTMAS_EVENT_MESSAGE
            EveResultClassPolicy.playerOperation(7, 32)?.kind shouldBe EvePlayerResultKind.SEND_ARENA_ERROR_MESSAGE
            EveResultClassPolicy.playerOperation(7, 35)?.kind shouldBe EvePlayerResultKind.SEND_LANTERN_ERROR_MESSAGE
            EveResultClassPolicy.playerOperation(7, 39)?.kind shouldBe EvePlayerResultKind.SEND_UNKNOWN_ACTIVITY_MESSAGE
            EveResultClassPolicy.playerOperation(10, 30) shouldBe null
        }

        it("resultClass=5 pStyle=1/2 tương ứng thêm/bớt tiền") {
            EveResultClassPolicy.moneyOperation(1, 100)?.kind shouldBe EveMoneyResultKind.ADD
            EveResultClassPolicy.moneyOperation(1, 100)?.amount shouldBe 100
            EveResultClassPolicy.moneyOperation(2, 100)?.kind shouldBe EveMoneyResultKind.SUBTRACT
            EveResultClassPolicy.moneyOperation(2, 100)?.amount shouldBe 100
        }

        it("resultClass=5 không nhận pStyle không xác định hoặc số tiền không dương") {
            EveResultClassPolicy.moneyOperation(3, 100) shouldBe null
            EveResultClassPolicy.moneyOperation(1, 0) shouldBe null
            EveResultClassPolicy.moneyOperation(2, -100) shouldBe null
        }
    }
})
