package tw.tsm.tests

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import tw.tsm.gamelogic.systems.EveMissionResultKind
import tw.tsm.gamelogic.systems.EveMoneyResultKind
import tw.tsm.gamelogic.systems.EvePlayerResultKind
import tw.tsm.gamelogic.systems.EveResultClassPolicy

class EveResultClassPolicyTest : DescribeSpec({
    describe("Eve resultClass 分流策略") {
        it("resultClass=10 pStyle=1/2 是明確 no-op marker") {
            EveResultClassPolicy.isExplicitNoOp(10, 1) shouldBe true
            EveResultClassPolicy.isExplicitNoOp(10, 2) shouldBe true
        }

        it("resultClass=10 其他 pStyle 不應被靜默吞掉") {
            EveResultClassPolicy.isExplicitNoOp(10, 0) shouldBe false
            EveResultClassPolicy.isExplicitNoOp(10, 3) shouldBe false
        }

        it("其他 resultClass 不是 no-op marker") {
            EveResultClassPolicy.isExplicitNoOp(1, 1) shouldBe false
            EveResultClassPolicy.isExplicitNoOp(2, 1) shouldBe false
            EveResultClassPolicy.isExplicitNoOp(7, 2) shouldBe false
        }

        it("resultClass=1 pStyle=0 使用 resultValue 正負號表示道具增減") {
            EveResultClassPolicy.itemDelta(0, 1) shouldBe 1
            EveResultClassPolicy.itemDelta(0, -1) shouldBe -1
        }

        it("resultClass=1 pStyle=1/2 轉成明確道具增減量") {
            EveResultClassPolicy.itemDelta(1, 3) shouldBe 3
            EveResultClassPolicy.itemDelta(1, -3) shouldBe -3
            EveResultClassPolicy.itemDelta(2, 3) shouldBe -3
            EveResultClassPolicy.itemDelta(2, -3) shouldBe -3
        }

        it("resultClass=1 未確認 pStyle 不應產生道具增減量") {
            EveResultClassPolicy.itemDelta(3, 1) shouldBe null
        }

        it("resultClass=2 pStyle=1/2/3 轉成任務步驟與完成操作") {
            EveResultClassPolicy.missionOperation(1, 2)?.kind shouldBe EveMissionResultKind.ADD_STEP
            EveResultClassPolicy.missionOperation(1, 2)?.value shouldBe 2
            EveResultClassPolicy.missionOperation(2, 2)?.kind shouldBe EveMissionResultKind.DEC_STEP
            EveResultClassPolicy.missionOperation(2, 2)?.value shouldBe 2
            EveResultClassPolicy.missionOperation(3, 0)?.kind shouldBe EveMissionResultKind.COMPLETE_MISSION
            // DVT MarkManager.AddMissionStep only changes mission progress; it must never be treated as completion.
            (EveResultClassPolicy.missionOperation(1, 1)?.kind == EveMissionResultKind.COMPLETE_MISSION) shouldBe false
        }

        it("resultClass=2 pStyle=4 使用 resultValue 作為直接旗標 bitId") {
            EveResultClassPolicy.missionOperation(4, 1001)?.kind shouldBe EveMissionResultKind.SET_FLAG
            EveResultClassPolicy.missionOperation(4, 1001)?.value shouldBe 1001
        }

        it("resultClass=2 pStyle=5 只移除任務不設定完成旗標") {
            EveResultClassPolicy.missionOperation(5, 0)?.kind shouldBe EveMissionResultKind.REMOVE_MISSION
            EveResultClassPolicy.missionOperation(5, 0)?.value shouldBe 0
        }

        it("resultClass=2 未確認 pStyle 不應產生任務操作") {
            EveResultClassPolicy.missionOperation(0, 11432) shouldBe null
        }

        it("resultClass=7 type=7 pStyle=1..5 是伺服器特殊 UI 流程") {
            EveResultClassPolicy.playerOperation(7, 1)?.kind shouldBe EvePlayerResultKind.OPEN_STORAGE
            EveResultClassPolicy.playerOperation(7, 2)?.kind shouldBe EvePlayerResultKind.OPEN_BANK
            EveResultClassPolicy.playerOperation(7, 3)?.kind shouldBe EvePlayerResultKind.OPEN_NPC_STORE
            EveResultClassPolicy.playerOperation(7, 4)?.kind shouldBe EvePlayerResultKind.OPEN_INN
            EveResultClassPolicy.playerOperation(7, 5)?.kind shouldBe EvePlayerResultKind.INN_REST_CONFIRM
        }

        it("resultClass=7 pStyle=1/3 帶值變體不得誤走服務 NPC UI") {
            EveResultClassPolicy.isRepeatableServicePlayerOperation(1, 0) shouldBe true
            EveResultClassPolicy.isRepeatableServicePlayerOperation(3, 0) shouldBe true
            EveResultClassPolicy.isRepeatableServicePlayerOperation(2, 100) shouldBe true
            EveResultClassPolicy.isRepeatableServicePlayerOperation(4, 100) shouldBe true
            EveResultClassPolicy.isRepeatableServicePlayerOperation(5, 100) shouldBe true

            // Eve.emg: pStyle=1 + value is 無界鬥陣 teleport;
            // pStyle=3 + value is premium-currency direct purchase.
            EveResultClassPolicy.isRepeatableServicePlayerOperation(1, 99) shouldBe false
            EveResultClassPolicy.isRepeatableServicePlayerOperation(1, 199) shouldBe false
            EveResultClassPolicy.isRepeatableServicePlayerOperation(3, 131171) shouldBe false
        }

        it("resultClass=7 type=0 pStyle=1..5 不應誤觸發特殊 UI") {
            EveResultClassPolicy.playerOperation(0, 1) shouldBe null
            EveResultClassPolicy.playerOperation(0, 2) shouldBe null
            EveResultClassPolicy.playerOperation(0, 3) shouldBe null
            EveResultClassPolicy.playerOperation(0, 4) shouldBe null
            EveResultClassPolicy.playerOperation(0, 5) shouldBe null
        }

        it("resultClass=7 pStyle=6 是金錢獎勵") {
            EveResultClassPolicy.playerOperation(0, 6)?.kind shouldBe EvePlayerResultKind.ADD_MONEY
            EveResultClassPolicy.playerOperation(7, 6)?.kind shouldBe EvePlayerResultKind.ADD_MONEY
        }

        it("resultClass=7 type=7 pStyle=12 是玩家轉生介面") {
            EveResultClassPolicy.playerOperation(7, 12)?.kind shouldBe EvePlayerResultKind.OPEN_PLAYER_REINCARNATION
        }

        it("resultClass=7 type=7 pStyle=13 是殺敵點兌換商店") {
            EveResultClassPolicy.playerOperation(7, 13)?.kind shouldBe EvePlayerResultKind.OPEN_KILL_POINT_SHOP
            EveResultClassPolicy.playerOperation(0, 13) shouldBe null
        }

        it("resultClass=7 type=7 pStyle=15 是水戰奉邑續行入口") {
            EveResultClassPolicy.playerOperation(7, 15)?.kind shouldBe EvePlayerResultKind.OPEN_WATER_WAR_ORG_MANAGE
            EveResultClassPolicy.playerOperation(0, 15) shouldBe null
        }

        it("resultClass=7 type=7 pStyle=21 是武將二轉介面") {
            EveResultClassPolicy.playerOperation(7, 21)?.kind shouldBe EvePlayerResultKind.OPEN_NPC_SECOND_REINCARNATION
        }

        it("resultClass=7 type=7 pStyle=27/29 是巴豆妖活動訊息") {
            EveResultClassPolicy.playerOperation(7, 27)?.kind shouldBe EvePlayerResultKind.SEND_BADO_ERROR_MESSAGE
            EveResultClassPolicy.playerOperation(7, 29)?.kind shouldBe EvePlayerResultKind.SEND_BADO_START_MESSAGE
            EveResultClassPolicy.playerOperation(8, 27) shouldBe null
            EveResultClassPolicy.playerOperation(10, 29) shouldBe null
        }

        it("resultClass=7 type=7 pStyle=10/30/32/35/39 是無 payload 活動入口") {
            EveResultClassPolicy.playerOperation(7, 10)?.kind shouldBe EvePlayerResultKind.OPEN_GALAXY_LEADERBOARD
            EveResultClassPolicy.playerOperation(7, 30)?.kind shouldBe EvePlayerResultKind.SEND_CHRISTMAS_EVENT_MESSAGE
            EveResultClassPolicy.playerOperation(7, 32)?.kind shouldBe EvePlayerResultKind.SEND_ARENA_ERROR_MESSAGE
            EveResultClassPolicy.playerOperation(7, 35)?.kind shouldBe EvePlayerResultKind.SEND_LANTERN_ERROR_MESSAGE
            EveResultClassPolicy.playerOperation(7, 39)?.kind shouldBe EvePlayerResultKind.SEND_UNKNOWN_ACTIVITY_MESSAGE
            EveResultClassPolicy.playerOperation(10, 30) shouldBe null
        }

        it("resultClass=5 pStyle=1/2 對應金錢增減") {
            EveResultClassPolicy.moneyOperation(1, 100)?.kind shouldBe EveMoneyResultKind.ADD
            EveResultClassPolicy.moneyOperation(1, 100)?.amount shouldBe 100
            EveResultClassPolicy.moneyOperation(2, 100)?.kind shouldBe EveMoneyResultKind.SUBTRACT
            EveResultClassPolicy.moneyOperation(2, 100)?.amount shouldBe 100
        }

        it("resultClass=5 不接受未知 pStyle 或非正數金額") {
            EveResultClassPolicy.moneyOperation(3, 100) shouldBe null
            EveResultClassPolicy.moneyOperation(1, 0) shouldBe null
            EveResultClassPolicy.moneyOperation(2, -100) shouldBe null
        }
    }
})
