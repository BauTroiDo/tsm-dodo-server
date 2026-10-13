# DVT quest11900, timed escort Trương Bào NPC10269 — seven original source waypoints

2026-10-12 VN. Implemented **navigation data only**, preserving the original quest state machine, follower checks, time window, battle win/failure and respawn. User deferred all in-game character tests until one consolidated E2E pass. All source evidence from `gamedata/Eve.emg`, `lua/Logic/MarkManager.lua`, Kotlin `EveConditionEvaluator` and `IOS_MarkStep_C.dat`.

## Why seven escort stages were unnavigable

Quest11900 steps1–4 already have good source NPC/door routes and step12 routes to inn NPC in scene12242, but **steps5–11 had END=(0,0,0,0,0)**. All seven have a first **follower objective kind1, NPC10269**, at `(12589,1440,1000,1,17)`. Original DVT client `MarkManager.Navigation` checks `Role.GetFollowNpc(Role.playerId, condition.id)`: when **not** following, it overrides the END with the objective's source location (recover wounded Trương Bào); when **following**, it uses **END**. Without the latter, no auto-go can continue even while an escort is active. This distinction is verified by code and the new tests.

| Stage | Real source destination (scene,x,y,kind,id) | Eve source proof | Original progression behavior |
|---|---|---|---|
| 5 | **(12589,1490,1020,1,17)** | NPC10269, Eve9, exact stage5 branch No20 | Dialogue requires follow NPC plus class7 guard before stage+1; original NPC placement and step4 END agree |
| 6 | **(12589,1340,1000,2,3)** | Door3, Eve10, stage6 gate | Battle class8 winning No10 advances; original follower/quest-state alternatives remain |
| 7 | **(12588,440,540,2,3)** | Door3, Eve5, stage7 gate | Battle win No13 advances; original scene effects untouched |
| 8 | **(12582,1280,1220,2,4)** | Door4, Eve8, stage8 gate | Battle win No10 advances; original warp/portal behavior preserved |
| 9 | **(12861,1220,1720,2,5)** | Door5, Eve6, stage9 gate | Battle win No10 advances; original event state and NPC show flags untouched |
| 10 | **(12861,1220,1720,2,5)** | Same original Door5/Eve6, distinct stage10 condition No13 | Distinct original conditional +1, not a new or fake target |
| 11 | **(12011,1140,880,2,15)** | Julu gate Door15, Eve24, stage11 branch No3 | Follower guard and original +1; original step12 inn NPC remains |

**Important:** Source `when_happen=[False,True,False,False]` in stages6–11 identifies real **Door** Eve via `doors[].events`, not automatic map-entry and not any nearby NPC. Stage5 is a **ClickNpc** Eve via `npcs[].events`, not Door. No scripted progression on mere arrival. Stage6–9 boss/trap win branches live in `conditionClass8`, not their initial quest-stage gate, so granting mission+1 on door-click would be wrong.

The source timer/quest auxiliary flags are complex (notably `conditionClass7` and `conditionClass2/param2` forms). This patch **does not assert timer semantics are fully implemented in Kotlin**; the native client tells the player to reaccept the escort when time expires. Static source comparisons alone cannot prove live timed escort completion. Kotlin has source-aware class7 follower-space and NPC-location evaluation, and those state guards remain unchanged.

## Exact fixed-field patch and protection

Source-guarded `tools/patch_dvt_11900_timed_escort_source_routes_20261012.py` validates the seven exact original Eve owner placements, all 11900 stage gates and appropriate quest-result stages, four battle-win checks, no unsupported class11/13/15 results, all original kind1 follower objectives and original stage4/stage12 neighbors; never changes conditions, follower target or Eve data. Full binary input SHA guard, fixed 8-byte END records, independent parser roundtrip, checksum-verified backup and atomic replacement, idempotent after application.

- Git+DEV MarkStep 892087 bytes old SHA `b2bb1ae382f5c0e50efe49e8ec8b98ce3a44bbba97e8bed955d94a1fd9749753` → new **`3128277213fd51bbf531a5b5b09f1558ab0265b638ddef680bc1211d00ce3788`**.
- Two staging clients 928425 bytes old SHA `4d6a4ea028df1649b77602fe0e9faebca0542032f5186d3b0e9e97e176d0dcc4` → new **`322be78108db3fd93463a21953164164176fa4cdeed958200462b4c9077c38fe`**.
- Exactly **55 differing bytes**, all inside seven 8-byte END fields. Git+DEV END offsets stages5–11: **526082,526433,526784,527135,527486,527837,528044**; client offsets each +1820. All 4505 MarkStep rows intact, no other bytes changed.
- Static source-valid located navigation targets **5817→5824**, audit missing-END **178→171**, raw literal blank END **172→165**. New regression `tools/test_dvt_11900_timed_escort_source_routes_20261012.py` covers all source paths, original fallback-to-follow objective, battle-wins only after victory, Kotlin follower guard, full SHA/idempotency and scene/door/NPC corpus integrity.

## Single deferred gameplay E2E (NOT RUN)

Test disposable character quest11900 at steps4–12:
- Stage4 recruit and follow NPC10269. **Without follower**, pressing mission in each stage5–11 should route to original NPC in map12589; with follower, stage5 routes to NPC17 dialogue and stage6–9 go to authentic Door trap battles. Verify successful battle vs failure/flee does not increment incorrectly, timer still expires as DVT originally required.
- Stage9→10 uses **same Door5** but different source condition branches; step10->11 travels to Julu Door15 and returns NPC10269; stage12 to inn scene12242 NPC7.
- Verify relog, follower lost/inn/cart, timeout/reaccept and original modal transition effects with no duplicated +1 or bypassed battle.
- Crucial: verify Kotlin source `conditionClass7`, quest auxiliary flag state, class8 battle result and cross-map portal navigation actually match the original runtime. Automated source parity is **not** gameplay E2E.

All remaining unrelated quests and 171 other audit-flagged blank END cases stay untouched until individually source-verified.
