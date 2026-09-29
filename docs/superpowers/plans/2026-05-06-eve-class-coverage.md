# Eve Class Coverage Phase 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** xây đợt cài đặt đầu tiên có thể kiểm thử bao phủ ngữ nghĩa lớp Eve, xử lý trước các lớp độ tin cậy cao và ngăn lớp chưa xác minh bị nhận nhầm là đã hoàn thành.

**Architecture:** trước tiên thêm test bao phủ định hướng dữ liệu, cố định tập hợp class đã quan sát trong `Eve.emg`. Logic điều kiện chỉ bù phần xác nhận chéo được từ Lua và mẫu sự kiện; ngữ nghĩa chưa xác nhận giữ false hoặc skip rõ ràng, không để nhiệm vụ qua nhờ đoán.

**Tech Stack:** Kotlin, Kotest, Gradle, mã nguồn Lua đã giải mã, dữ liệu tĩnh Eve.emg.

---

## Cấu trúc file

- Modify: `gamelogic/src/main/kotlin/tw/tsm/gamelogic/systems/EveConditionEvaluator.kt`
  - Điều chỉnh chú thích tài liệu, hạ `conditionClass=14` khỏi mức đã xác nhận RoleCount.
  - Thêm điều kiện kỹ năng `conditionClass=4`.
  - Thêm phân luồng an toàn RoleCount cho `conditionClass=18`.
- Modify: `gamelogic/src/main/kotlin/tw/tsm/gamelogic/systems/EveStateBuilder.kt`
  - Đọc cấp kỹ năng của người chơi từ `SkillSystem` và đưa vào `PlayerEventState`.
- Modify: `app/src/main/kotlin/tw/tsm/app/ServerModule.kt`
  - Nếu tham số khởi tạo của `EveStateBuilder` thêm `SkillSystem`, đồng bộ injection Koin.
- Modify: `tests/src/test/kotlin/tw/tsm/tests/EveConditionEvaluatorTest.kt`
  - Thêm test điều kiện kỹ năng, RoleCount class18, hạ cấp class14.
- Modify: `tests/src/test/kotlin/tw/tsm/tests/EveStateBuilderTest.kt`
  - Thêm test xây dựng trạng thái kỹ năng.
- Create: `tests/src/test/kotlin/tw/tsm/tests/EveClassCoverageTest.kt`
  - Cố định tập hợp class đã quan sát của `Eve.emg`, chặn âm thầm mở rộng khi dữ liệu cập nhật.
- Modify: `docs/eve-system.md`
  - Đồng bộ sửa mô tả `conditionClass=14/18`.

## Task 1: Cố định tập hợp class Eve.emg

**Files:**
- Create: `tests/src/test/kotlin/tw/tsm/tests/EveClassCoverageTest.kt`

- [ ] **Step 1: Write the coverage test**

```kotlin
package tw.tsm.tests

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import tw.tsm.data.loaders.EveDataLoader
import java.nio.file.Files
import java.nio.file.Path

class EveClassCoverageTest : DescribeSpec({
    describe("Eve.emg tập hợp class") {
        val eveBytes = Files.readAllBytes(Path.of("gamedata", "Eve.emg"))
        val events = EveDataLoader.load(eveBytes).values.flatMap { it.npcEvents.values }

        it("tập hợp conditionClass phải được theo dõi tường minh") {
            val actual = events
                .flatMap { it.conditions }
                .map { it.conditionClass }
                .toSet()

            actual shouldBe setOf(0, 1, 2, 3, 4, 5, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18)
        }

        it("tập hợp resultType phải được theo dõi tường minh") {
            val actual = events
                .flatMap { event -> event.conditions.flatMap { it.results } }
                .map { it.resultType }
                .toSet()

            actual shouldBe setOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
        }

        it("tập hợp resultClass phải được theo dõi tường minh") {
            val actual = events
                .flatMap { event -> event.conditions.flatMap { it.results } }
                .map { it.resultClass }
                .toSet()

            actual shouldBe setOf(0, 1, 2, 3, 4, 5, 7, 8, 10, 11, 12, 13, 14, 15)
        }
    }
})
```

- [ ] **Step 2: Run coverage test**

Run: `.\gradlew.bat :tests:test --tests tw.tsm.tests.EveClassCoverageTest --no-daemon`

Expected: PASS.

- [ ] **Step 3: Commit**

```powershell
git add tests/src/test/kotlin/tw/tsm/tests/EveClassCoverageTest.kt
git commit -m "Thêm test bao phủ lớp Eve"
```

## Task 2: Thêm trạng thái điều kiện kỹ năng

**Files:**
- Modify: `gamelogic/src/main/kotlin/tw/tsm/gamelogic/systems/EveConditionEvaluator.kt`
- Modify: `gamelogic/src/main/kotlin/tw/tsm/gamelogic/systems/EveStateBuilder.kt`
- Modify: `app/src/main/kotlin/tw/tsm/app/ServerModule.kt`
- Modify: `tests/src/test/kotlin/tw/tsm/tests/EveConditionEvaluatorTest.kt`
- Modify: `tests/src/test/kotlin/tw/tsm/tests/EveStateBuilderTest.kt`

- [ ] **Step 1: Write the failing evaluator test**

Add to `EveConditionEvaluatorTest.kt`:

```kotlin
describe("class=4 điều kiện kỹ năng") {
    it("sẽ so cấp kỹ năng với conditionValue") {
        val state = PlayerEventState(skillLevels = mapOf(14001 to 2))

        EveConditionEvaluator.evaluate(cond(cls = 4, param = 14001, ops = 5, value = 1), state) shouldBe true
        EveConditionEvaluator.evaluate(cond(cls = 4, param = 14001, ops = 2, value = 1), state) shouldBe false
    }

    it("kỹ năng chưa học coi là cấp 0") {
        val state = PlayerEventState(skillLevels = emptyMap())

        EveConditionEvaluator.evaluate(cond(cls = 4, param = 14001, ops = 1, value = 0), state) shouldBe true
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :tests:test --tests tw.tsm.tests.EveConditionEvaluatorTest --no-daemon`

Expected: FAIL because `PlayerEventState.skillLevels` or `conditionClass=4` does not exist yet.

- [ ] **Step 3: Implement minimal evaluator state**

In `PlayerEventState`, add:

```kotlin
val skillLevels: Map<Int, Int> = emptyMap(),
```

In `EveConditionEvaluator.evaluate`, add:

```kotlin
4 -> {
    val skillLevel = state.skillLevels[cond.conditionParameter] ?: 0
    val result = compareStep(skillLevel, cond.conditionOps, cond.conditionValue)
    logger.debug {
        "class=4 skillId=${cond.conditionParameter} level=$skillLevel ops=${cond.conditionOps} val=${cond.conditionValue} → $result"
    }
    result
}
```

- [ ] **Step 4: Run evaluator test**

Run: `.\gradlew.bat :tests:test --tests tw.tsm.tests.EveConditionEvaluatorTest --no-daemon`

Expected: PASS.

- [ ] **Step 5: Add state builder test**

Add imports in `EveStateBuilderTest.kt`:

```kotlin
import tw.tsm.domain.interfaces.repositories.CharacterRepository
import tw.tsm.domain.interfaces.repositories.SkillRepository
import tw.tsm.gamelogic.systems.SkillSystem
```

Add this fake repository code inside the test before constructing `EveStateBuilder`:

```kotlin
val skillRepository = object : SkillRepository {
    override suspend fun findByCharacterId(characterId: Long): Map<Int, Int> =
        mapOf(14001 to 2)

    override suspend fun saveAll(characterId: Long, skills: Map<Int, Int>) = Unit
}

val characterRepository = mockk<CharacterRepository>(relaxed = true)
val skillSystem = SkillSystem(skillRepository, characterRepository, emptyMap())
skillSystem.load(charId, element = 1, skillPoint = 0)
```

After `builder.buildPlayerState(charId, mapId)`, assert:

```kotlin
state.skillLevels[14001] shouldBe 2
```

- [ ] **Step 6: Wire `EveStateBuilder`**

Add `SkillSystem` to `EveStateBuilder` constructor:

```kotlin
private val skillSystem: SkillSystem,
```

Add in `buildPlayerState`:

```kotlin
val skillLevels = skillSystem.getCache(charId)?.skills?.toMap() ?: emptyMap()
```

Pass into `PlayerEventState`:

```kotlin
skillLevels = skillLevels,
```

Update `ServerModule.kt`:

```kotlin
EveStateBuilder(
    questSystem,
    inventorySystem,
    petSystem,
    eveEventStateMachine,
    playerStateManager,
    markDefs,
    roleCountSystem,
    skillSystem,
    questRepository
)
```

- [ ] **Step 7: Run focused tests**

Run:

```powershell
.\gradlew.bat :tests:test --tests tw.tsm.tests.EveConditionEvaluatorTest --no-daemon
.\gradlew.bat :tests:test --tests tw.tsm.tests.EveStateBuilderTest --no-daemon
```

Expected: PASS.

- [ ] **Step 8: Commit**

```powershell
git add gamelogic/src/main/kotlin/tw/tsm/gamelogic/systems/EveConditionEvaluator.kt gamelogic/src/main/kotlin/tw/tsm/gamelogic/systems/EveStateBuilder.kt app/src/main/kotlin/tw/tsm/app/ServerModule.kt tests/src/test/kotlin/tw/tsm/tests/EveConditionEvaluatorTest.kt tests/src/test/kotlin/tw/tsm/tests/EveStateBuilderTest.kt
git commit -m "Bổ sung điều kiện kỹ năng Eve"
```

## Task 3: Sửa nhãn lớp RoleCount

**Files:**
- Modify: `gamelogic/src/main/kotlin/tw/tsm/gamelogic/systems/EveConditionEvaluator.kt`
- Modify: `tests/src/test/kotlin/tw/tsm/tests/EveConditionEvaluatorTest.kt`
- Modify: `docs/eve-system.md`

- [ ] **Step 1: Write failing tests**

Add to `EveConditionEvaluatorTest.kt`:

```kotlin
describe("RoleCount phân luồng điều kiện Eve") {
    it("class=18 và tham số là chỉ số RoleCount đã biết thì đọc roleCountValues") {
        val state = PlayerEventState(roleCountValues = mapOf(668 to 120))

        EveConditionEvaluator.evaluate(cond(cls = 18, param = 668, ops = 5, value = 100), state) shouldBe true
    }

    it("class=18 tham số thấp chưa rõ ngữ nghĩa không dùng nhầm RoleCount") {
        val state = PlayerEventState(roleCountValues = mapOf(1 to 999))

        EveConditionEvaluator.evaluate(cond(cls = 18, param = 1, ops = 5, value = 1), state) shouldBe false
    }

    it("class=14 không còn đánh dấu là RoleCount đã xác nhận") {
        val state = PlayerEventState(roleCountValues = mapOf(0 to 999))

        EveConditionEvaluator.evaluate(cond(cls = 14, param = 0, ops = 5, value = 1), state) shouldBe false
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :tests:test --tests tw.tsm.tests.EveConditionEvaluatorTest --no-daemon`

Expected: FAIL because class18 is not implemented and class14 still reads RoleCount.

- [ ] **Step 3: Implement safe RoleCount predicate**

Add helper in `EveConditionEvaluator`:

```kotlin
private fun isKnownRoleCountIndex(index: Int): Boolean =
    index in setOf(100, 101, 102, 108, 109, 192, 668, 822)
```

Replace class14 branch with:

```kotlin
14 -> {
    logger.warn {
        "conditionClass=14 ngữ nghĩa chờ xác minh param=${cond.conditionParameter} pStyle=${cond.conditionParameterStyle} ops=${cond.conditionOps} val=${cond.conditionValue} → false"
    }
    false
}
```

Add class18 branch:

```kotlin
18 -> {
    if (!isKnownRoleCountIndex(cond.conditionParameter)) {
        logger.warn {
            "conditionClass=18 không phải chỉ số RoleCount đã biết param=${cond.conditionParameter} pStyle=${cond.conditionParameterStyle} ops=${cond.conditionOps} val=${cond.conditionValue} → false"
        }
        false
    } else {
        val actual = state.roleCountValues[cond.conditionParameter] ?: 0
        val result = compareStep(actual, cond.conditionOps, cond.conditionValue)
        logger.debug {
            "class=18 roleCount=${cond.conditionParameter} actual=$actual ops=${cond.conditionOps} val=${cond.conditionValue} → $result"
        }
        result
    }
}
```

- [ ] **Step 4: Update docs**

In `docs/eve-system.md`, replace any wording that says `conditionClass=14` is confirmed RoleCount with:

```markdown
| **14** | Chờ xác minh | Không dùng thẳng làm RoleCount; đa số mẫu trong Eve.emg là param=0/pStyle=0/value=0 |
| **18** | Ứng viên RoleCount | Chỉ chỉ số RoleCount đã biết được đọc roleCountValues, còn lại giữ false |
```

- [ ] **Step 5: Run focused tests**

Run: `.\gradlew.bat :tests:test --tests tw.tsm.tests.EveConditionEvaluatorTest --no-daemon`

Expected: PASS.

- [ ] **Step 6: Commit**

```powershell
git add gamelogic/src/main/kotlin/tw/tsm/gamelogic/systems/EveConditionEvaluator.kt tests/src/test/kotlin/tw/tsm/tests/EveConditionEvaluatorTest.kt docs/eve-system.md
git commit -m "Sửa phân luồng điều kiện Eve RoleCount"
```

## Task 4: Kiểm chứng hồi quy Eve đợt đầu

**Files:**
- No source change unless tests expose compile errors.

- [ ] **Step 1: Run Eve focused tests**

Run: `.\gradlew.bat :tests:test --tests "tw.tsm.tests.Eve*" --no-daemon`

Expected: PASS.

- [ ] **Step 2: Run full test suite**

Run: `.\gradlew.bat :tests:test --no-daemon`

Expected: PASS.

- [ ] **Step 3: Commit if any test-only correction was required**

Run: `git status --short`

Expected: no additional modified source files. If this command shows additional files, stop and inspect the diff before committing.

## Self-Review

- Spec coverage: kế hoạch này xử lý các mục độ tin cậy cao đợt đầu, không tuyên bố đã xong toàn bộ class. Bao phủ đầy đủ vẫn cần đợt hai xử lý `resultClass=10`, `conditionClass=5/13/16/17`.
- Placeholder scan: không dùng text giữ chỗ chờ bổ sung hay dấu ba chấm; ngữ nghĩa chờ xác minh kiểm soát bằng false hoặc skip rõ ràng.
- Type consistency: tên trường trạng thái thêm mới cố định là `skillLevels`; phân luồng RoleCount cố định tại `conditionClass=18`.
