# Eve Class Coverage Phase 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 建立 Eve 類別語意覆蓋的第一批可測試實作，先處理高信心類別並防止未驗證類別被誤判為已完成。

**Architecture:** 先新增資料驅動的覆蓋測試，固定 `Eve.emg` 中已觀測的 class 集合。條件邏輯只補能從 Lua 與事件樣本交叉確認的部分；未確認語意維持 false 或明確 skip，不讓任務靠猜測通過。

**Tech Stack:** Kotlin、Kotest、Gradle、Lua 解密原始碼、Eve.emg 靜態資料。

---

## 檔案結構

- Modify: `gamelogic/src/main/kotlin/tw/tsm/gamelogic/systems/EveConditionEvaluator.kt`
  - 調整文件註解，把 `conditionClass=14` 從已確認 RoleCount 降級。
  - 增加 `conditionClass=4` 技能條件。
  - 增加 `conditionClass=18` 的 RoleCount 安全分流。
- Modify: `gamelogic/src/main/kotlin/tw/tsm/gamelogic/systems/EveStateBuilder.kt`
  - 從 `SkillSystem` 讀取玩家技能等級並放入 `PlayerEventState`。
- Modify: `app/src/main/kotlin/tw/tsm/app/ServerModule.kt`
  - 若 `EveStateBuilder` 建構參數新增 `SkillSystem`，同步 Koin 注入。
- Modify: `tests/src/test/kotlin/tw/tsm/tests/EveConditionEvaluatorTest.kt`
  - 新增技能條件、RoleCount class18、class14 降級測試。
- Modify: `tests/src/test/kotlin/tw/tsm/tests/EveStateBuilderTest.kt`
  - 新增技能狀態建構測試。
- Create: `tests/src/test/kotlin/tw/tsm/tests/EveClassCoverageTest.kt`
  - 固定 `Eve.emg` 已觀測 class 集合，防止資料更新後無聲擴張。
- Modify: `docs/eve-system.md`
  - 同步修正 `conditionClass=14/18` 描述。

## Task 1: 固定 Eve.emg 類別集合

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
    describe("Eve.emg 類別集合") {
        val eveBytes = Files.readAllBytes(Path.of("gamedata", "Eve.emg"))
        val events = EveDataLoader.load(eveBytes).values.flatMap { it.npcEvents.values }

        it("conditionClass 集合必須被明確追蹤") {
            val actual = events
                .flatMap { it.conditions }
                .map { it.conditionClass }
                .toSet()

            actual shouldBe setOf(0, 1, 2, 3, 4, 5, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18)
        }

        it("resultType 集合必須被明確追蹤") {
            val actual = events
                .flatMap { event -> event.conditions.flatMap { it.results } }
                .map { it.resultType }
                .toSet()

            actual shouldBe setOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
        }

        it("resultClass 集合必須被明確追蹤") {
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
git commit -m "新增 Eve 類別覆蓋測試"
```

## Task 2: 加入技能條件狀態

**Files:**
- Modify: `gamelogic/src/main/kotlin/tw/tsm/gamelogic/systems/EveConditionEvaluator.kt`
- Modify: `gamelogic/src/main/kotlin/tw/tsm/gamelogic/systems/EveStateBuilder.kt`
- Modify: `app/src/main/kotlin/tw/tsm/app/ServerModule.kt`
- Modify: `tests/src/test/kotlin/tw/tsm/tests/EveConditionEvaluatorTest.kt`
- Modify: `tests/src/test/kotlin/tw/tsm/tests/EveStateBuilderTest.kt`

- [ ] **Step 1: Write the failing evaluator test**

Add to `EveConditionEvaluatorTest.kt`:

```kotlin
describe("class=4 技能條件") {
    it("會用技能等級比對 conditionValue") {
        val state = PlayerEventState(skillLevels = mapOf(14001 to 2))

        EveConditionEvaluator.evaluate(cond(cls = 4, param = 14001, ops = 5, value = 1), state) shouldBe true
        EveConditionEvaluator.evaluate(cond(cls = 4, param = 14001, ops = 2, value = 1), state) shouldBe false
    }

    it("未學技能等級視為 0") {
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
git commit -m "補齊 Eve 技能條件"
```

## Task 3: 修正 RoleCount 類別標記

**Files:**
- Modify: `gamelogic/src/main/kotlin/tw/tsm/gamelogic/systems/EveConditionEvaluator.kt`
- Modify: `tests/src/test/kotlin/tw/tsm/tests/EveConditionEvaluatorTest.kt`
- Modify: `docs/eve-system.md`

- [ ] **Step 1: Write failing tests**

Add to `EveConditionEvaluatorTest.kt`:

```kotlin
describe("RoleCount Eve 條件分流") {
    it("class=18 且參數為已知 RoleCount 索引時會讀 roleCountValues") {
        val state = PlayerEventState(roleCountValues = mapOf(668 to 120))

        EveConditionEvaluator.evaluate(cond(cls = 18, param = 668, ops = 5, value = 100), state) shouldBe true
    }

    it("class=18 低參數未知語意不會誤用 RoleCount") {
        val state = PlayerEventState(roleCountValues = mapOf(1 to 999))

        EveConditionEvaluator.evaluate(cond(cls = 18, param = 1, ops = 5, value = 1), state) shouldBe false
    }

    it("class=14 不再標示為已確認 RoleCount") {
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
        "conditionClass=14 語意待驗證 param=${cond.conditionParameter} pStyle=${cond.conditionParameterStyle} ops=${cond.conditionOps} val=${cond.conditionValue} → false"
    }
    false
}
```

Add class18 branch:

```kotlin
18 -> {
    if (!isKnownRoleCountIndex(cond.conditionParameter)) {
        logger.warn {
            "conditionClass=18 非已知 RoleCount 索引 param=${cond.conditionParameter} pStyle=${cond.conditionParameterStyle} ops=${cond.conditionOps} val=${cond.conditionValue} → false"
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
| **14** | 待驗證 | 不可直接當 RoleCount；Eve.emg 多數樣本為 param=0/pStyle=0/value=0 |
| **18** | RoleCount 候選 | 僅已知 RoleCount 索引可讀 roleCountValues，其餘保持 false |
```

- [ ] **Step 5: Run focused tests**

Run: `.\gradlew.bat :tests:test --tests tw.tsm.tests.EveConditionEvaluatorTest --no-daemon`

Expected: PASS.

- [ ] **Step 6: Commit**

```powershell
git add gamelogic/src/main/kotlin/tw/tsm/gamelogic/systems/EveConditionEvaluator.kt tests/src/test/kotlin/tw/tsm/tests/EveConditionEvaluatorTest.kt docs/eve-system.md
git commit -m "修正 Eve RoleCount 條件分流"
```

## Task 4: 驗證第一批 Eve 回歸

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

- Spec coverage：本計畫處理第一批高信心項目，不宣稱完成全部 class。完整覆蓋仍需第二批處理 `resultClass=10`、`conditionClass=5/13/16/17`。
- Placeholder scan：沒有使用待補占位文字或省略號；待驗證語意以明確 false 或 skip 管控。
- Type consistency：新增狀態欄位名稱固定為 `skillLevels`；RoleCount 分流固定在 `conditionClass=18`。
