# DVT 10988 — fix two empty Sun Shangxiang follower-condition navigation targets

Checkpoint 2026-10-12, PR #41, after commit `70a2dfb`. This is a *condition target* repair, not a mission END repair. No game character E2E testing this round.

## Confirmed original DVT evidence

- In `File.dat/Data-ios/IOS_MarkStep_C.dat`, mission10988 stages3 and4 each require condition kind1 (following NPC13038, count1). Both CONDITION_1 targets were exactly `(0,0,0,0,0)`, **even though both ENDs were already `(18501,1870,1800,0,0)`**.
- DVT `lua/Logic/MarkManager.lua`: when follow NPC is not present, `Navigation` gives priority to the first unmet condition's target and replaces the END destination with that target. For these stages the client therefore cannot start navigating despite having a valid battle location.
- Original DVT `Eve.emg` map **18501** NPC placement **4**, NPC type **10240**, position **(1870,1800)**, real `ClickNpc` Eve7 and Eve8. Eve7 has original class2 mission10988 stage3 and stage4 gates and battle outcomes.
- The **same quest's stage6** already includes its original kind1 follower NPC13038 CONDITION_1 target `(18501,1870,1800,0,0)`. Thus copying this **existing quest's source battle-area waypoint**, rather than guessing NPC type13038's local placement (there are different appearances of this NPC), preserves quest logic.
- No claim that arriving re-acquires a lost follower or wins the battle: original `kind1` requirement still blocks completion. Source NPC13038 also appears separately in scene18101 and scene18501; no new auto-capture is fabricated.

## Exact scope

Patch only **10988 step3 CONDITION_1** and **10988 step4 CONDITION_1** from `(0,0,0,0,0)` to `(18501,1870,1800,0,0)` via guarded `tools/patch_dvt_10988_follow_battle_routes_20261012.py`, requiring source NPC and Eve gates and source step6 parity; atomic backup and hash locks. Exactly **12 changed bytes** per binary across two eight-byte fields. END fields, NPC/Eve/Lua/Kotlin, rewards, mission step, item requirements unchanged.

- Server/Git MarkStep length892087: SHA256 `e48a4b7f50f6dfc4d3b902279bdda5efea0c783c3eb46f7c300a80db1bec13ad` → `a2ddb57f24debb314d2d5bc59b7721d99561717e6bad3252c35c1280150c80da`.
- Local client staging localized two identical data editions length928425: SHA256 `93afa327618e1632b80bb0ddcd1fb1da73e3a6f0584da2046048d0be1ac35acc` → `511563050ea38ca86e8b85a9ec954fb1b8165ec12403b34521f3acf6b33e40f7`.
- All MarkStep rows **4505**; source-located targets **5932 → 5934**; unlocated condition targets **87 → 85**. Raw blank-END **63** unchanged, including 8 condition-guided (while unmet) and 55 without condition-guidance; zero nonexistent static scene/NPC/door references.
- Four independent regression tests in `tools/test_dvt_10988_follow_battle_routes_20261012.py`; adjusted prior version-locked full-file checks. All **157** tests passed locally.

## 4 Halloween A Dou escort condition cases intentionally NOT patched

Quest12444 steps1–4 all require follower NPC38265. Original Eve has four different NPC38265 placements in scene54947 plus separate scenes54924–54927, with *stage-dependent* branches for correct/wrong candidate, including a unique follow effect in scene54947 NPC1 branch for stage1. Pointing every stage at the same NPC or the return NPC38266 would bypass the puzzle, misroute players or break capture. These four cases require deeper stage-specific source logic and remain unlocated, not false claims of bugs fixed.

## Manual consolidated test (not executed)

For 10988 stages3 and4 both with NPC13038 already following and with NPC13038 absent, click the quest; path should lead to the real scene18501 pirate encounter, never to origin (0,0). The original battle can advance only with its required conditions. Check cross-map route, cancellation and quest step change; avoid automatically completing the battle or reclaiming companion, and ensure no reward duplication. Test stage6 follower re-acquisition routing remains unchanged.
