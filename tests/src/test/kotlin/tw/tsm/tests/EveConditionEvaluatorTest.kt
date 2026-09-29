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

    describe("class=2 bước nhiệm vụ") {
        val state = PlayerEventState(
            missionSteps = mapOf(10 to 2),
            missionFlags = mapOf(10 to 1)
        )

        it("pStyle=1 chỉ xem step — step=2 ops=5 val=1 → 2>=1 true") {
            EveConditionEvaluator.evaluate(
                cond(cls = 2, param = 10, pStyle = 1, ops = 5, value = 1), state
            ) shouldBe true
        }
        it("pStyle=3 lấy max(step, flag) — nhiệm vụ đã xong dùng giá trị cờ") {
            // mission 20: step=0, flag=1 → actual = max(0,1) = 1
            val s = PlayerEventState(
                missionSteps = emptyMap(),
                missionFlags = mapOf(20 to 1)
            )
            EveConditionEvaluator.evaluate(
                cond(cls = 2, param = 20, pStyle = 3, ops = 5, value = 1), s
            ) shouldBe true
        }
        it("pStyle=3 nhiệm vụ chưa bắt đầu → actual=0") {
            val s = PlayerEventState()
            EveConditionEvaluator.evaluate(
                cond(cls = 2, param = 99, pStyle = 3, ops = 1, value = 0), s
            ) shouldBe true
        }
    }

    describe("class=7 thuộc tính chung") {
        it("param=0 kiểm tra cấp") {
            val state = PlayerEventState(level = 10)
            EveConditionEvaluator.evaluate(
                cond(cls = 7, param = 0, ops = 5, value = 5), state
            ) shouldBe true
            EveConditionEvaluator.evaluate(
                cond(cls = 7, param = 0, ops = 5, value = 15), state
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
        it("param=16 subItem=2 mẫu là điều kiện kết quả trống, tạm không coi là nhánh xuống ngựa") {
            val mounted = PlayerEventState(isMounted = true)

            EveConditionEvaluator.evaluate(
                cond(cls = 7, param = 16, pStyle = 1, ops = 0, value = 0).copy(conditionSubItem = 2),
                mounted
            ) shouldBe false
        }
    }

    describe("class=8 kết quả chiến đấu") {
        it("pStyle=1 khớp chiến thắng") {
            val state = PlayerEventState(battleResult = 1)
            EveConditionEvaluator.evaluate(
                cond(cls = 8, pStyle = 1), state
            ) shouldBe true
        }
        it("pStyle=2 thất bại không khớp chiến thắng") {
            val state = PlayerEventState(battleResult = 1)
            EveConditionEvaluator.evaluate(
                cond(cls = 8, pStyle = 2), state
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

    describe("class=4 điều kiện kỹ năng") {
        it("sẽ so cấp kỹ năng với conditionValue") {
            val state = PlayerEventState(skillLevels = mapOf(14001 to 2))

            EveConditionEvaluator.evaluate(
                cond(cls = 4, param = 14001, ops = 5, value = 1), state
            ) shouldBe true
            EveConditionEvaluator.evaluate(
                cond(cls = 4, param = 14001, ops = 2, value = 1), state
            ) shouldBe false
        }

        it("kỹ năng chưa học tính cấp là 0") {
            val state = PlayerEventState(skillLevels = emptyMap())

            EveConditionEvaluator.evaluate(
                cond(cls = 4, param = 14001, ops = 1, value = 0), state
            ) shouldBe true
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

        it("class=18 tham số nhỏ chưa xác định sẽ không dùng nhầm RoleCount") {
            val state = PlayerEventState(roleCountValues = mapOf(2 to 999))

            EveConditionEvaluator.evaluate(
                cond(cls = 18, param = 2, ops = 5, value = 1), state
            ) shouldBe false
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
