# DVT quest12509 step16 — guided stolen-thief return to original Santa completion (2026-10-12)

## Root cause

Original `File.dat/Data-ios/IOS_MarkStep_C.dat` quest12509 stage16 "Cùng tên trộm đến tìm ông già Noel hoặc Khảm Bộc Từ" has one `kind1` follow/capture objective, **NPC38136**, at original `(49946,590,310,1,12)`, and a **blank END**. The original client `lua/Logic/MarkManager.lua` switches to END once `Role.GetFollowNpc(Role.playerId, condition.id) ~= nil`, so after capturing the thief tapping the mission cannot automatically route to any completion scene.

## Why the original Santa waypoint is correct

The active DVT `gamedata/Eve.emg` scene **49945** contains visible NPC **38127 "Ông già Noel" placement1 at (290,420)**; MarkStep stages **12509/1 and 12509/2 already route to `(49945,270,420,1,1)`**, within its original NPC arrival vicinity. Do **not** use Santa's NPC conversation as a made-up completion: **scene49945 Eve9 has `when_happen=[False,True,False,False]` (scene-entry trigger), no NPC binding**, and precisely:

- Condition1 checks **mission12509 step16** `class2/pStyle1/ops5/val16`, AND condition2 checks companion/follower **NPC38136** `class9/pStyle1/ops5/val38136`.
- Original result removes quest12509 `class2/pStyle3/val0`, starts next quest12510, clears follower38136 and advances related quest flags. No `resultClass11/13/15` on this branch.
- **Alternate valid finish is preserved**: scene49947 and scene49948 each have source Eve3, scene-entry trigger, condition 12509/16 + follower38136, and original mission completion. This patch selects a single already-existing Santa waypoint as a default while leaving the separate Khảm Bộc Từ route functional.

## Byte-safe patch

`tools/patch_dvt_12509_final_santa_return_20261012.py` validates Eve9 scene entry, exact AND follower gate, original completion and transition, both alternate finishes, NPC visibility/placement, existing MarkStep stage1/2 Santa routes and stage15 capture objective, original stage16 follow condition, original known binary SHA, exact diff bound to one END field; checksum backup/atomic replace/readback, idempotent.

- Only quest **12509/16 END** changes `(0,0,0,0,0) → (49945,270,420,1,1)`; **8 bytes** at repo/DEV offset **768781–768788**, client staging offset **770601–770608**.
- Git repository and DEV MarkStep size **892087 bytes**, SHA `0cecf9af8234f7c31a16ab0937839c1b5bcfcea8e1f7be1872e7e85218e31c2a` → **`87e30327e5c42ccec30f788022fc28a5f181505c03bb8102fdb3beb14a489ec0`**.
- Both local client staging editions size **928425 bytes**, SHA `247bcb4c698e033a58e750d819c6861d668fdeb5431c0031c745266ba2648fab` → **`ccb3d8a119c7adc4e9503a3aaae721e0d20103c300168075610bbac698790f78`**.
- Source-valid static destinations census **5751→5752**, blank END **243→242**. Structural parity remains 4505 rows per edition. No Eve/Lua/Kotlin/DB/inventory/reward/quest stage writes, other Christmas objectives untouched.

## Regression

`tools/test_dvt_12509_santa_final_return_20261012.py` tests original source Eve9 stage+follower AND gate and scene-entry nature, both alternate scene-entry completion branches, reused original Santa stage1/2 endpoint, exactly unchanged thief capture stage15, original Lua transition logic, precise SHA and idempotency. Existing source-guarded Christmas food/vending regressions remain.

**Limitations:** No live in-client character click-to-route, scene-entry completion, inventory or quest reward test. No unsupported reward effects implemented or bypassed. Does not auto-complete on quest click: original Eve9 still requires the real thief follower and actual map entry.
