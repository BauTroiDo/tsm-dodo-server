# Eve 事件系統技術文檔

> tsm_dodo NPC 對話 / 任務推進 / 事件觸發的完整技術參考

---

## 1. 系統總覽

### 元件關係圖

```
Client C:020-{1~8}
        │
        ▼
┌──────────────────────────────────┐
│        NpcEventHandler           │  ← app 層入口，路由 7 種觸發
│  (mainKind=20, sub=1~9)          │
└──────┬───────────────────────────┘
       │
       │ buildPlayerState()
       ▼
┌────────────────┐  ┌─────────────────┐  ┌─────────────────────┐
│ QuestSystem    │  │ InventorySystem │  │ PetSystem           │
│ (任務步驟/旗標) │  │ (背包/裝備)      │  │ (武將持有)           │
└────────────────┘  └─────────────────┘  └─────────────────────┘
       │                    │                      │
       └────────────────────┼──────────────────────┘
                            ▼
                   PlayerEventState（快照）
                            │
              ┌─────────────┼─────────────┐
              ▼             ▼             ▼
     EveConditionEvaluator  │    EveChainResolver
     (純函數條件評估)        │    (AND 鏈分組 + 四層排序)
                            │
                            ▼
                   EveEventStateMachine
                   (Session 生命週期 + 完成追蹤)
                            │
                            ▼
                   dispatchResult()
                   (resultType 分流 → S:020-001/007/008)
```

### 檔案清單

| 檔案 | 模組 | 職責 |
|------|------|------|
| `app/.../handlers/NpcEventHandler.kt` | app | C→S 封包路由、結果分發、任務/物品/武將處理 |
| `gamelogic/.../systems/EveConditionEvaluator.kt` | gamelogic | 純函數條件評估（已觀測 18 種 conditionClass，未確認者保守 false） |
| `gamelogic/.../systems/EveChainResolver.kt` | gamelogic | AND 鏈分組 + 四層排序匹配 |
| `gamelogic/.../systems/EveEventStateMachine.kt` | gamelogic | 事件 Session 管理、完成追蹤、預填充 |
| `gamelogic/.../systems/QuestSystem.kt` | gamelogic | 任務步驟增減、完成旗標 |
| `domain/.../data/EveData.kt` | domain | 15 個事件 data class |
| `domain/.../data/MarkDef.kt` | domain | Mark_C.dat 任務定義 |
| `data/.../loaders/EveDataLoader.kt` | data | Eve.emg 二進位載入器 |
| `persistence/.../tables/CharacterMissions.kt` | persistence | 進行中任務 DB 表 |
| `persistence/.../tables/CharacterMissionFlags.kt` | persistence | 完成旗標 DB 表 |

---

## 2. 資料結構

### 2.1 核心 Data Class（domain/data/EveData.kt）

#### EveResult — 事件結果（對應 S:020-001 封包 15 bytes）

| 欄位 | 型別 | 說明 |
|------|------|------|
| resultGroupNo | Int (2B) | 結果分組號（>0 引用 GroupData 機率選擇） |
| resultNo | Int (1B) | 結果序號（GroupData 選擇標的） |
| resultType | Int (1B) | 結果類型：0=動作 1=對話 2=門 3=戰鬥 5=動畫 6=互動 9=NPC動作 |
| resultClass | Int (1B) | 結果分類：1=物品 2=任務 3=NPC隊伍 4=技能 7=玩家 8=獎勵武將 |
| parameter | Int (2B) | 參數（missionId / itemId / npcClickId / sceneInfoKey） |
| parameterStyle | Int (1B) | 參數樣式（1=增加 2=減少/收回 3=完成） |
| resultValue | Int (4B) | 數值（步驟增量 / 物品數量 / 技能等級） |
| resultMeanNo | Int (2B) | 對話 ID / fightDataKey / surfaceId |

#### EveCondition — 事件條件

| 欄位 | 型別 | 說明 |
|------|------|------|
| conditionNo | Int (1B) | 條件序號 |
| conditionClass | Int (1B) | 條件分類（§3 詳述） |
| conditionParameter | Int (2B) | 條件參數（missionId / itemId / 屬性索引） |
| conditionParameterStyle | Int (1B) | 參數樣式（class=2 pStyle=3 特殊語義） |
| conditionOps | Int (1B) | 比較運算子（§3.2 詳述） |
| conditionValue | Int (4B) | 比較值 |
| conditionSubItem | Int (2B) | 子項目 |
| toResult | Int (1B) | 跳轉結果位置（AND 鏈用） |
| andNum | Int (1B) | AND 鏈長度（§4.1 詳述） |
| results | List\<EveResult\> | 此條件附帶的結果列表 |

#### NpcEventData — NPC 事件

| 欄位 | 說明 |
|------|------|
| eveNo | 事件編號 |
| whenHappen | 觸發時機 [0]=ClickNpc [1]=BumpNpc [2]=ScanNpc [3]=ServerBump |
| conditions | 條件列表（依序評估） |

#### 場景擺放

| Class | 說明 |
|-------|------|
| EveNpcPlacement | NPC 擺放（id/npcId/events[]/x/y/close） |
| EveDoorPlacement | 傳送點擺放（id/events[]/x/y） |

#### 場景資訊

| Class | 說明 |
|-------|------|
| EveSceneInfo | Door 目的地（backgroundNo/playerAppearX/Y/direction） |
| EveFightData | 戰鬥配置（leftEnemies[]/rightEnemies[]/fightType/fightLimit） |
| EveFightEnemy | 敵人配置（npcId/locationPos/ai） |
| EveSurfaceData | 互動定義（sentences[]/optionIndex/optionCount/optionMode） |
| EveSentence | 句子（dataId/dataKind/style/canCut） |
| EveGroupData | 結果分組（memberNoAy/probabilityRateAy/pickMember/useMode） |

#### 場景資料集合

```kotlin
data class SceneEveData(
    val npcs: Map<Int, EveNpcPlacement>,        // clickId → NPC
    val doors: Map<Int, EveDoorPlacement>,       // doorId → Door
    val npcEvents: Map<Int, NpcEventData>,       // eveNo → 事件
    val sceneInfos: Map<Int, EveSceneInfo>,      // eveNo → 場景資訊
    val fightDatas: Map<Int, EveFightData>,      // eveNo → 戰鬥配置
    val surfaceDatas: Map<Int, EveSurfaceData>,  // id → Surface
    val groupDatas: Map<Int, EveGroupData>       // eveNo → 分組
)
```

### 2.2 Eve.emg 二進位格式

#### 容器結構

```
[UInt16 LE] 場景數量（如 3823）
每個場景 32B 目錄：
  [1B]  名稱長度
  [23B] 固定名稱欄位
  [4B]  絕對偏移
  [4B]  大小
```

#### 場景資料（偏移 +103 bytes 標頭後）

9 個區段依序解析：

| # | 區段 | 格式 |
|---|------|------|
| 1 | NpcData | Int32 count → 每筆：id(2B) npcId(2B) eventCount(1B) events[](2B each) x(4B) y(4B) close(1B) |
| 2 | GoodsData | Int32 count → 每筆 13B（跳過） |
| 3 | DoorData | Int32 count → 每筆：id(2B) eventCount(1B) events[](2B each) x(2B) y(2B) |
| 4 | MineData | Int32 count → 可變長（跳過） |
| 5 | SurfaceData | UInt16 count → 每筆：id(2B) sentenceCount(1B) sentences[]... optionIndex(1B) optionCount(1B) optionMode(1B) |
| 6 | SceneInfoData | UInt16 count → 每筆 39B 固定：eveNo(2B) name(9B) bgNo(2B) x(2B) y(2B) direction(1B)... |
| 7 | GroupData | UInt16 count → 每筆可變長 |
| 8 | NpcEventData | UInt16 count → 每筆：eveNo(2B) name(9B) whenHappen(4×1B) condCount(1B) conditions[]... |
| 9 | FightData | UInt16 count → 每筆可變長 |

#### NpcEventData 條件/結果內部結構

```
Condition:
  ConditionNo(1B) Class(1B) Parameter(2B) ParameterStyle(1B)
  Ops(1B) Value(4B) CTimeValue(8B) ToResult(1B) AndNum(1B) SubItem(2B)
  ResultCount(1B) → Result[]:

Result:
  GroupNo(2B) ResultNo(1B) Type(1B) Class(1B)
  Param(2B) PStyle(1B) Value(4B) MeanNo(2B)
```

---

## 3. 條件系統（EveConditionEvaluator）

### 3.1 conditionClass 評估邏輯

| Class | 名稱 | 比較方向 | 評估邏輯 |
|-------|------|---------|---------|
| **0** | 無條件 | — | 永遠 `true` |
| **1** | 背包物品 | **反向** | `compareStep(conditionValue, ops, bagItems[param] ?: 0)` |
| **2** | 任務步驟 | 正向 | pStyle=3 → `max(step, flag)`；其他 → `step`；再 `compareStep(actual, ops, val)` |
| **3** | 場景物件可互動狀態 | 直接 | 僅支援已觀測固定形狀 `pStyle=4,ops=5,value=0,subItem=0` |
| **4** | 技能 | 正向 | `skillLevels[param] ?: 0`；`compareStep(actual, ops, val)` |
| **5** | 金錢 | 自訂 | `gold`；`ops=2,value=金額-1` 表示金幣達門檻，`ops=4` 保守視為至少達門檻 |
| **7** | 屬性/狀態 | 正向 | 已支援 param=0(等級) / 2(背包空間：pStyle=1 滿、pStyle=2 有空位、pStyle=4 剩餘格數) / 3(裝備：有→itemId, 無→0) / 5(性別：pStyle=1/2) / 6(武將位置) / 16(騎乘中需下馬)；其他 param 保守 `false` |
| **8** | 戰鬥結果 | 直接 | `pStyle == battleResult`（1=勝 2=敗 3=逃） |
| **9** | 武將持有 | 正向 | `param=0` 時以 `conditionValue` 當 npcId 檢查隨身武將；`param!=0` 為進階武將狀態，暫時保守 `false` |
| **10** | 對話選項 | 直接 | `lastSurfaceId == param && lastChoiceCode == pStyle` |
| **11** | 收驚流程確認 | 直接 | 僅支援已觀測固定形狀 `param=0,pStyle=1,ops=0,value=0,subItem=0` |
| **12** | 場景事件計數 | 正向 | 已支援 `param=0` → 當前 eveNo 完成次數；其他 param 暫時保守 `false` |
| **13** | 軍團/城池/移民 | — | 需軍團、城池或移民系統；目前保守回傳 `false` |
| **14** | 待驗證 | — | 不可直接當 RoleCount；目前保守回傳 `false` |
| **15** | 玩家轉生 | 正向 | 已支援 `param=0,pStyle=0,subItem=0` → `rebornCount`；`param=1` → 武將二轉入口狀態碼 |
| **16** | 非背包倉儲物品 | 反向 | `pStyle=1,subItem=0` → 檢查錢莊+寶庫物品數量；不可重用背包 class1 |
| **17** | 子女系統 | — | 子女存在、等級或出戰狀態；需子女系統，目前保守 `false` |
| **18** | RoleCount 候選 | 正向 | 僅已知 RoleCount 索引讀 `roleCountValues`；其他保守 `false` |
| 6 | 未實作 | — | 保守回傳 `false` |

#### 反向比較說明

目前只有 Class 1 使用 `compareStep(conditionValue, ops, actualValue)` 而非正向的 `compareStep(actualValue, ops, conditionValue)`。

典型用法：
- **Class=1**（背包物品）：`val=0 ops=2` → `0 < qty` → 有此物品
- **Class=1**：`val=0 ops=5` → `0 >= qty` → 沒有此物品（qty=0）
- **Class=12**（事件計數）已改為正向比較：`count=0 ops=2 val=1` → `0 < 1` → 尚未完成才通過

#### Class=3 場景物件可互動狀態

`Eve.emg` 中 `conditionClass=3` 共有 783 筆，全部為
`pStyle=4, ops=5, value=0, subItem=0`，常見於點擊場景物件後
給予物品或金錢，並伴隨 `resultClass=3 pStyle=5` 的客戶端場景物件效果。
目前伺服器只對此固定形狀回傳 `true`，其他 class3 形狀仍保守 `false`。

#### Class=7 param=2 背包空間

`Eve.emg` 中 `conditionClass=7, param=2` 共有 2972 筆，
主要分為：

- `pStyle=1, ops=0, value=0`：背包已滿，常接「背包滿」對話。
- `pStyle=2, ops=0, value=0`：背包有空位，常接正常給物品或任務流程。
- `pStyle=4`：以背包剩餘格數做 `ops/value` 比較。

背包容量對齊 Lua `Item.RefreshRoleCount()`：
`50 + (RoleCount[103] + RoleCount[106] + RoleCount[124]) * 5`，
上限使用 `ItemConstants.MAX_BAG_SLOTS`。

#### Class=7 param=16 騎乘狀態

`Eve.emg` 中 `conditionClass=7, param=16` 共有 77 筆，
全部為 `pStyle=1, ops=0, value=0`，其中 `subItem=1` 固定接
Talk 37776「抱歉,請你下馬好嗎?」。伺服器僅在玩家 `session.mountNpcId != 0`
且 `subItem=1` 時通過；`subItem=2` 在樣本中為空結果條件，目前保守 `false`。

#### Class=11 收驚流程確認

`Eve.emg` 中 `conditionClass=11` 共有 76 筆，全部集中在收驚婆婆事件，
形狀固定為 `param=0, pStyle=1, ops=0, value=0, subItem=0`。
此條件位於 Surface 選擇之後，後續結果包含 Talk 10476「★&@#!%......」
以及任務完成結果。伺服器只對此固定形狀回傳 `true`，其他 class11
形狀仍保守 `false`。

#### Class=15 玩家 / 武將轉生入口

`Eve.emg` 中 `conditionClass=15` 共有 4 筆。場景 55002 的玩家轉生入口使用：

- `param=0, ops=6, value=0`：玩家已轉生時，接轉生說明 Talk。
- `param=0, ops=5, value=0`：玩家未轉生時，接 `resultType=7/resultClass=7/pStyle=12`。

`pStyle=12` 對應 Lua `protocolTable[36][11]`，伺服器發送 `S:036-011`
讓客戶端執行 `UI.Open(UITurn, ETrunMode.Reincarnation, 1)`。

場景 59102 的武將二轉入口使用 `param=1`，其狀態碼由 Lua
`UINpcTurn.CheckTurn(currentTurn=2)` 還原：

- 隨身武將等級 `>= 60`
- 忠誠度 `>= 100`
- 背包持有 `item.kind == 51`
- `attr1.kind == 67 && attr1.value == npcId`
- `attr2.kind == 67`

伺服器端以 `npcSecondReincarnationStatus` 表示入口狀態：

- `0`：至少一名隨身武將符合資格，`ops=5,value=0` 開啟 UI。
- `-1`：沒有符合資格武將，`ops=6,value=0` 顯示說明 Talk。

`pStyle=21` 對應 Lua `protocolTable[44][3]`，伺服器發送 `S:044-003`
讓客戶端執行 `UI.Open(UINpcTurn, 2)`。

### 3.2 compareStep 運算子

| ops | 含義 | 表達式 |
|-----|------|--------|
| 0 | 等於（Eve 慣例） | `actual == expected` |
| 1 | 等於 | `actual == expected` |
| 2 | 小於 | `actual < expected` |
| 3 | 小於等於 | `actual <= expected` |
| 4 | 大於 | `actual > expected` |
| 5 | 大於等於 | `actual >= expected` |
| 6 | 不等於 | `actual != expected` |

> 注意：ops=0 在 Eve 系統中為「等於」，但在 DungeonCriteriaData 中為 `false`。

### 3.3 Class=2 特殊語義（pStyle=3）

```
pStyle=1/2：只看 missionSteps[missionId]
pStyle=3：  actual = max(missionSteps[missionId], missionFlags[missionId])
```

這讓已完成任務（step=0, flag=1）仍能通過 `actual >= 1` 的條件。

---

## 4. AND 鏈解析（EveChainResolver）

### 4.1 buildChains — AND 鏈分組

`andNum` 欄位定義鏈的總長度（含起頭條件自身）：

| andNum | 含義 |
|--------|------|
| 0 | 屬於前一鏈（不應出現在起點，邊界處理為 1） |
| 1 | 獨立條件 |
| 2 | 此條件 + 下一個 = 2 條件 AND |
| N | 此條件 + 後面 N-1 個 = N 條件 AND |

範例：`[andNum=2, 0, 2, 0, 1, 1]` → `[[0,1], [2,3], [4], [5]]`

```kotlin
fun buildChains(conditions: List<EveCondition>): List<List<EveCondition>> {
    val chains = mutableListOf<List<EveCondition>>()
    var i = 0
    while (i < conditions.size) {
        val chainLen = conditions[i].andNum.coerceAtLeast(1)
        val chainEnd = minOf(i + chainLen, conditions.size)
        chains.add(conditions.subList(i, chainEnd))
        i = chainEnd
    }
    return chains
}
```

### 4.2 resolve — 四層排序匹配

對所有匹配的 AND 鏈，按以下優先級選最佳：

| 層 | 排序依據 | 越大越好 | 原因 |
|----|---------|---------|------|
| 1 | **stepScore** — cls=2 條件中最高的 conditionValue | ✓ | step 門檻越高 = 越精確的任務階段 |
| 2 | **鏈長度** — chain.size | ✓ | 更多約束 = 更精確的匹配 |
| 3 | **results 數量** — head.results.size | ✓ | 主 handler（完整對話+action）vs fallback（僅問候語） |
| 4 | **先出現者** — 數據順序 | — | 設計優先級 |

```kotlin
val stepScore = chain.filter { it.conditionClass == 2 }.maxOfOrNull { it.conditionValue } ?: 0
val isBetter = stepScore > bestStepScore ||
    (stepScore == bestStepScore && chain.size > bestLength) ||
    (stepScore == bestStepScore && chain.size == bestLength && resultCount > bestResultCount)
```

### 4.3 chainFilter — 重評時的額外過濾

| 場景 | chainFilter |
|------|------------|
| 首次觸發 | null（無過濾） |
| Surface 選擇後（020-009） | `chain.any { it.conditionClass == 10 }` |
| 戰鬥結束後 | `chain.any { it.conditionClass == 8 }` |

---

## 5. 事件狀態機（EveEventStateMachine）

### 5.1 EventSession

```kotlin
data class EventSession(
    val mapId: Int,                  // 觸發場景
    val eveNo: Int,                  // 事件編號（0=Fallback Talk）
    val npcClickId: Int,             // 觸發 NPC clickId
    val results: List<EveResult>,    // 結果佇列
    var currentIndex: Int = 0,       // 下次發送的 index
    var lastSurfaceId: Int = -1,     // 最後 Surface ID（class=10 用）
    var lastChoiceCode: Int = -1,    // 最後選擇碼（class=10 用）
    var battleResult: Int = 0,       // 戰鬥結果 0/1/2/3（class=8 用）
    var awaitingChoice: Boolean = false,  // 等 020-009
    var awaitingBattle: Boolean = false,  // 等戰鬥結束
    val pendingGuideFlags: MutableList<MissionFlagRecord> = mutableListOf()
)
```

### 5.2 生命週期

```
activate()         → 啟動（覆蓋舊 Session）
advance()          → 取出下一個 result，遞增 index
replaceResults()   → 替換 results 佇列（Surface/Battle 重評後）
complete()         → 正常完成（記錄 eveNo 到 completedEvents）
clear()            → 強制清除（斷線/異常，不記錄完成）
```

### 5.3 完成追蹤（completedEvents）

```
結構：charId → (mapId → Set<eveNo>)
用途：conditionClass=12 param=0 → completedEveNos.size
限制：純記憶體，重登/重啟後歸零 → 需預填充補償
```

- `complete()` 記錄 eveNo（eveNo=0 不計入）
- `clearSceneEvents()` 清除指定場景記錄（進場時呼叫）
- `getCompletedCount()` / `getCompletedEveNos()` 查詢

### 5.4 預填充演算法（estimateCompletedEvents）

解決重登後 completedEveNos 歸零的問題。在 `buildPlayerState()` 時根據任務進度推算：

```
對地圖中每個事件：
  對每條 AND 鏈：
    找 cls=2 條件 → 計算 threshold（最低觸發步驟）
    找同 missionId 的 resultClass=2 pStyle=1 結果 → 累加 increment
    若 currentStep >= threshold + increment → 事件已完成

    或者：cls=2 結果有 pStyle=3 且 missionFlags[missionId] > 0 → 事件已完成
```

**threshold 計算**：

| ops | threshold |
|-----|-----------|
| 0, 1 (==) | conditionValue |
| 5 (>=) | conditionValue |
| 4 (>) | conditionValue + 1 |
| 2, 3 (<, <=) | 0 |

**範例**（10801, missionId=M, currentStep=2）：

| eveNo | threshold | increment | threshold+increment | currentStep>=? | 結果 |
|-------|-----------|-----------|---------------------|---------------|------|
| 2 | 0 (step==0) | 1 | 1 | 2 >= 1 ✓ | completed |
| 1 | 1 (step>=1) | 1 | 2 | 2 >= 2 ✓ | completed |
| 4 | 2 (step>=2) | 1 | 3 | 2 >= 3 ✗ | not completed |

→ completedEveNos = {1, 2}（count=2）

---

## 6. 結果分發（dispatchResult）

### 6.1 resultType 處理

| Type | 名稱 | 伺服器行為 |
|------|------|-----------|
| **0** | 動作 | 發 S:020-001 + 依 resultClass 執行伺服器邏輯（§6.2） |
| **1** | 對話 | 發 S:020-001（客戶端顯示 Talk 對話框） |
| **2** | 門傳送 | `handleDoorWarp()` — 完整傳送流程（§7） |
| **3** | 戰鬥 | `handleBattleTrigger()` — 啟動 PvE（§8） |
| **5** | 動畫 | 發 S:020-001（客戶端播動畫） |
| **6** | 互動 | 記錄 lastSurfaceId + 設 awaitingChoice → 發 S:020-001 |
| **7** | 伺服器特殊 | 不送客戶端；由伺服器依 resultClass/pStyle 分流 |
| **8** | 無 handler | 靜默跳過 + 自動 advance（客戶端無 EventHandler[8]） |
| **9** | 無 handler | 靜默跳過 + 自動 advance（客戶端無 EventHandler[9]） |
| **10** | 無 handler | 靜默跳過 + 自動 advance（客戶端無 EventHandler[10]） |

### 6.2 resultClass 伺服器邏輯（僅 type=0 時）

| Class | 名稱 | pStyle | 行為 |
|-------|------|--------|------|
| **1** | 物品 | 0=依 resultValue 正負增減 | `+n` 給予 / `-n` 收回，用於交換道具 |
|       |      | 1=依 resultValue 正負增減 | `+n` 給予 / `-n` 收回 |
|       |      | 2=收回 | `inventorySystem.removeFromBag()` → S:015-022 |
| **2** | 任務 | 1=增加步驟 | `questSystem.addMissionStep()` → S:024-001 |
|       |      | 2=減少步驟 | `questSystem.decMissionStep()` → S:024-002（歸零=完成） |
|       |      | 3=完成任務 | `questSystem.completeMission()` → S:024-004 + S:024-005 |
|       |      | 4=直接設定旗標 | `resultValue` 是 `Mark_C.dat.bitId` → S:024-005 |
|       |      | 5=移除任務 | 刪除進行中任務，不設定完成旗標 |
| **3** | NPC隊伍 | 1=加入 | `petSystem.addFollowNpc()` → S:016-004 + S:016-005 |
|       |         | 3=隱形 / 4=顯現 / 5=換圖序 | 客戶端自行處理 |
| **4** | 技能 | 1=學習/升級 | `skillSystem` 更新 → S:021-000 |
| **5** | 金錢 | 1=增加 / 2=扣除 | `MoneySystem` 更新 → S:023-002 / S:023-003 |
| **7** | 玩家 | type=7 pStyle=1 | 開啟寶庫 |
|       |      | type=7 pStyle=2 | 開啟錢莊 |
|       |      | type=7 pStyle=3 | 開啟 NPC 賣場 |
|       |      | type=7 pStyle=4 | 開啟客棧武將倉庫 |
|       |      | type=7 pStyle=5 | 住宿確認 |
|       |      | pStyle=6 | 金錢獎勵；type=0/type=7 皆可處理 |
|       |      | type=7 pStyle=10 | 開啟無界排行榜 → S:036-010 |
|       |      | type=7 pStyle=12 | 開啟玩家轉生介面 → S:036-011 |
|       |      | type=7 pStyle=13 | 開啟殺敵點兌換商店 → S:036-013 |
|       |      | type=7 pStyle=15 | 水戰奉邑續行入口 → S:036-015 |
|       |      | type=7 pStyle=21 | 開啟武將二轉介面 → S:044-003 |
|       |      | type=7 pStyle=27 | 尋找惡魔巴豆妖錯誤訊息 → S:020-027 |
|       |      | type=7 pStyle=29 | 尋找惡魔巴豆妖開始訊息 → S:020-029 |
|       |      | type=7 pStyle=30 | 聖誕節活動訊息 → S:020-030 |
|       |      | type=7 pStyle=32 | 擂台賽錯誤訊息 → S:020-032 |
|       |      | type=7 pStyle=35 | 元宵節錯誤訊息 → S:020-035 |
|       |      | type=7 pStyle=39 | 未知活動訊息 → S:020-039 |
|       |      | type=0 pStyle=1..5 | 未確認，保守不觸發特殊 UI |
| **8** | 獎勵武將 | 1=加入(canGrow=1) | `petSystem.addFollowNpc()` → S:016-004 + S:016-005 |
|       |      | 2=離開 | `petSystem.removeFollowNpcByNpcId()` → S:016-006 |
| **10** | 流程 marker | 1/2 | 明確 no-op，自動推進 |

### 6.3 教學觸發標記延遲機制

當 resultClass=2 pStyle=1 將任務從 step=0 推進到 step=1 時：
1. 檢查 `markDefs[missionId].bitId` 是否有對應教學（`markFlagToGuideId`）
2. 若有 → 設定旗標 + 暫存到 `EventSession.pendingGuideFlags`
3. 事件結束時（`completeAndEnd`）統一發送 S:024-005
4. 確保客戶端 UITeach 不被事件 UI 阻擋

### 6.4 GroupData 機率選擇

當 `resultGroupNo > 0` 時引用 GroupData：

```
1. 收集所有不同的 groupNo
2. 每個 group 做一次選擇（selectMember）：
   - pickMember > 0 → 預設選擇
   - 單成員 → 直接回傳
   - 權重全零 → 保留全部（-1）
   - 正常 → 依 probabilityRateAy 加權隨機
3. 過濾 results：groupNo=0 保留 + 被選中的 resultNo 保留
```

---

## 7. Door 傳送流程（handleDoorWarp）

```
1. 發 S:020-001（resultType=2 結果）
2. 查 sceneInfos[result.parameter] → 取得目標場景/座標
3. 發 S:020-007（EventChangeScene）
4. worldManager.leaveMap() → worldManager.enterMap()
5. characterRepository.updatePosition()（持久化）
6. 發 S:012-000（玩家瞬移）
7. 發 S:007-000（場景切換）
8. 發 S:022-004（MapNpc 初始化）
9. 記錄 lastDoorWarpMapId = 舊地圖, lastDoorWarpTime = now
10. completeAndEnd()
```

### MeetDoor 冷卻機制

**目的**：防止到達點在回程門上時立即彈回（門 bounce-back）。

```kotlin
if (subKind == 8 &&
    session.mapId == session.lastDoorWarpMapId &&    // ← 同地圖才擋
    System.currentTimeMillis() - session.lastDoorWarpTime < 2000L) {
    // 忽略
}
```

- **同地圖**：傳送後 2 秒冷卻（防回彈）
- **跨地圖**：不受冷卻影響（如 10801→10851 的 MeetDoor 正常觸發）

---

## 8. 戰鬥流程（handleBattleTrigger）

```
1. 查 fightDatas[result.resultMeanNo] → 取得敵方配置
2. 發 S:020-001（resultType=3，客戶端 SetSession(true)）
3. stateMachine.setAwaitingBattle(true)
4. battleManager.createPveBattle()

── 非同步等待戰鬥結束 ──

5. BattleManager 回呼 → resumeAfterBattle(playerWin)
6. 重評：chainFilter = { chain.any { it.conditionClass == 8 } }
   - 匹配 → replaceResults → 繼續分發
   - 無匹配 + 勝利 → autoCaptureFromEvent() + handleNextEvent()
   - 無匹配 + 敗北 → 事件結束
```

### autoCaptureFromEvent

針對無 class=8 後續鏈的事件戰鬥（如教學抓武將）：
- 掃描事件中 cls=9 條件引用的 npcId
- 自動加入武將隊伍

---

## 9. 任務系統（QuestSystem）

### 9.1 操作介面

| 方法 | 對應 Lua | 行為 |
|------|---------|------|
| `addMissionStep(charId, missionId, delta)` | MarkManager.AddMissionStep | 步驟累加，不存在則建立 |
| `decMissionStep(charId, missionId, delta)` | MarkManager.DecMissionStep | 步驟遞減，歸零=完成 |
| `completeMission(charId, missionId)` | pStyle=3 | 直接完成（移除+旗標） |
| `removeMission(charId, missionId)` | — | 刪除進行中任務 |
| `setFlag(charId, bitIndex, bitValue)` | — | 設定完成標記 |
| `getMissions(charId)` | — | 查詢進行中任務 |
| `getFlags(charId)` | — | 查詢完成標記 |

### 9.2 任務完成流程

```
decMissionStep:
  current=0 → 不觸發完成（任務不存在）
  current-delta=0 → removeMission + setFlag(bitId, 1) → completed=true
  current-delta>0 → upsertMission(newStep) → completed=false
```

### 9.3 持久化表結構

**CharacterMissions**（主鍵：charId + missionId）

| 欄位 | 型別 | 說明 |
|------|------|------|
| charId | Long | 角色 ID |
| missionId | Int | 任務 ID |
| step | Int (default=1) | 當前步驟 |

**CharacterMissionFlags**（主鍵：charId + bitIndex）

| 欄位 | 型別 | 說明 |
|------|------|------|
| charId | Long | 角色 ID |
| bitIndex | Int | Mark_C.dat bitId |
| bitValue | Int (default=1) | 標記值 |

### 9.4 旗標查詢鏈

```
MarkDef: missionId → bitId
DB:      charId + bitId → bitValue
State:   missionFlags[missionId] = bitValue（buildPlayerState 建構）
```

---

## 10. 核心流程圖

### 10.1 事件觸發→完成

```
Client C:020-{1~8}  (triggerID)
       │
       ├── subKind=4/8 (Door) ─────→ doors[triggerID].events → resolve
       ├── subKind=2 (BumpNpc) ────→ WhenHappen[1] filter → resolve
       ├── subKind=3 (ScanNpc) ────→ WhenHappen[2] filter → resolve
       └── subKind=1 (ClickNpc) ──→ npcs[triggerID].events → resolve
                                     ├── 失敗 → BumpNpc fallback
                                     └── 失敗 → findFallbackTalk
       │
       ▼ resolveAndActivate()
  buildPlayerState() → EveChainResolver.resolve() → applyGroupData()
       │
       ▼ stateMachine.activate() + advance()
  dispatchResult(first result)
       │
       ├── type=0/1/5 → sendCommonEvent + server logic
       │   └── Client 020-006 → advance() → dispatch next
       │
       ├── type=2 → handleDoorWarp → complete
       │
       ├── type=3 → handleBattleTrigger → await
       │   └── BattleManager callback → resumeAfterBattle → re-evaluate
       │
       ├── type=6 → awaitingChoice=true
       │   └── Client 020-009 → handleEventChoice → re-evaluate
       │
       └── type=8/9/10 → 靜默跳過 → auto advance
       │
       ▼ results 佇列空
  completeAndEnd() → sendEventEnd + pending guide flags
```

### 10.2 Surface 選擇流程

```
Server: type=6 → setLastSurfaceId + setAwaitingChoice → S:020-001
Client: 顯示選單 → 選擇 → C:020-009 (code)
Server: setLastChoiceCode(code)
        → buildPlayerState (含 lastSurfaceId + lastChoiceCode)
        → EveChainResolver.resolve(chainFilter = cls=10)
        → replaceResults → advance → dispatch
```

選擇碼對照：
| 碼 | 含義 |
|---|------|
| 20 | Yes |
| 21 | No |
| 30~39 | Option 1~10 |
| 40 | Cancel |
| 61+ | Option 11+ |

### 10.3 戰鬥恢復流程

```
Server: type=3 → setAwaitingBattle → S:020-001 → createPveBattle
Client: 進入戰鬥
BattleManager: callback → resumeAfterBattle(playerWin)
Server: setBattleResult(1=勝/2=敗)
        → buildPlayerState (含 battleResult)
        → EveChainResolver.resolve(chainFilter = cls=8)
        ├── 匹配 → replaceResults → dispatch
        ├── 無匹配 + 勝 → autoCaptureFromEvent + handleNextEvent
        └── 無匹配 + 敗 → clear + sendEventEnd
```

---

## 11. C→S / S→C 封包對照

### C→S（mainKind=20）

| SubKind | 名稱 | 資料 |
|---------|------|------|
| 1 | ClickNpc | triggerID (2B LE) |
| 2 | BumpNpc | triggerID (2B LE) |
| 3 | ScanNpc | triggerID (2B LE) |
| 4 | ClickDoor | triggerID (2B LE) |
| 5 | NotInUse | triggerID (2B LE) |
| 6 | NextEvent | 空 |
| 7 | Mine | triggerID (2B LE) |
| 8 | MeetDoor | triggerID (2B LE) |
| 9 | EventChoice | code (1B) |

### S→C

| Main-Sub | 名稱 | 用途 |
|----------|------|------|
| 020-001 | CommonEvent | 發送 EveResult（15B） |
| 020-007 | EventChangeScene | 場景切換標頭 |
| 020-008 | EventEnd | 事件結束 |
| 012-000 | PlayerTeleport | 瞬移 |
| 007-000 | SceneChange | 場景切換 |
| 022-004 | MapNpcInit | NPC 擺放初始化 |
| 024-001 | AddMissionStep | 任務步驟增加 |
| 024-002 | DecMissionStep | 任務步驟減少 |
| 024-004 | RemoveMission | 刪除任務 |
| 024-005 | SetMissionFlags | 設定任務旗標 |
| 015-003 | SetBagItem | 物品給予 |
| 015-022 | ReduceBagItem | 物品收回 |
| 016-004 | FollowNpcAdd | 武將加入 |
| 016-005 | FollowNpcFullData | 武將完整資料 |
| 016-006 | FollowNpcRemove | 武將離開 |
| 021-000 | PlayerSkillData | 技能資料 |

---

## 12. 已知邊界案例與解法

### 12.1 completedEveNos 重登歸零

**問題**：completedEvents 純記憶體，重登/重啟後 cls=12 count=0。
**解法**：`estimateCompletedEvents()` 在 `buildPlayerState()` 中根據任務進度預填充。

### 12.2 MeetDoor 跨地圖冷卻

**問題**：全局 2 秒冷卻擋住跨地圖 MeetDoor。
**解法**：`lastDoorWarpMapId` 欄位，冷卻只在同地圖生效。

### 12.3 AND 鏈同分匹配

**問題**：stepScore 和鏈長度相同時，fallback 問候語覆蓋主 handler。
**解法**：第三層 tiebreaker — results 數量越多 = 主 handler。

### 12.4 Fallback Talk

當 ClickNpc 的 NPC 事件全部不匹配時：
1. 嘗試 BumpNpc（WhenHappen[1]）事件中含 Talk 的
2. 嘗試 NPC 事件中第一個 resultType=1 的 Talk
3. 都沒有 → sendEventEnd

### 12.5 type=8/9/10 無 handler 靜默跳過

客戶端 `EventHandler.lua` 只定義 0..6。resultType=8/9/10 會出現在 Eve.emg，
但客戶端沒有對應 handler；伺服器靜默跳過並自動推進到下一個 result。

### 12.6 教學觸發標記延遲

任務從 step=0 → step=1 觸發的教學旗標（S:024-005）延遲到事件結束後發送，避免 UITeach 被事件 UI 阻擋。

### 12.7 戰鬥進行中的 020-006

`awaitingBattle=true` 時忽略 020-006（NextEvent），防止在戰鬥中意外推進或完成事件。

### 12.8 GroupData 全零權重

`probabilityRateAy` 全零時回傳 `-1`（保留全部成員），表示設計者未指定機率，這些結果是一個整體。
