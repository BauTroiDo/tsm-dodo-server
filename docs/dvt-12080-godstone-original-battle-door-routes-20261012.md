# Quest12080 — DVT original two Door battle-gated Godstone waypoints

2026-10-12 VN. User instruction: continue source-backed fixes, persist PR #41 and consolidated **deferred** in-game E2E ledger; no character-level play tests until one combined pass.

## Source proof

Authoritative original `gamedata/Eve.emg` defines stage-specific Door triggers for quest12080 whose original `File.dat/Data-ios/IOS_MarkStep_C.dat` has blank END. Missing END prevents the original client `lua/Logic/MarkManager.lua` from navigating when mission-clicked.

- Stage4, "Trì Quốc Thiên nói hình như nhìn thấy đá thần ở Thiên Kỳ Phong": **scene32839 Door1 at (640,700)** attached to **Eve1**, new END **`(32839,640,700,2,1)`**; original Door warp leads into scene32837.
- Stage7, "Lão long vương kim giác lại cảm nhận thấy đá thần, đi xung quanh xem sao": **scene32837 Door1 at (820,540)** attached to **Eve1**, new END **`(32837,820,540,2,1)`**; original Door warp returns to scene32839.

Each original Eve is `when_happen=[False,True,False,False]`, bound to the actual Door, not an NPC. Each source event has **six** conditionClass2/quest12080/pStyle1/ops5/stage branches, plus battle-related conditionClass8, AND lengths 2 and 3. Original alternate results can conditionally advance or alter quest stage, reveal/hide characters, and start the original battle. **Do not replace or bypass ANY source conditions, auto-finish the quest, or assume a battle has been won**. Both source Door targets direct to a *trigger*, not guaranteed completion. Original intermediate steps 3/5/6/8 retain their current legitimate waypoints. The follower/bonus/reward effects elsewhere in Godstone quest are untouched.

Read-only Kotlin `EveTriggerRouter.routeDoor` audit: it selects `sceneData.doors[triggerID]`, loops through `door.events`, runs `resolveActivateAndAdvance` for each, and returns on the first actually resolved branch. No source-backed defect in this ordering was established; therefore **do not change Kotlin based on guesswork**. The dynamic E2E must verify battle-result classes and warp handling.

## Exact guarded data patch

Source-based `tools/patch_dvt_12080_godstone_two_door_routes_20261012.py` verifies all six per-stage event gates and battle-class8, actual original Door coordinates and Eve ownership, unchanged neighboring quest steps, known input binary SHA and fixed-length packed field, idempotent backup and atomic write. Exactly **two 8-byte END fields (16 actual differing bytes)**:
- Repo+DEV 892087-byte MarkStep SHA `18291b043c1384d3cbc10305230d2e9798877c8dd5f6c89802bd677832273234` → **`91eee7bb0f02b1357c4c166a5b32467a71842a292e74d1d66057d0e706b7595e`**. Server offsets **637851** and **638700**.
- Both staging client copies 928425 bytes SHA `75f6e387c0af1d0c8ff15aad9ac2fadfd256433fc23e13fb47af6b6967cbc208` → **`c7e1b563fbba6a366556130657172febf196657ca3dfbaa32c0c45ff0b6a2969`**. Client offsets **639671** and **640520**.
- Source-valid located targets **5795→5797**, missing END audit metric **200→198**, raw empty END rows **194→192**, MarkStep row count unchanged **4505**. No server Kotlin, DVT Eve, client Lua, spawn or rewards changed.
- `tools/test_dvt_12080_godstone_two_door_routes_20261012.py` covers exact original routes and Door Eve branches, original event battle conditions, unchanged neighboring steps, exact SHA/idempotency, valid scene/door data and read-only Kotlin Door dispatch. Added CI workflow regression.

## Deferred consolidated E2E

Disposable DEV test character at stages4 and7, mission click→correct Door trigger, verify stage-specific Eve is chosen and conditional battle starts where appropriate; success and failure should follow original battle-result branch, not prematurely set quest +1. Check warp movement, relog, repeat-click, and unchanged follow-on steps5/6/8. **No in-game tests performed** pending one full user-approved run.
