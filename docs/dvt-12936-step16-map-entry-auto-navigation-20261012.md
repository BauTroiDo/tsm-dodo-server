# DVT quest12936 step16: route to authentic map-entry Eve10 (no fabricated NPC)

2026-10-12 VN. Follow-on to commit1bcd8d4, source-proof of originally blank END. Actual gameplay E2E deferred until user-requested consolidated test.

**SOURCE CORRECTION (2026-10-12):** The older claim below that scene12403 Eve10 activates automatically on map entry is **incorrect**. Eve10 is bound to **Door7 (100,400)**. The stage12936/16 endpoint was corrected from the stage17 NPC8 to **(12403,100,400,2,7)**. See `docs/dvt-door-trigger-parity-correction-12936-12509-12938-20261012.md`. Preserve the older patch SHA/offset history for auditing, but never reapply the superseded route.

## Original source chain and root cause

Original `IOS_MarkStep_C.dat` quest12936/16 narrative "Đến Từ Vô Sơn giúp Hoàng Nguyệt Anh tìm Gia Cát Lượng" contains zero item/follower objectives and END=(0,0,0,0,0). The client `lua/Logic/MarkManager.lua` cannot auto-navigate without an END despite the real DVT world already having the correct transition.

Actual `gamedata/Eve.emg` has a contiguous original door graph:
- Scene12401 **door2 → scene12402**.
- Scene12402 **door2 → scene12403**.
- Scene12403 **door1 → scene12402**, verifying the return path.
- **Scene12403 Eve10** has **`when_happen=[False,True,False,False]`**, is **not bound to any NPC**, checks original `conditionClass2, param12936, pStyle1, ops5, val16`; its original effects `resultType0/resultClass2/12936/pStyle1/+1` advance to stage17 AND `resultClass3,param8,pStyle4,value500` and `param7,pStyle4,value500` reveal original NPCs as defined. No external quest state mutation or special resultClass bypass is introduced.
- Real NPC **12059**, original scene12403 **placement8**, at (270,420), original `close=true`, owns **Eve11** and stage17 conditions. Critically, **original MarkStep quest12936 stage17 END already points to `(12403,220,420,1,8)`**. Navigating stage16 to that same existing target causes crossing the original scene entry first, triggering Eve10 and revealing original target NPC8. No invented coordinates, dummy NPC or premature quest increment.

## Minimal patch

Only **stage12936/16 END** `(0,0,0,0,0)→(12403,220,420,1,8)`. Fixed-size **8-byte packed field, 7 actually differing bytes**:
- Git/DEV 892087 bytes; offset **843605–843612**. Old SHA `98b8ac110c68acc8e2921b21f965e57e763878cdda4e69f015651a39599f2aa1` → **`382540766e75528bacbb40792188dfa7c210a930bc9bcb82250ccfbea11eb280`**.
- Both staging clients 928425 bytes; offset **845425–845432**. Old SHA `6de2b89ace1c97c8ff674352da767bf02a57be78ccc1768cc58f4227d204834d` → **`b76cc4dc6a0721bda6ff650687582ae0cd93a26d71a9b6bd4f2e00d3643f425e`**.
- Source-valid located static destinations **5761→5762**, empty END count **234→233**, total unchanged **4505** MarkStep records.
- `tools/patch_dvt_12936_stage16_scene_entry_20261012.py`: source-verifies actual contiguous two-door path, stage16 Eve10 original map-entry trigger and +1, two source NPC reveal effects, stage17 visible-when-triggered NPC stage gate, exact previous and next MarkStep endpoints, full SHA version, atomic SHA backup and idempotency.
- `tools/test_dvt_12936_stage16_scene_entry_navigation_20261012.py` source checks original map-entry and NPC behavior, correct stage16/17 target, unchanged prior stage15 and no invalid static NPC/scene/doors. Earlier quest12998/12936 regression updated to reflect the newly *routable* but still original scene-event-driven stage16.

## Consolidated manual validation deferred

Use disposable DEV character at quest12936/16; click quest and verify original portal chain from scene12401 through12402 into scene12403. On entry, Eve10 must trigger once, increment stage16→17 and make original NPC12059 placement8 available; verify the new stage17 waypoint remains the exact same source NPC, and the NPC interaction gives the appropriate original stage17 results. Reload/relog should not rerun Eve10 or give stage17 rewards prematurely. Source event may still fail in Kotlin if its own stage and visibility handling is wrong; **test manually at final consolidated pass**, do not claim E2E PASS from static auditing alone.
