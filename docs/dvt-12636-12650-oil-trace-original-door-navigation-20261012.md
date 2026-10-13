# DVT Chu-family oil-trace missions 12636 and 12650 — original Door-bound Eve routes

2026-10-12 (VN). This is a **data-only** navigation repair: no Eve result, Lua, Kotlin, DB, quest flag or spawn changed. **Actual character-level E2E is deferred for one consolidated test pass**, as requested.

## Confirmed root cause

Original server and local client MarkStep for mission **12636 stages1–11** and mission **12650 stages1–2** have **END=(0,0,0,0,0)** and no active item/follower objective. DVT Lua `MarkManager.Navigation` therefore cannot start auto-go. Authoritative `gamedata/Eve.emg` instead has exact step-matched original **Door** events. For each step, the original scene `doors[doorId].events` includes its Eve number, the Eve has `when_happen=[False,True,False,False]`, matching `conditionClass2,quest,pStyle1,ops5,step,and_num1`, and original `resultClass2` advances that quest by one. No invented NPC targets or reward effects.

| Quest/step | Original map | Door ID | Door coordinates | Real Eve number |
|---|---|---:|---|---:|
| 12636/1 | 49957 | 4 | (560,340) | 5 |
| 12636/2 | 49957 | 3 | (380,320) | 6 |
| 12636/3 | 49954 | 10 | (940,440) | 11 |
| 12636/4 | 49954 | 11 | (1180,740) | 12 |
| 12636/5 | 49954 | 7 | (1200,940) | 6 |
| 12636/6 | 49960 | 3 | (1120,780) | 6 |
| 12636/7 | 49960 | 4 | (920,580) | 7 |
| 12636/8 | 49960 | 5 | (480,440) | 8 |
| 12636/9 | 49960 | 2 | (320,540) | 2 |
| 12636/10 | 49961 | 2 | (720,520) | 2 |
| 12636/11 | 49961 | 3 | (580,360) | 3 |
| 12650/1 | 49957 | 4 | (560,340) | 8 |
| 12650/2 | 49958 | 3 | (400,320) | 7 |

Every new END is **(scene,x,y,2,doorId)**. The canonical last step is unchanged: original **12636/12** and **12650/3** END both `(49955,410,370,1,1)` at the Chu grandfather.

### Multiple Eves on same Door — important not to conflate them

Original scene49957 **Door4 events order `[12,4,5,8]`** includes special events for other quests and both active oil-trace quest events: Eve5 for **12636/1** and Eve8 for **12650/1**. Door3 events `[11,3,6]` similarly share a mechanism. The repair **does not remove other Eve events, change event priority or force a branch**, only directs the client to the original Door. In the later consolidated test, check Kotlin `EveTriggerRouter.routeDoor` chooses the correct stage-matched event even when multiple quests or conditions are active.

Original **12636/9** Door2 in scene49960 has a map transition to scene49961, so also check both the quest step advance and resulting map movement. The final Eve3 door in scene49961 contains nonpersistent UI results as well as quest advancement; these remain untouched.

## Guarded binary patch

`tools/patch_dvt_12636_12650_oil_trace_door_routes_20261012.py` checks the original Eve for all 13 exact step matches, source door ID and position, Door-bound rather than NPC-bound event, +1 mission result, absence of unsupported resultClass11/13/15 on the chosen branch, original no-objective blank MarkStep, and original two quest-return ENDs. The script checks both known predecessor SHA editions, compares a separately parsed packed MarkStep index, limits writes to 13 eight-byte END fields, saves SHA-verified adjacent backups and atomically replaces the file. Reapplication is idempotent; unknown editions fail closed.

- Server+Git MarkStep (892087 bytes): old SHA `363cb99326302577ca26b432716c4a9862fa16c8d164a5d592e11faf4959308a` → **`918133ebee509d28b964de93c0820e1eccc7c75557263e7ba20b735a8a1dc5f8`**.
- Two staging client copies (928425 bytes): old SHA `a7ac8cd9b1fc4b8479b5886adb1d2d6daa80e716a7b7fb4cced4ab5df1f7b11d` → **`24f4bb1a825c4db902e3f5fd9f713a174ac25feaa0d64f4666fa8bbbe4669288`**.
- Exactly **104 actual differing bytes** within 13 original zeroed END fields; no MarkStep row added or removed. Server END offsets: 12636/1..11 respectively **787095, 787262, 787429, 787596, 787763, 787930, 788097, 788264, 788431, 788598, 788765**; 12650/1–2 **796970, 797139**. Client offsets 12636/1..11 **788915, 789082, 789249, 789416, 789583, 789750, 789917, 790084, 790251, 790418, 790585**; 12650/1–2 **798790, 798959**.
- Source-located MarkStep target census **5764→5777**, blank ENDs **231→218**, still exactly **4505** MarkStep rows per edition. Regression file `tools/test_dvt_12636_12650_oil_trace_door_routes_20261012.py` verifies 13 source event/route pairs, shared Door4 events, original end returns, SHA/idempotency, no missing scene/door/NPC.

## One deferred consolidated gameplay E2E

For **12636** start test player at step1; for each step1 through11 click the mission, verify map/Door target and actual activation, exactly one step advance, and no extra advance when revisiting another Door. In particular try Door4 in scene49957 with only quest12636, only quest12650 and both conditions (if possible) to check correct Eve priority, and verify stage9 map movement 49960→49961. Then stage12 returns original Chu grandpa NPC at scene49955.

For **12650** similarly verify Door4 scene49957 at step1 and Door3 scene49958 at step2, then step3 original grandpa. Retry with relog and test alternate Eve branches on the same Door. The absence of class11/13/15 in selected advancing conditions **does not prove dynamic door interactions work in a running client**; all remain marked **NOT TESTED E2E** until one consolidated pass.
