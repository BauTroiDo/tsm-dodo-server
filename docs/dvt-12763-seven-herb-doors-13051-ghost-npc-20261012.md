# DVT 12763 Ngải Thảo seven source Door guides + 13051 cursed-village ghost NPC guides

2026-10-12 VN. Source-backed navigation **only**. The user specifically defers character/client in-game manual E2E until one future combined pass.

## What was broken

Original DVT `IOS_MarkStep_C.dat` has **seven literal empty END fields for quest12763 stages2–8** (different herb delivery clues in Trác Quận). The true source `gamedata/Eve.emg` contains one physical Door Eve for **each exact active quest stage**; its quest step is intentionally non-linear. These Doors run `when_happen=[False,True,False,False]`, so navigating to a merely nearby house NPC would not fire the intended Eve.

Original quest13051 stages1–2 contained only scene IDs `(12137,0,0,0,0)` and `(49991,0,0,0,0)`, so DVT Lua `MarkManager.Navigation` rejects them (requires a nonzero x or y). The source has an actual NPC + placement to target in each scene, both initially hidden (`close=true`). This is a **partial-location repair**, not a guessed new scene.

## Exact source targets

| Quest/step | Original Eve | Destination (scene,x,y,kind,id) | Original step result |
|---|---|---|---|
| 12763/2 | 12179 Door2 Eve3 | `(12179,540,440,2,2)` | consume one 29513, quest +8 |
| 12763/3 | 12102 Door2 Eve3 | `(12102,520,440,2,2)` | consume one 29513, quest +7 |
| 12763/4 | 12003 Door10 Eve45 | `(12003,640,240,2,10)` | consume one 29513, quest +6 |
| 12763/5 | 12004 Door2 Eve7 | `(12004,520,480,2,2)` | consume one 29513, quest +5 |
| 12763/6 | 12015 Door2 Eve7 | `(12015,420,440,2,2)` | consume one 29513, quest +4 |
| 12763/7 | 12001 Door16 Eve100 | `(12001,1700,360,2,16)` | consume one 29513, quest +3 |
| 12763/8 | 12005 Door4 Eve11 | `(12005,900,560,2,4)` | consume one 29513, quest +2 |
| 13051/1 | 12137 NPC38428 placement7 Eve7 | `(12137,570,340,1,7)` | source item29639-gated progress |
| 13051/2 | 49991 NPC38427 placement5 Eve10 | `(49991,690,480,1,5)` | source original completion/rewards |

**Important original DVT logic:** All seven herb Door Eves have TWO `conditionClass2/param12763/pStyle1/ops5/step/and_num2` branches: one `conditionClass1,param29513,pStyle1,ops5,value0` for **no herbs**, and one `ops2,value0` requiring **herb count > 0**. Only the latter consumes one item29513 and applies original quest `resultClass2,pStyle1` with the respective **8,7,6,5,4,3,2** value. These are **non-linear quest updates**; never reinterpret as stage+1, never award herb, never bypass item check. Original first/final stage1/10 Bao BDY NPC `(12002,170,340,1,19)` stays untouched.

13051/1 targets original NPC38428 scene12137, conditional item29639 branch; 13051/2 targets original ghost NPC38427 scene49991, source class2 quest completion and reward class1 items remain unedited. `close=true` on both NPCs stays untouched and may require visibility handling at runtime.

## Binary safety, reproducible source test and rollback

`tools/patch_dvt_12763_13051_herb_ghost_source_routes_20261012.py` source-verifies real Door/NPC placement, Eve trigger kind, step matching, both 29513 branches, exact non-linear effect, original 13051 item and completion effect, known predecessor full SHA, exact packed END framing and prior starting NPC. Writes only **9 8-byte END fields; 67 actually differing bytes**. It does not modify original Eve, quest state, Lua, Kotlin, NPC flags, world objects or game rewards.

- Git+DEV 892087-byte MarkStep source SHA: old `3128277213fd51bbf531a5b5b09f1558ab0265b638ddef680bc1211d00ce3788` → new **`55f09d04eca91d488ab85113a33550e207db0943e5784093ae1b42dffa2ab867`**.
- Both local staging client editions 928425 bytes: old `322be78108db3fd93463a21953164164176fa4cdeed958200462b4c9077c38fe` → **`24d936d4f95368888425d2b81b4d4fb33f7d2c2794379743b4c924c20484161c`**.
- Git source server END byte offsets: `12763/2=816943, /3=817242, /4=817539, /5=817802, /6=818061, /7=818322, /8=818573; 13051/1=857654, /2=857787`. Staging client offsets: `818763,819062,819359,819622,819881,820142,820393,864614,864815`. **Do not assume all client offsets are server+1820**, since 13051 has a different client binary record layout; the guarded patcher scans each edition independently.
- Source-valid located targets **5824→5833**; blocker audit missing-END **171→162**; literal blank END **165→158**; total 4505 MarkStep records intact. Scene/NPC/Door reference census remains valid, 87 unresolved unlocated condition targets unchanged.

No one-shot game E2E yet; only original source and automated regression `tools/test_dvt_12763_13051_herb_ghost_source_routes_20261012.py` plus full DVT suite.

## Deferred combined in-game E2E

For quest12763 stages2–8, click quest → correct source Door in seven separate Trác Quận scenes, verify no-item branch never consumes/grants or advances, possess item29513 and verify consume exactly one with corresponding original **non-linear** quest update. Test stage1 and10 original Bao BDY NPC, relog and repeat activation. For quest13051 stages1–2, verify Chinese-text clues point to NPC38428 in scene12137 and NPC38427 in cursed village49991, visibility `close=true` is gated by original event state, original item29639 requirement, reward and completion when eligible; no premature completion or dupe. Also verify navigation across portals/scenes and server Kotlin Eve trigger routing. **NOT YET MANUALLY PLAYTESTED**.
