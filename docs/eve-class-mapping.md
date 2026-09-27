# Eve Class 語意覆蓋分析

日期：2026-05-06

本文件記錄 `Eve.emg` 的 `conditionClass`、`resultType`、`resultClass`
語意對照與目前伺服器覆蓋狀態。Lua 與 Eve 資料是唯一真相來源；
未被 Lua 或實際資料證明的語意不得直接硬寫成正式邏輯。

## 來源

- `gamedata/Eve.emg`
- `TSClient/TSMonline_TW/lua_decrypted/Data/Eve/Eve_NpcEventData.lua`
- `TSClient/TSMonline_TW/lua_decrypted/Logic/Event/EventHandler.lua`
- `TSClient/TSMonline_TW/lua_decrypted/Logic/Event/EventManager.lua`
- `TSClient/TSMonline_TW/lua_decrypted/Logic/CheckCondition.lua`
- `TSClient/TSMonline_TW/lua_decrypted/Logic/RoleCount.lua`
- `docs/eve-system.md`
- `gamelogic/.../EveConditionEvaluator.kt`
- `gamelogic/.../EveResultExecutor.kt`
- `gamelogic/.../EveFlowController.kt`

## 解析基準

- Scene 目錄數：3823
- 成功解析 Scene：3823
- 解析錯誤：0
- 已觀測 `conditionClass`：0, 1, 2, 3, 4, 5, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18
- 已觀測 `resultType`：0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10
- 已觀測 `resultClass`：0, 1, 2, 3, 4, 5, 7, 8, 10, 11, 12, 13, 14, 15

## 信心等級

- 已確認：Lua 直接有處理器，或目前封包與實際流程已跑通。
- 高信心推論：Lua 欄位名、事件上下文、參數分布一致，但仍需測試固定。
- 中信心推論：有資料模式，但缺少完整客戶端執行語意。
- 待驗證：不能直接實作成正式效果，只能加診斷或明確 no-op。

## 重要修正

1. `conditionClass=14` 目前文件與程式把它當成 RoleCount，這個判斷需要降級。
   `Eve.emg` 中 class14 幾乎集中在 `param=0, pStyle=0, value=0`，
   不像 `RoleCount.lua` 的索引分布。
2. `conditionClass=18` 有 `param=668` 範例，對得上
   `RoleCount.lua` 的 `ERoleCount.ReUseAct_1 = 668`。RoleCount 更可能在
   class18，或至少 class18 的部分情境使用 RoleCount。
3. `CheckCondition.lua` 的 `ECondition.RoleCount=14` 是通用條件系統，不可直接
   1:1 套用到 Eve `conditionClass`。Eve class1 在資料中是物品條件，
   但 `CheckCondition.ECondition.Gold=1`，兩者已明確不一致。
4. `EventHandler.lua` 只定義 `EventHandler[0]` 到 `[6]`。`resultType=8/9/10`
   雖存在於 Eve 資料，但客戶端沒有同名 handler；伺服器不得盲目送給客戶端。
5. `conditionClass=7, param=2` 不可視為轉生次數。實際樣本主要是背包空間：
   `pStyle=1` 接「背包滿」對話，`pStyle=2` 接正常給物品或互動流程。
   背包容量需使用 `RoleCount[103/106/124]` 對齊 Lua `Item.RefreshRoleCount()`。

## conditionClass 覆蓋表

| Class | 筆數 | 狀態 | 語意判斷 | 後續處理 |
| --- | ---: | --- | --- | --- |
| 0 | 17857 | 已確認 | 無條件 | 保持 true |
| 1 | 4704 | 已確認 | 物品持有數量，`param=itemId` | 補 pStyle 分布測試 |
| 2 | 29381 | 部分 | 任務步驟或任務旗標 | 補 pStyle 4/10；確認 pStyle 3 flag 合併 |
| 3 | 783 | 高信心 | 場景物件可互動狀態；資料全部固定為 `pStyle=4,ops=5,value=0,subItem=0`，常接 `resultClass=3 pStyle=5` | 已補固定形狀，其他形狀保守 false |
| 4 | 36 | 高信心 | 技能條件，`param=skillId` | 第一批實作，從 `SkillSystem` 讀技能等級 |
| 5 | 142 | 高信心 | 金錢條件，`param=0/pStyle=1`；常見 `ops=2,value=金額-1` 表示身上金幣足夠 | 已接 `PlayerEventState.gold`，少數 `ops=4` 保守視為至少達門檻 |
| 7 | 8531 | 部分 | 玩家狀態、等級、背包空間、裝備、性別、武將所在、騎乘狀態；未知 param 不可因 `ops=0,value=0` 誤通過 | 已補 param=2 背包空間（含 pStyle=4 剩餘格數）、param=5 性別、param=16 騎乘下馬；續補 param 8/12/18 等分支 |
| 8 | 6242 | 已確認 | 戰鬥結果分支 | 保持 battleResult 比對 |
| 9 | 2102 | 部分 | `param=0` 是隨身武將持有；`param!=0` 疑似武將等級、位置或狀態條件，不可用 `value=0` 誤通過 | 補 pStyle 1/2/3 與 param 非 0 情境 |
| 10 | 6055 | 已確認 | Surface 選項分支，`param=surfaceId`、`pStyle=choiceCode` | 保持現況並補 regression |
| 11 | 76 | 高信心 | 收驚婆婆流程確認；資料固定 `param=0,pStyle=1,ops=0,value=0` | 已補固定形狀，其他形狀保守 false |
| 12 | 662 | 部分 | `param=0` 是當前 eveNo 完成次數；`param!=0` 尚未確認，不可用 0 值誤通過 | 補 param 1/2/3/4 與 pStyle 3/5/6 |
| 13 | 99 | 已定位但未實作 | 軍團、城池、移民身分條件；樣本集中在城池設施、正副軍團長、城池軍團成員與移民資格 | 需先有軍團/城池/移民系統，暫時保守 false |
| 14 | 291 | 中信心 | 不是已確認 RoleCount；多數為零值條件 | 先改文件與診斷，不再標已確認 |
| 15 | 4 | 已支援 | `param=0` 是玩家轉生次數，場景 55002 以 `ops=6,value=0` 擋已轉生玩家、`ops=5,value=0` 開啟玩家轉生介面；`param=1` 是武將二轉入口狀態碼，依 Lua `UINpcTurn.CheckTurn(currentTurn=2)` 檢查隨身武將等級/忠誠與二轉道具 | `param=0` 對應 `rebornCount`；`param=1` 對應 `npcSecondReincarnationStatus`（0=可開啟，-1=顯示說明） |
| 16 | 9 | 高信心 | 非背包倉儲物品檢查；`pStyle=1,subItem=0` 檢查錢莊+寶庫數量，與 class1 背包檢查互補 | 其他 pStyle/subItem 形狀仍保守 false |
| 17 | 16 | 已定位但未實作 | 子女系統條件，包含子女是否存在、子女等級與子女出戰分支 | 需先有子女系統，暫時保守 false |
| 18 | 11 | 高信心但需分流 | 部分樣本像 RoleCount；也有低 param 分支 | 只對已證明 RoleCount 索引實作 |

## resultType 覆蓋表

| Type | 筆數 | 客戶端 handler | 狀態 | 後續處理 |
| --- | ---: | --- | --- | --- |
| 0 | 55156 | 有 | 已確認 | 伺服器執行 resultClass 效果 |
| 1 | 70455 | 有 | 已確認 | Talk，送 S:020-001 |
| 2 | 10058 | 有 | 已確認 | Door，走傳送流程 |
| 3 | 23504 | 有 | 已確認 | Battle，等待戰鬥結果 |
| 4 | 211 | 有 | 部分 | NPC 移動，客戶端處理為主 |
| 5 | 230 | 有 | 部分 | Movie，客戶端處理為主 |
| 6 | 2617 | 有 | 已確認 | Surface，等待選項 |
| 7 | 727 | 無直接 handler | 伺服器特殊分流 | 需逐 pStyle 保持明確語意 |
| 8 | 226 | 無 | 靜默跳過 | 不送客戶端；自動 advance |
| 9 | 1611 | 無 | 靜默跳過 | 不送客戶端；自動 advance |
| 10 | 115 | 無 | 靜默跳過 | 不送客戶端；自動 advance |

## resultClass 覆蓋表

| Class | 筆數 | 狀態 | 語意判斷 | 後續處理 |
| --- | ---: | --- | --- | --- |
| 0 | 23734 | 已確認 | 無伺服器效果 | 保持自動推進 |
| 1 | 8248 | 部分 | 物品給予或扣除；pStyle 0/1 依 resultValue 正負增減，pStyle 2 明確扣除 | pStyle 3 仍待驗證 |
| 2 | 11852 | 部分 | 任務步驟、完成、旗標；pStyle 4 直接設定 resultValue 指定 bitId，pStyle 5 只移除任務 | pStyle 0 單筆異常資料待驗證 |
| 3 | 90514 | 部分 | NPC 隊伍、顯隱、移動或客戶端演出 | 補 pStyle 0/2/6 與 type 搭配 |
| 4 | 34 | 已確認 | 技能學習或升級 | 補 conditionClass=4 對應測試 |
| 5 | 402 | 高信心 | 金錢增減，`param=0`；pStyle=1 增加，pStyle=2 扣除 | 已接 `MoneySystem` 與金錢封包 |
| 7 | 28125 | 部分 | 玩家、UI、商店、客棧、銀行、金錢；特殊 UI 只允許 `resultType=7`，`pStyle=6` 為金錢獎勵，`pStyle=10` 為無界排行榜 `S:036-010`，`pStyle=12` 為玩家轉生 UI `S:036-011`，`pStyle=13` 為殺敵點兌換商店 `S:036-013`，`pStyle=15` 為水戰奉邑續行入口 `S:036-015`，`pStyle=21` 為武將二轉 UI `S:044-003`，`pStyle=27/29/30/32/35/39` 為無 payload 活動訊息 | 其他 pStyle 7 以上仍需逐一對照 Lua/協定 |
| 8 | 485 | 已確認 | 獎勵武將加入或離開 | 保持現況並補失敗路徑測試 |
| 10 | 1337 | 明確 no-op | type=0、param/value/meanNo 皆為 0，pStyle 只見 1/2；客戶端無分支 | 伺服器靜默 no-op 後自動推進 |
| 11 | 35 | 待驗證 | 稀有特殊效果 | 建立範例集 |
| 12 | 21 | 待驗證 | 疑似親子或特殊 NPC | 對照 Talk showRole 分支 |
| 13 | 89 | 待驗證 | 稀有特殊效果 | 建立範例集 |
| 14 | 33 | 待驗證 | 稀有特殊效果 | 建立範例集 |
| 15 | 1 | 待驗證 | 單筆特殊效果 | 單獨追事件上下文 |

## 優先順序

1. 先建立 Eve class 範例與覆蓋測試，避免「已觀測但未分類」被忽略。
2. 實作高信心且不會大幅改變流程的類別：
   `conditionClass=4` 技能條件、`resultClass=1/2` 已知 pStyle 補齊、
   `resultType=8/9/10` 明確 skip 策略。
3. 修正 RoleCount 判斷：
   class14 不再標成已確認 RoleCount；class18 只在參數命中
   `RoleCount.lua` 已知索引時才讀 RoleCount。
4. 集中處理高影響缺口：
   `resultClass=10`、`resultClass=5`、`conditionClass=5/13/16/17/18`。
5. 對每個待驗證類別建立最小事件樣本：
   sceneId、eveNo、conditionNo、resultGroupNo、Lua 對照、預期行為。

## 驗收標準

- 每個實際出現在 `Eve.emg` 的 `conditionClass` 都必須有：
  已實作邏輯、明確 no-op、或待驗證診斷項目。
- 每個實際出現在 `Eve.emg` 的 `resultType/resultClass` 都必須有：
  伺服器效果、客戶端轉發、明確 skip，或待驗證診斷項目。
- 正常主線任務流程不得出現未分類類別的 WARN。
- 未驗證語意不得以「猜測通過」方式讓條件回傳 true。
- 每次新增語意必須有 Eve 範例測試或純函數單元測試。
