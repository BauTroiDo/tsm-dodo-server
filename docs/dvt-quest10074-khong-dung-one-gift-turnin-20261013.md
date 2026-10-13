# DEV bugfix: Khổng Phương đưa Lễ / Khổng Dung (mission 10074, display 10075)

Date: 2026-10-13. Source: DVT `gamedata/Eve.emg`, `IOS_MarkStep_C.dat`, `lua/Data/DungeonCriteriaData.lua`, and DEV runtime log `logs/tsm_dodo.log`.

## Proven reproduction

- User screenshot: mainline **Khổng Phương đưa Lễ**, map **Đường Lớn Bắc Hải**, NPC **Khổng Dung** says “Ngươi là ai? Tới đây có chuyện gì?” but cannot advance.
- Original active mission ID **10074**, user-facing Mark alias **10075** (`MarkDef[10074].bitId=0`). Mission step1 requires **item 30002 x1**. Source scene **11101 / NPC1 / Eve2** awards exactly one item 30002. Source scene **11012 / NPC1 (NPC ID 11002) / Eve8** checks condition16: `class2,param10074,pStyle1,ops5,val1` AND condition17: `class1,param30002,pStyle1,ops4,val1`. Its result sequence includes TALKs, `class1/item30002/value=-1`, and `class2/mission10074/pStyle1/value=+1`. Next stop after step2 is quest giver map11101.
- Live DEV log around **09:44** on 2026-10-13 shows `CaoYênDao`, `itemId=30002 qty=1 ops=4 val=1 → false`; later at **10:57:32** and **11:42:34**, NPC click1 (events 8,12,15) with `missions=...10074=1...` reports `eveNo=8 không khớp` and **`Fallback Talk charId=31 clickId=1 talkId=12100`**. This is exact root cause, not pathfinding or a missing client button.
- Kotlin `EveConditionEvaluator` previously routed item `ops=4` to generic `compareStep(1,4,1)`, returning `1>1=false`. In DVT Eve item quantity gating, `ops4` must be inclusive for a one-item delivery. This differs from independent `lua/Data/DungeonCriteriaData.CompareResult` strict-greater behavior; do not change `compareStep` or unrelated mission/numeric comparators.

## Code fix and permanent tests

- `EveConditionEvaluator.kt`, only `conditionClass=1` item quantity branch: explicit `4 -> qty >= cond.conditionValue`. Retain class1 `ops2` strictly greater and other branches unchanged.
- New `DvtKongRongGiftTurnInRegressionTest.kt` tests real Eve.emg original giver grant+1, recipient NPC/AND chain, exactly one item selects Eve8 condition16 (nine results) and mission increment, zero items cannot advance, and comparator contrast `ops4 >=` vs `ops2 >`.
- New `tools/test_dvt_khong_dung_gift_delivery_20261013.py` checks original DVT source, code branch, Kotlin cases; registered in DVT data audit workflow.

## DEV deployment and rollback plan

The DEV source `gamelogic/src/main/kotlin/tw/tsm/gamelogic/systems/EveConditionEvaluator.kt` is **already dirty with unrelated edits**, compared to git baseline. Pre-fix SHA256 recorded **B59417A95C85A16BD9398AB41D58F81ECBB5DD3FA82371880C9A3ED63C054475**. Preserve it exactly by copying one per-file backup under `C:\Server-Game\tsm_dodo\backups\` with timestamp and SHA. After both GitHub workflows PASS, apply only the unique four-line comparison switch to existing DEV file, compile/test carefully without checking out/resetting the dirty DEV worktree, then restart DEV service only after a successful build. Verify running port/process and matched UUID/mission data. If unable to compile or restart safely, leave the existing runtime and note deployment pending. **Do not change item inventory or quest step by GM/SQL**; the next genuine NPC interaction should consume item 30002 and move mission10074 to step2.

The staged C025 SQL trade modules on PR #41 remain isolated/unwired. This fix is only Eve item-condition semantic parity.
