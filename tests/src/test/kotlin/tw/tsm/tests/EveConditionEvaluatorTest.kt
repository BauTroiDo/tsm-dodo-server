package tw.tsm.tests

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import tw.tsm.domain.data.EveCondition
import tw.tsm.gamelogic.systems.EveConditionEvaluator
import tw.tsm.gamelogic.systems.PlayerEventState

/**
 * Kiểm thử đơn vị EveConditionEvaluator
 *
 * Bao gồm: 7 ops của compareStep + 9 conditionClass
 */
class EveConditionEvaluatorTest : DescribeSpec({

    // ── Hàm phụ trợ ──

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
    //  compareStep — 7 ops
    // ══════════════════════════════════════════════

    describe("compareStep") {
        it("ops=0 bằng") {
            EveConditionEvaluator.compareStep(5, 0, 5) shouldBe true
            EveConditionEvaluator.compareStep(5, 0, 3) shouldBe false
        }
        it("ops=1 bằng") {
            EveConditionEvaluator.compareStep(5, 1, 5) shouldBe true
            EveConditionEvaluator.compareStep(5, 1, 3) shouldBe false
        }
        it("ops=2 nhỏ hơn") {
            EveConditionEvaluator.compareStep(3, 2, 5) shouldBe true
            EveConditionEvaluator.compareStep(5, 2, 5) shouldBe false
        }
        it("ops=3 nhỏ hơn hoặc bằng") {
            EveConditionEvaluator.compareStep(5, 3, 5) shouldBe true
            EveConditionEvaluator.compareStep(6, 3, 5) shouldBe false
        }
        it("ops=4 lớn hơn") {
            EveConditionEvaluator.compareStep(6, 4, 5) shouldBe true
            EveConditionEvaluator.compareStep(5, 4, 5) shouldBe false
        }
        it("ops=5 lớn hơn hoặc bằng") {
            EveConditionEvaluator.compareStep(5, 5, 5) shouldBe true
            EveConditionEvaluator.compareStep(4, 5, 5) shouldBe false
        }
        it("ops=6 khác") {
            EveConditionEvaluator.compareStep(4, 6, 5) shouldBe true
            EveConditionEvaluator.compareStep(5, 6, 5) shouldBe false
        }
    }

    // ══════════════════════════════════════════════
    //  conditionClass
    // ══════════════════════════════════════════════

    describe("class=0 không điều kiện") {
        it("luôn luôn true") {
            EveConditionEvaluator.evaluate(cond(cls = 0), emptyState) shouldBe true
        }
    }

    describe("class=1 sở hữu vật phẩm (so sánh ngược)") {
        val state = PlayerEventState(bagItems = mapOf(100 to 3))

        it("val=0 ops=2 → có vật phẩm này (0 < 3)") {
            EveConditionEvaluator.evaluate(
                cond(cls = 1, param = 100, ops = 2, value = 0), state
            ) shouldBe true
        }
        it("val=0 ops=5 → không có vật phẩm này (0 >= 3 = false)") {
            EveConditionEvaluator.evaluate(
                cond(cls = 1, param = 100, ops = 5, value = 0), state
            ) shouldBe false
        }
        it("vật phẩm không có, val=0 ops=5 → true (0 >= 0)") {
            EveConditionEvaluator.evaluate(
                cond(cls = 1, param = 999, ops = 5, value = 0), state
            ) shouldBe true
        }
    }

    describe("class=16 vật phẩm kho ngoài túi đồ") {
        it("chỉ kiểm tra tổng số lượng Tiền trang/kho báu, không dùng lại số lượng túi đồ") {
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

    describe("class=2 bước nhiệm vụ dùng comparator Eve") {
        it("mission11120 step1 ops2 val0 nghĩa là step > 0") {
            val state = PlayerEventState(missionSteps = mapOf(11120 to 1))
            EveConditionEvaluator.evaluate(
                cond(cls = 2, param = 11120, pStyle = 1, ops = 2, value = 0), state
            ) shouldBe true
        }

        it("ops5 là exact step, không phải >=") {
            val state = PlayerEventState(missionSteps = mapOf(10 to 2))
            EveConditionEvaluator.evaluate(
                cond(cls = 2, param = 10, pStyle = 1, ops = 5, value = 2), state
            ) shouldBe true
            EveConditionEvaluator.evaluate(
                cond(cls = 2, param = 10, pStyle = 1, ops = 5, value = 1), state
            ) shouldBe false
        }

        it("pStyle=3 chỉ đọc completion flag, không bị active step làm nhiễu") {
            val active = PlayerEventState(
                missionSteps = mapOf(20 to 6),
                missionFlags = emptyMap()
            )
            EveConditionEvaluator.evaluate(
                cond(cls = 2, param = 20, pStyle = 3, ops = 5, value = 0), active
            ) shouldBe true

            val completed = PlayerEventState(
                missionSteps = emptyMap(),
                missionFlags = mapOf(20 to 1)
            )
            EveConditionEvaluator.evaluate(
                cond(cls = 2, param = 20, pStyle = 3, ops = 5, value = 1), completed
            ) shouldBe true
            EveConditionEvaluator.evaluate(
                cond(cls = 2, param = 20, pStyle = 3, ops = 5, value = 0), completed
            ) shouldBe false
        }

        it("mission comparator đủ họ < > <= >= != và ==") {
            EveConditionEvaluator.compareMissionStep(4, 1, 5) shouldBe true
            EveConditionEvaluator.compareMissionStep(6, 2, 5) shouldBe true
            EveConditionEvaluator.compareMissionStep(5, 3, 5) shouldBe true
            EveConditionEvaluator.compareMissionStep(5, 4, 5) shouldBe true
            EveConditionEvaluator.compareMissionStep(5, 5, 5) shouldBe true
            EveConditionEvaluator.compareMissionStep(4, 6, 5) shouldBe true
        }
    }

    describe("class=7 thuộc tính chung") {
        it("param=0 kiểm tra cấp") {
            val state = PlayerEventState(level = 10)
            EveConditionEvaluator.evaluate(
                cond(cls = 7, param = 0, pStyle = 1, ops = 5, value = 5), state
            ) shouldBe true
            EveConditionEvaluator.evaluate(
                cond(cls = 7, param = 0, pStyle = 1, ops = 5, value = 15), state
            ) shouldBe false
        }

        it("param=0 dùng comparator level của Eve cho nhánh Hương Dũng") {
            val lv39 = PlayerEventState(level = 39)

            // scene 12705: event 6 catch-up branch requires level > 14
            EveConditionEvaluator.evaluate(
                cond(cls = 7, param = 0, pStyle = 1, ops = 2, value = 14), lv39
            ) shouldBe true

            // lower-level branch uses level < 15 and must not steal Lv39
            EveConditionEvaluator.evaluate(
                cond(cls = 7, param = 0, pStyle = 1, ops = 1, value = 15), lv39
            ) shouldBe false
        }

        it("param=0 pStyle2 là hệ nhân vật, không phải level") {
            val earth = PlayerEventState(level = 99, element = 1)
            val water = PlayerEventState(level = 99, element = 2)

            // DVT scene11525/12064 dùng ops5 + value1..4 làm bốn nhánh hệ riêng.
            EveConditionEvaluator.evaluate(
                cond(cls = 7, param = 0, pStyle = 2, ops = 5, value = 1), earth
            ) shouldBe true
            EveConditionEvaluator.evaluate(
                cond(cls = 7, param = 0, pStyle = 2, ops = 5, value = 2), earth
            ) shouldBe false
            EveConditionEvaluator.evaluate(
                cond(cls = 7, param = 0, pStyle = 2, ops = 5, value = 2), water
            ) shouldBe true
        }

        it("param=0 pStyle3 là NPC ID biến hình hiện tại") {
            val transformed = PlayerEventState(level = 999, transformNpcId = 11050)
            val normal = PlayerEventState(level = 11050, transformNpcId = 0)

            // DVT S:005-005 đặt Role.player.npcId; Eve scene12093 kiểm tra Hứa Chử 11050.
            EveConditionEvaluator.evaluate(
                cond(cls = 7, param = 0, pStyle = 3, ops = 5, value = 11050), transformed
            ) shouldBe true
            EveConditionEvaluator.evaluate(
                cond(cls = 7, param = 0, pStyle = 3, ops = 6, value = 11050), transformed
            ) shouldBe false

            // Level có trùng NPC ID cũng không được làm điều kiện biến hình pass.
            EveConditionEvaluator.evaluate(
                cond(cls = 7, param = 0, pStyle = 3, ops = 5, value = 11050), normal
            ) shouldBe false
            EveConditionEvaluator.evaluate(
                cond(cls = 7, param = 0, pStyle = 3, ops = 6, value = 11050), normal
            ) shouldBe true
        }
        it("param=7 pStyle=1/2 phân nhánh giới tính nam nữ") {
            val male = PlayerEventState(sex = 1)
            val female = PlayerEventState(sex = 2)

            EveConditionEvaluator.evaluate(
                cond(cls = 7, param = 7, pStyle = 1, ops = 0, value = 0), male
            ) shouldBe true
            EveConditionEvaluator.evaluate(
                cond(cls = 7, param = 7, pStyle = 2, ops = 0, value = 0), male
            ) shouldBe false
            EveConditionEvaluator.evaluate(
                cond(cls = 7, param = 7, pStyle = 2, ops = 0, value = 0), female
            ) shouldBe true
        }

        it("param=4 pStyle3 kiểm tra item không nằm trong Tiền Trang/Bảo Khố") {
            val absent = PlayerEventState(storedItems = emptyMap())
            val stored = PlayerEventState(storedItems = mapOf(23177 to 1))
            val c = cond(cls = 7, param = 4, pStyle = 3, ops = 6, value = 0)
                .copy(conditionSubItem = 23177)

            EveConditionEvaluator.evaluate(c, absent) shouldBe true
            EveConditionEvaluator.evaluate(c, stored) shouldBe false
        }

        it("param=9 pStyle1..4 phân nhánh mốc chuyển sinh DVT") {
            val turn0 = PlayerEventState(rebornCount = 0)
            val turn1 = PlayerEventState(rebornCount = 1)
            val turn2 = PlayerEventState(rebornCount = 2)
            val turn3 = PlayerEventState(rebornCount = 3)

            // pStyle1/2: đã/chưa Nhất Chuyển
            EveConditionEvaluator.evaluate(
                cond(cls = 7, param = 9, pStyle = 1, ops = 0, value = 0), turn0
            ) shouldBe false
            EveConditionEvaluator.evaluate(
                cond(cls = 7, param = 9, pStyle = 2, ops = 0, value = 0), turn0
            ) shouldBe true
            EveConditionEvaluator.evaluate(
                cond(cls = 7, param = 9, pStyle = 1, ops = 0, value = 0), turn1
            ) shouldBe true

            // pStyle3/4: đã/chưa Nhị Chuyển. Turn3 vẫn đã qua Nhị Chuyển.
            EveConditionEvaluator.evaluate(
                cond(cls = 7, param = 9, pStyle = 3, ops = 0, value = 0), turn1
            ) shouldBe false
            EveConditionEvaluator.evaluate(
                cond(cls = 7, param = 9, pStyle = 4, ops = 0, value = 0), turn1
            ) shouldBe true
            EveConditionEvaluator.evaluate(
                cond(cls = 7, param = 9, pStyle = 3, ops = 0, value = 0), turn2
            ) shouldBe true
            EveConditionEvaluator.evaluate(
                cond(cls = 7, param = 9, pStyle = 4, ops = 0, value = 0), turn2
            ) shouldBe false
            EveConditionEvaluator.evaluate(
                cond(cls = 7, param = 9, pStyle = 3, ops = 0, value = 0), turn3
            ) shouldBe true
            EveConditionEvaluator.evaluate(
                cond(cls = 7, param = 9, pStyle = 4, ops = 0, value = 0), turn3
            ) shouldBe false
        }

        it("param=11 pStyle1 2 phân nhánh đủ 12 Thiên Châu Nhị Chuyển") {
            val eleven = PlayerEventState(activeTurn2Balls = (1..11).toSet())
            val complete = PlayerEventState(activeTurn2Balls = (1..12).toSet())
            val noisy = PlayerEventState(activeTurn2Balls = (1..11).toSet() + 99)

            EveConditionEvaluator.evaluate(
                cond(cls = 7, param = 11, pStyle = 1, ops = 0, value = 0), eleven
            ) shouldBe false
            EveConditionEvaluator.evaluate(
                cond(cls = 7, param = 11, pStyle = 2, ops = 0, value = 0), eleven
            ) shouldBe true

            EveConditionEvaluator.evaluate(
                cond(cls = 7, param = 11, pStyle = 1, ops = 0, value = 0), complete
            ) shouldBe true
            EveConditionEvaluator.evaluate(
                cond(cls = 7, param = 11, pStyle = 2, ops = 0, value = 0), complete
            ) shouldBe false

            EveConditionEvaluator.evaluate(
                cond(cls = 7, param = 11, pStyle = 1, ops = 0, value = 0), noisy
            ) shouldBe false
        }

        it("param=14 pStyle1..4 phân nhánh nghề chuyển sinh") {
            val ghost = PlayerEventState(career = 3)
            EveConditionEvaluator.evaluate(
                cond(cls = 7, param = 14, pStyle = 3, ops = 0, value = 0), ghost
            ) shouldBe true
            EveConditionEvaluator.evaluate(
                cond(cls = 7, param = 14, pStyle = 1, ops = 0, value = 0), ghost
            ) shouldBe false
        }

        it("param=10 pStyle=1 dùng Honor bằng KillNum với comparator Eve") {
            val before = PlayerEventState(killNum = 49)
            val reached = PlayerEventState(killNum = 50)

            EveConditionEvaluator.evaluate(
                cond(cls = 7, param = 10, pStyle = 1, ops = 1, value = 50), before
            ) shouldBe true
            EveConditionEvaluator.evaluate(
                cond(cls = 7, param = 10, pStyle = 1, ops = 2, value = 49), before
            ) shouldBe false

            EveConditionEvaluator.evaluate(
                cond(cls = 7, param = 10, pStyle = 1, ops = 1, value = 50), reached
            ) shouldBe false
            EveConditionEvaluator.evaluate(
                cond(cls = 7, param = 10, pStyle = 1, ops = 2, value = 49), reached
            ) shouldBe true
        }

        it("param=10 chỉ chấp nhận shape pStyle=1/subItem=0 đã xác minh") {
            val state = PlayerEventState(killNum = 1000)
            EveConditionEvaluator.evaluate(
                cond(cls = 7, param = 10, pStyle = 2, ops = 2, value = 49), state
            ) shouldBe false
            EveConditionEvaluator.evaluate(
                cond(cls = 7, param = 10, pStyle = 1, ops = 2, value = 49)
                    .copy(conditionSubItem = 1), state
            ) shouldBe false
        }

        it("param=1 pStyle=6 phân nhánh phe thường và Khăn Vàng/Hắc Sơn") {
            val normal = PlayerEventState(storyFaction = 1)
            val yellowTurban = PlayerEventState(storyFaction = 2)

            // DVT Eve 12000/eve13: phe thường dùng ops=6, value=2.
            EveConditionEvaluator.evaluate(
                cond(cls = 7, param = 1, pStyle = 6, ops = 6, value = 2), normal
            ) shouldBe true
            EveConditionEvaluator.evaluate(
                cond(cls = 7, param = 1, pStyle = 6, ops = 5, value = 2), normal
            ) shouldBe false

            // Nhánh Khăn Vàng/Hắc Sơn dùng >= 2.
            EveConditionEvaluator.evaluate(
                cond(cls = 7, param = 1, pStyle = 6, ops = 5, value = 2), yellowTurban
            ) shouldBe true
            EveConditionEvaluator.evaluate(
                cond(cls = 7, param = 1, pStyle = 6, ops = 6, value = 2), yellowTurban
            ) shouldBe false
        }

        it("param=1 pStyle=2..5 đọc đúng Moral Khăn Vàng-Ngô-Thục-Ngụy") {
            val state = PlayerEventState(
                moral = intArrayOf(
                    50,   // 1 Ngụy  <- pStyle 5
                    800,  // 2 Thục  <- pStyle 4
                    2000, // 3 Ngô   <- pStyle 3
                    300,  // 4 Khăn Vàng <- pStyle 2
                    10    // 5 Du Hiệp <- pStyle 1
                )
            )

            EveConditionEvaluator.evaluate(
                cond(cls = 7, param = 1, pStyle = 5, ops = 5, value = 50), state
            ) shouldBe true
            EveConditionEvaluator.evaluate(
                cond(cls = 7, param = 1, pStyle = 4, ops = 5, value = 800), state
            ) shouldBe true
            EveConditionEvaluator.evaluate(
                cond(cls = 7, param = 1, pStyle = 3, ops = 5, value = 2000), state
            ) shouldBe true
            EveConditionEvaluator.evaluate(
                cond(cls = 7, param = 1, pStyle = 2, ops = 5, value = 300), state
            ) shouldBe true
            EveConditionEvaluator.evaluate(
                cond(cls = 7, param = 1, pStyle = 1, ops = 5, value = 10), state
            ) shouldBe true
        }

        it("Moral dùng comparator Eve riêng cho các dải thanh danh") {
            val wu200 = PlayerEventState(moral = intArrayOf(0, 0, 200, 0, 0))

            // Dải 200..799: >199 và <800.
            EveConditionEvaluator.evaluate(
                cond(cls = 7, param = 1, pStyle = 3, ops = 2, value = 199), wu200
            ) shouldBe true
            EveConditionEvaluator.evaluate(
                cond(cls = 7, param = 1, pStyle = 3, ops = 1, value = 800), wu200
            ) shouldBe true

            // ops=5 là equality trong họ Moral; ops=6 là !=.
            EveConditionEvaluator.evaluate(
                cond(cls = 7, param = 1, pStyle = 3, ops = 5, value = 200), wu200
            ) shouldBe true
            EveConditionEvaluator.evaluate(
                cond(cls = 7, param = 1, pStyle = 3, ops = 6, value = 201), wu200
            ) shouldBe true
        }

        it("param=1 Moral và storyFaction đều từ chối subItem lạ") {
            val state = PlayerEventState(storyFaction = 1, moral = intArrayOf(50, 0, 0, 0, 0))
            EveConditionEvaluator.evaluate(
                cond(cls = 7, param = 1, pStyle = 5, ops = 5, value = 50)
                    .copy(conditionSubItem = 1), state
            ) shouldBe false
            EveConditionEvaluator.evaluate(
                cond(cls = 7, param = 1, pStyle = 6, ops = 6, value = 2)
                    .copy(conditionSubItem = 1), state
            ) shouldBe false
        }

        it("param=3 kiểm tra trang bị — có trang bị") {
            val state = PlayerEventState(equipItems = setOf(500))
            EveConditionEvaluator.evaluate(
                cond(cls = 7, param = 3, ops = 5, value = 500), state
            ) shouldBe true
        }
        it("param=3 kiểm tra trang bị — không trang bị") {
            val state = PlayerEventState(equipItems = emptySet())
            EveConditionEvaluator.evaluate(
                cond(cls = 7, param = 3, ops = 5, value = 500), state
            ) shouldBe false
        }
        it("param=2 pStyle=1 nghĩa là túi đồ đã đầy, không được nhầm qua vì số lần chuyển sinh = 0") {
            val state = PlayerEventState(rebornCount = 0, bagUsedSlots = 49, bagCapacity = 50)

            EveConditionEvaluator.evaluate(
                cond(cls = 7, param = 2, pStyle = 1, ops = 0, value = 0), state
            ) shouldBe false
        }
        it("param=2 pStyle=1 qua khi túi đầy") {
            val state = PlayerEventState(bagUsedSlots = 50, bagCapacity = 50)

            EveConditionEvaluator.evaluate(
                cond(cls = 7, param = 2, pStyle = 1, ops = 0, value = 0), state
            ) shouldBe true
        }
        it("param=2 pStyle=2 nghĩa là túi đồ còn chỗ") {
            val state = PlayerEventState(bagUsedSlots = 49, bagCapacity = 50)

            EveConditionEvaluator.evaluate(
                cond(cls = 7, param = 2, pStyle = 2, ops = 0, value = 0), state
            ) shouldBe true
            EveConditionEvaluator.evaluate(
                cond(cls = 7, param = 2, pStyle = 2, ops = 0, value = 0),
                state.copy(bagUsedSlots = 50)
            ) shouldBe false
        }
        it("param=2 pStyle=4 dùng số ô còn lại của túi để so sánh") {
            val state = PlayerEventState(bagUsedSlots = 47, bagCapacity = 50)

            EveConditionEvaluator.evaluate(
                cond(cls = 7, param = 2, pStyle = 4, ops = 5, value = 3), state
            ) shouldBe true
            EveConditionEvaluator.evaluate(
                cond(cls = 7, param = 2, pStyle = 4, ops = 5, value = 4), state
            ) shouldBe false
        }
        it("param không xác định dù ops=0/value=0 cũng không được nhầm qua") {
            EveConditionEvaluator.evaluate(
                cond(cls = 7, param = 99, ops = 0, value = 0), emptyState
            ) shouldBe false
        }
        it("param=5 dùng pStyle so khớp không gian tướng tùy thân") {
            val emptyPets = PlayerEventState(followPetCount = 0, maxFollowPets = 4)
            val fullPets = PlayerEventState(followPetCount = 4, maxFollowPets = 4)

            // pStyle=1: tướng đã đầy
            EveConditionEvaluator.evaluate(
                cond(cls = 7, param = 5, pStyle = 1, ops = 0, value = 0), fullPets
            ) shouldBe true
            EveConditionEvaluator.evaluate(
                cond(cls = 7, param = 5, pStyle = 1, ops = 0, value = 0), emptyPets
            ) shouldBe false

            // pStyle=2: tướng còn chỗ trống
            EveConditionEvaluator.evaluate(
                cond(cls = 7, param = 5, pStyle = 2, ops = 0, value = 0), emptyPets
            ) shouldBe true
            EveConditionEvaluator.evaluate(
                cond(cls = 7, param = 5, pStyle = 2, ops = 0, value = 0), fullPets
            ) shouldBe false

            // pStyle=4: so sánh số ô còn lại
            EveConditionEvaluator.evaluate(
                cond(cls = 7, param = 5, pStyle = 4, ops = 5, value = 2),
                PlayerEventState(followPetCount = 2, maxFollowPets = 4)
            ) shouldBe true
        }
        it("param=8 dùng Server ID đăng nhập; bao phủ shape thật của Eve.emg") {
            val normal = PlayerEventState(serverId = 1)
            val galaxy97 = PlayerEventState(serverId = 97)
            val galaxy197 = PlayerEventState(serverId = 197)
            val server100 = PlayerEventState(serverId = 100)

            // 12003/12180: ops=2,value=100 → serverId < 100
            EveConditionEvaluator.evaluate(
                cond(cls = 7, param = 8, pStyle = 1, ops = 2, value = 100), normal
            ) shouldBe true
            EveConditionEvaluator.evaluate(
                cond(cls = 7, param = 8, pStyle = 1, ops = 2, value = 100), server100
            ) shouldBe false

            // 12003/12180: ops=1,value=100 → serverId == 100
            EveConditionEvaluator.evaluate(
                cond(cls = 7, param = 8, pStyle = 1, ops = 1, value = 100), server100
            ) shouldBe true

            // 54901..54904: ops=5 with exact galaxy-server thresholds.
            EveConditionEvaluator.evaluate(
                cond(cls = 7, param = 8, pStyle = 1, ops = 5, value = 97), galaxy97
            ) shouldBe true
            EveConditionEvaluator.evaluate(
                cond(cls = 7, param = 8, pStyle = 1, ops = 5, value = 98), galaxy97
            ) shouldBe false
            EveConditionEvaluator.evaluate(
                cond(cls = 7, param = 8, pStyle = 1, ops = 5, value = 197), galaxy197
            ) shouldBe true
        }

        it("param=8 thiếu serverId hoặc shape lạ phải bảo thủ false") {
            EveConditionEvaluator.evaluate(
                cond(cls = 7, param = 8, pStyle = 1, ops = 2, value = 100), PlayerEventState()
            ) shouldBe false
            EveConditionEvaluator.evaluate(
                cond(cls = 7, param = 8, pStyle = 2, ops = 2, value = 100), PlayerEventState(serverId = 1)
            ) shouldBe false
        }

        it("param=19 dùng Point thực; khóa đúng 6 threshold casino của Eve.emg") {
            val state = PlayerEventState(realPoint = 50)

            for (threshold in listOf(9, 10, 45, 50)) {
                EveConditionEvaluator.evaluate(
                    cond(cls = 7, param = 19, pStyle = 2, ops = 5, value = threshold), state
                ) shouldBe true
            }
            for (threshold in listOf(90, 100)) {
                EveConditionEvaluator.evaluate(
                    cond(cls = 7, param = 19, pStyle = 2, ops = 5, value = threshold), state
                ) shouldBe false
            }
        }

        it("param=19 thiếu Point hoặc shape lạ phải bảo thủ false") {
            EveConditionEvaluator.evaluate(
                cond(cls = 7, param = 19, pStyle = 2, ops = 5, value = 10), PlayerEventState()
            ) shouldBe false
            EveConditionEvaluator.evaluate(
                cond(cls = 7, param = 19, pStyle = 1, ops = 5, value = 10),
                PlayerEventState(realPoint = 100)
            ) shouldBe false
        }

        it("param=16 subItem=1 nghĩa là đang cưỡi ngựa cần xuống ngựa") {
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
        it("param=16 subItem=2 nghĩa là không cưỡi và cho phép event tiếp tục") {
            val mounted = PlayerEventState(isMounted = true)
            val notMounted = PlayerEventState(isMounted = false)
            val c = cond(cls = 7, param = 16, pStyle = 1, ops = 0, value = 0)
                .copy(conditionSubItem = 2)

            EveConditionEvaluator.evaluate(c, mounted) shouldBe false
            EveConditionEvaluator.evaluate(c, notMounted) shouldBe true
        }
    }

    describe("class=8 kết quả chiến đấu") {
        it("parameter=0 giữ wildcard legacy và pStyle=1 khớp chiến thắng") {
            val state = PlayerEventState(battleResult = 1, fightDataKey = 174)
            EveConditionEvaluator.evaluate(
                cond(cls = 8, param = 0, pStyle = 1), state
            ) shouldBe true
        }
        it("pStyle=2 thất bại không khớp chiến thắng") {
            val state = PlayerEventState(battleResult = 1, fightDataKey = 174)
            EveConditionEvaluator.evaluate(
                cond(cls = 8, param = 174, pStyle = 2), state
            ) shouldBe false
        }
        it("parameter khác 0 phải khớp đúng FightData key vừa đánh") {
            val state = PlayerEventState(battleResult = 1, fightDataKey = 174)

            EveConditionEvaluator.evaluate(
                cond(cls = 8, param = 174, pStyle = 1), state
            ) shouldBe true
            EveConditionEvaluator.evaluate(
                cond(cls = 8, param = 6, pStyle = 1), state
            ) shouldBe false
        }
    }

    describe("class=9 sở hữu tướng") {
        it("có tướng → true") {
            val state = PlayerEventState(followNpcIds = setOf(22081))
            EveConditionEvaluator.evaluate(
                cond(cls = 9, ops = 5, value = 22081), state
            ) shouldBe true
        }
        it("không tướng → false") {
            val state = PlayerEventState(followNpcIds = emptySet())
            EveConditionEvaluator.evaluate(
                cond(cls = 9, ops = 5, value = 22081), state
            ) shouldBe false
        }
        it("điều kiện tướng nâng cao có param khác 0 không được dùng value=0 để nhầm qua") {
            val state = PlayerEventState(followNpcIds = setOf(15384))

            EveConditionEvaluator.evaluate(
                cond(cls = 9, param = 15384, pStyle = 2, ops = 1, value = 0), state
            ) shouldBe false
        }
    }

    describe("class=9 điều kiện tướng mang theo") {
        it("pStyle1 ops2 kiểm tra level lớn hơn ngưỡng") {
            val state = PlayerEventState(
                followNpcIds = setOf(17367),
                followNpcLevels = mapOf(17367 to 25)
            )
            EveConditionEvaluator.evaluate(
                cond(cls = 9, param = 17367, pStyle = 1, ops = 2, value = 24), state
            ) shouldBe true
        }

        it("pStyle2 ops2 kiểm tra loyalty lớn hơn ngưỡng") {
            val state = PlayerEventState(
                followNpcIds = setOf(17367),
                followNpcLoyalties = mapOf(17367 to 25)
            )
            EveConditionEvaluator.evaluate(
                cond(cls = 9, param = 17367, pStyle = 2, ops = 2, value = 24), state
            ) shouldBe true
        }

        it("pStyle3 ops1 value0 chỉ pass khi tướng chưa được xuất chiến") {
            val resting = PlayerEventState(
                followNpcIds = setOf(14641),
                deployedNpcIds = emptySet()
            )
            val deployed = resting.copy(deployedNpcIds = setOf(14641))
            val c = cond(cls = 9, param = 14641, pStyle = 3, ops = 1, value = 0)

            EveConditionEvaluator.evaluate(c, resting) shouldBe true
            EveConditionEvaluator.evaluate(c, deployed) shouldBe false
        }
    }

    describe("class=10 lựa chọn hội thoại") {
        it("surfaceId + code đều khớp") {
            val state = PlayerEventState(lastSurfaceId = 100, lastChoiceCode = 30)
            EveConditionEvaluator.evaluate(
                cond(cls = 10, param = 100, pStyle = 30), state
            ) shouldBe true
        }
        it("surfaceId không khớp") {
            val state = PlayerEventState(lastSurfaceId = 999, lastChoiceCode = 30)
            EveConditionEvaluator.evaluate(
                cond(cls = 10, param = 100, pStyle = 30), state
            ) shouldBe false
        }
    }

    describe("class=11 xác nhận quy trình thu kinh") {
        it("param=0 pStyle=1 ops=0 value=0 là điều kiện xác nhận thu kinh cố định đã quan sát") {
            EveConditionEvaluator.evaluate(
                cond(cls = 11, param = 0, pStyle = 1, ops = 0, value = 0), emptyState
            ) shouldBe true
        }
        it("các shape khác của class=11 bảo thủ không qua") {
            EveConditionEvaluator.evaluate(
                cond(cls = 11, param = 0, pStyle = 0, ops = 0, value = 0), emptyState
            ) shouldBe false
            EveConditionEvaluator.evaluate(
                cond(cls = 11, param = 1, pStyle = 1, ops = 0, value = 0), emptyState
            ) shouldBe false
        }
    }

    describe("class=12 bộ đếm hoàn thành sự kiện (so sánh thuận)") {
        it("val=1 ops=2(<) → phải số lần hoàn thành eveNo hiện tại < 1 mới qua") {
            // eveNo=5 hoàn thành 0 lần → 0 < 1 = true
            val state0 = PlayerEventState(completedEveCounts = emptyMap(), currentEveNo = 5)
            EveConditionEvaluator.evaluate(
                cond(cls = 12, param = 0, ops = 2, value = 1), state0
            ) shouldBe true

            // eveNo=5 hoàn thành 1 lần → 1 < 1 = false
            val state1 = PlayerEventState(completedEveCounts = mapOf(5 to 1), currentEveNo = 5)
            EveConditionEvaluator.evaluate(
                cond(cls = 12, param = 0, ops = 2, value = 1), state1
            ) shouldBe false
        }

        it("val=2 ops=1(==) → phải hoàn thành eveNo hiện tại 2 lần mới qua") {
            val state2 = PlayerEventState(completedEveCounts = mapOf(1 to 2), currentEveNo = 1)
            EveConditionEvaluator.evaluate(
                cond(cls = 12, param = 0, ops = 1, value = 2), state2
            ) shouldBe true  // 2 == 2

            val state1 = PlayerEventState(completedEveCounts = mapOf(1 to 1), currentEveNo = 1)
            EveConditionEvaluator.evaluate(
                cond(cls = 12, param = 0, ops = 1, value = 2), state1
            ) shouldBe false  // 1 == 2 = false
        }

        it("param không xác định không được nhầm qua vì value=0/ops=0") {
            EveConditionEvaluator.evaluate(
                cond(cls = 12, param = 3, ops = 0, value = 0), emptyState
            ) shouldBe false
        }
    }

    describe("class=15 điều kiện chuyển sinh người chơi") {
        it("param=0 đọc số lần chuyển sinh người chơi và áp dụng so sánh của Eve") {
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

        it("param=1 phân biệt bằng mã trạng thái cửa nhị chuyển sinh tướng: 0 mở được, -1 hiển thị hướng dẫn") {
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

    describe("class=3 trạng thái tương tác của vật thể trong cảnh") {
        it("shape cố định đã quan sát của Eve: pStyle=4 ops=5 value=0 qua") {
            EveConditionEvaluator.evaluate(
                cond(cls = 3, param = 1, pStyle = 4, ops = 5, value = 0), emptyState
            ) shouldBe true
        }

        it("các shape khác của class=3 bảo thủ không qua") {
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

    describe("class=4 điều kiện kỹ năng dùng comparator Eve riêng") {
        it("quest 10143: Thuật trinh thám Lv1 thỏa ops4 value1") {
            val learned = PlayerEventState(skillLevels = mapOf(14001 to 1))
            val absent = PlayerEventState(skillLevels = emptyMap())

            // scene12820/eve6: 14001 maxLv=1, ops4/value1 phải là >=1.
            EveConditionEvaluator.evaluate(
                cond(cls = 4, param = 14001, pStyle = 1, ops = 4, value = 1), learned
            ) shouldBe true
            EveConditionEvaluator.evaluate(
                cond(cls = 4, param = 14001, pStyle = 1, ops = 4, value = 1), absent
            ) shouldBe false
        }

        it("ops5 là exact level cho nhánh chưa học và đã học Lv1") {
            val absent = PlayerEventState(skillLevels = emptyMap())
            val learned = PlayerEventState(skillLevels = mapOf(14001 to 1))

            EveConditionEvaluator.evaluate(
                cond(cls = 4, param = 14001, pStyle = 1, ops = 5, value = 0), absent
            ) shouldBe true
            EveConditionEvaluator.evaluate(
                cond(cls = 4, param = 14001, pStyle = 1, ops = 5, value = 0), learned
            ) shouldBe false
            EveConditionEvaluator.evaluate(
                cond(cls = 4, param = 14001, pStyle = 1, ops = 5, value = 1), learned
            ) shouldBe true
        }

        it("ops2 là lớn hơn, khóa các skill đạt cấp tối đa") {
            val level10 = PlayerEventState(skillLevels = mapOf(14005 to 10))
            val level9 = PlayerEventState(skillLevels = mapOf(14005 to 9))

            // scene18102 uses skill14005 maxLv10 + ops2/value9.
            EveConditionEvaluator.evaluate(
                cond(cls = 4, param = 14005, pStyle = 1, ops = 2, value = 9), level10
            ) shouldBe true
            EveConditionEvaluator.evaluate(
                cond(cls = 4, param = 14005, pStyle = 1, ops = 2, value = 9), level9
            ) shouldBe false
        }
    }

    describe("class=5 điều kiện tiền") {
        it("ops=2 và value=bằng số tiền trừ 1 nghĩa là vàng đủ") {
            val state = PlayerEventState(gold = 100)

            EveConditionEvaluator.evaluate(
                cond(cls = 5, pStyle = 1, ops = 2, value = 99), state
            ) shouldBe true
            EveConditionEvaluator.evaluate(
                cond(cls = 5, pStyle = 1, ops = 2, value = 99), state.copy(gold = 99)
            ) shouldBe false
        }

        it("ops=4 khi xuất hiện trong chuỗi trừ tiền, bảo thủ coi là đã đạt ngưỡng") {
            val state = PlayerEventState(gold = 50)

            EveConditionEvaluator.evaluate(
                cond(cls = 5, pStyle = 1, ops = 4, value = 50), state
            ) shouldBe true
            EveConditionEvaluator.evaluate(
                cond(cls = 5, pStyle = 1, ops = 4, value = 50), state.copy(gold = 49)
            ) shouldBe false
        }
    }

    describe("Phân luồng điều kiện RoleCount của Eve") {
        it("class=18 và tham số là chỉ số RoleCount đã biết thì sẽ đọc roleCountValues") {
            val state = PlayerEventState(roleCountValues = mapOf(668 to 120))

            EveConditionEvaluator.evaluate(
                cond(cls = 18, param = 668, ops = 5, value = 100), state
            ) shouldBe true
        }

        it("class=18 param=1 bộ đếm hội thoại Đào Viên Kết Nghĩa đã được xác định là RoleCount (bằng chứng phía ghi resultClass=14)") {
            val state = PlayerEventState(roleCountValues = mapOf(1 to 3))

            EveConditionEvaluator.evaluate(
                cond(cls = 18, param = 1, ops = 5, value = 3), state
            ) shouldBe true
        }

        it("class=12 param1 dùng số người tổ đội, solo vẫn tính là 1") {
            val solo = PlayerEventState(teamSize = 1)
            val full = PlayerEventState(teamSize = 5)

            EveConditionEvaluator.evaluate(
                cond(cls = 12, param = 1, pStyle = 1, ops = 5, value = 1), solo
            ) shouldBe true
            EveConditionEvaluator.evaluate(
                cond(cls = 12, param = 1, pStyle = 1, ops = 5, value = 5), solo
            ) shouldBe false
            EveConditionEvaluator.evaluate(
                cond(cls = 12, param = 1, pStyle = 1, ops = 5, value = 5), full
            ) shouldBe true
        }

        it("class=13 offline không quân đoàn đi nhánh không thuộc thành, không mở đặc quyền") {
            val state = PlayerEventState(
                organizationId = 0L, organizationCityId = 0, organizationRank = 0
            )

            EveConditionEvaluator.evaluate(
                cond(cls = 13, param = 1, pStyle = 1, ops = 6, value = 3), state
            ) shouldBe true
            EveConditionEvaluator.evaluate(
                cond(cls = 13, param = 1, pStyle = 1, ops = 5, value = 3), state
            ) shouldBe false
            EveConditionEvaluator.evaluate(
                cond(cls = 13, param = 2, pStyle = 1, ops = 0, value = 0), state
            ) shouldBe false
            EveConditionEvaluator.evaluate(
                cond(cls = 13, param = 0, pStyle = 1, ops = 1, value = 1), state
            ) shouldBe true
        }

        it("class=13 quân đoàn sở hữu thành và chức vụ khớp đúng") {
            val state = PlayerEventState(
                organizationId = 123L, organizationCityId = 3, organizationRank = 1
            )

            EveConditionEvaluator.evaluate(
                cond(cls = 13, param = 1, pStyle = 1, ops = 5, value = 3), state
            ) shouldBe true
            EveConditionEvaluator.evaluate(
                cond(cls = 13, param = 1, pStyle = 1, ops = 6, value = 3), state
            ) shouldBe false
            EveConditionEvaluator.evaluate(
                cond(cls = 13, param = 2, pStyle = 1, ops = 0, value = 0), state
            ) shouldBe true
            EveConditionEvaluator.evaluate(
                cond(cls = 13, param = 2, pStyle = 2, ops = 0, value = 0), state
            ) shouldBe false
        }

        it("class14 compares OLE time bounds, not an activityOpen flag") {
            val start = cond(cls = 14, ops = 2).copy(conditionTimeValue = 43320.0)
            val end = cond(cls = 14, ops = 1).copy(conditionTimeValue = 43325.0)
            val inside = PlayerEventState(eveNowOaDate = 43322.0, activityOpen = false)
            EveConditionEvaluator.evaluate(start, inside) shouldBe true
            EveConditionEvaluator.evaluate(end, inside) shouldBe true
            val after = PlayerEventState(eveNowOaDate = 43330.0, activityOpen = true)
            EveConditionEvaluator.evaluate(start, after) shouldBe true
            EveConditionEvaluator.evaluate(end, after) shouldBe false
        }

        it("class=18 tham số nhỏ chưa xác định sẽ không dùng nhầm RoleCount") {
            val state = PlayerEventState(roleCountValues = mapOf(2 to 999))

            EveConditionEvaluator.evaluate(
                cond(cls = 18, param = 2, ops = 5, value = 1), state
            ) shouldBe false
        }

        it("class14 ops3-4 inclusive bounds, unknown time fails closed") {
            val lower = cond(cls = 14, ops = 4).copy(conditionTimeValue = 43376.0)
            val upper = cond(cls = 14, ops = 3).copy(conditionTimeValue = 43383.0)
            EveConditionEvaluator.evaluate(lower, PlayerEventState(eveNowOaDate = 43376.0)) shouldBe true
            EveConditionEvaluator.evaluate(upper, PlayerEventState(eveNowOaDate = 43383.0)) shouldBe true
            EveConditionEvaluator.evaluate(lower, PlayerEventState(eveNowOaDate = 43375.0)) shouldBe false
            EveConditionEvaluator.evaluate(upper, PlayerEventState(eveNowOaDate = 43384.0)) shouldBe false
            EveConditionEvaluator.evaluate(lower, PlayerEventState(eveNowOaDate = null)) shouldBe false
            EveConditionEvaluator.evaluate(lower, PlayerEventState(eveNowOaDate = Double.NaN)) shouldBe false
            EveConditionEvaluator.evaluate(lower.copy(conditionTimeValue = 0.0),
                PlayerEventState(eveNowOaDate = 43376.0)) shouldBe false
        }

        it("class14 unknown param2 and pStyle1-4 do not auto-enable") {
            for (style in 1..4) {
                val c = cond(cls = 14, param = 2, pStyle = style)
                    .copy(conditionTimeValue = 37931.6033664236)
                EveConditionEvaluator.evaluate(
                    c, PlayerEventState(eveNowOaDate = 37931.6033664236)
                ) shouldBe false
            }
        }

        it("class=14 không còn đánh dấu là RoleCount đã xác nhận") {
            val state = PlayerEventState(roleCountValues = mapOf(0 to 999))

            EveConditionEvaluator.evaluate(
                cond(cls = 14, param = 0, ops = 5, value = 1), state
            ) shouldBe false
        }
    }

    describe("class không xác định") {
        it("bảo thủ trả về false") {
            EveConditionEvaluator.evaluate(cond(cls = 99), emptyState) shouldBe false
        }
    }
})
