# DVT source correction: Door-trigger Eve, not automatic scene-entry

2026-10-12 VN. **This supersedes the prior static interpretation of Eve10/scene12403 and Eve9/scene49945 as automatically triggered on map entry.** Those source events are attached to real Door records. This correction changes navigation only. All game-character E2E remains deferred for the single consolidated pass as requested.

## Original event ownership is authoritative

Original `gamedata/Eve.emg` has `when_happen=[False,True,False,False]` for these Door-bound events. Original `doors[].events` identifies the exact Eve while `npcs[].events` does not. Kotlin `EveTriggerRouter.routeDoor` performs `val door = sceneData.doors[triggerID]` and then iterates `for (eveNo in door.events)`. Original DVT Lua `MarkManager.CheckNavigationDoor` requires the matching `navigationEventId` from `DoorManager.doors`. Entering the scene or clicking a nearby NPC is **not sufficient**. Script-only doors do not need a warp destination (`scene_infos`).

| Mission / step | Prior END | Corrected END (scene,x,y,kind,id) | Original event/result |
|---|---|---|---|
| **12936/16** | (12403,220,420,1,8), future NPC | **(12403,100,400,2,7)** | Door7→Eve10, stage16→17 (+1) and reveals original NPC12059 placement8; original stage17 NPC route stays untouched |
| **12509/16** | (49945,270,420,1,1), Santa NPC stage1/2 | **(49945,280,540,2,2)** | Door2→Eve9, exact quest12509 step16 AND follower38136; finishes quest and removes follower; no unsupported class11/13/15 |
| **12938/4** | Blank | **(12031,420,480,1,13)** | Same existing original stage3 waypoint to Aunt NPC15133 attached Eve17 and quest step4→5 |
| **12938/5** | Blank | **(12031,740,680,2,6)** | Door6→Eve19, stage5→6, shows original NPC27007 placement14; original stage6 NPC route remains |

The original portal graph for 12936/16 still runs 12401 door2→12402 door2→12403, **then must interact with door7**. The optional 12509 completion in cave49947/49948 is also Door3→Eve3, not automatic scene entry. The previous 12509/12 fix targeting cave Door2 remains a correct door endpoint, although its older explanatory text should not be used as an authority for trigger type.

## Exact fixed-field data edit

- Source-guarded, idempotent `tools/patch_dvt_quest_door_trigger_corrections_20261012.py` checks every original Door/NPC binding, quest-stage gates, actual quest results, existing adjacent MarkStep routes, input SHA and exact byte ranges; atomic write with checksum-verified backups.
- Four existing 8-byte END fields, **25 actual byte differences**, no Kotlin/Eve/Lua/DB, quest-state, spawn or reward changes. Server offsets: 12936/16 **843605**, 12509/16 **768781**, 12938/4 **845502**, 12938/5 **845733**. Client offsets: **845425**, **770601**, **847322**, **847553** respectively.
- Server binary old SHA `382540766e75528bacbb40792188dfa7c210a930bc9bcb82250ccfbea11eb280` → **`363cb99326302577ca26b432716c4a9862fa16c8d164a5d592e11faf4959308a`**.
- Two staging clients old SHA `b76cc4dc6a0721bda6ff650687582ae0cd93a26d71a9b6bd4f2e00d3643f425e` → **`a7ac8cd9b1fc4b8479b5886adb1d2d6daa80e716a7b7fb4cced4ab5df1f7b11d`**.
- Valid source targets **5762→5764**, blank END fields **233→231**, unchanged **4505** MarkStep rows. New `tools/test_dvt_quest_door_trigger_corrections_20261012.py` guards Door versus NPC ownership, Kotlin and client logic, four endpoints and neighboring step transitions; older regression tests updated.

## Deferred manual E2E (not done yet)

- 12936/16: auto-go into map12403 **Door7**; verify original Eve10 advances exactly to stage17, exposes NPC12059 and avoids repeat rewards after relog.
- 12509/16: with follower38136 auto-go to Santa **Door2** in scene49945; verify Eve9 finishes and clears follower. Without follower the quest must not finish. Cave door3 alternative unchanged.
- 12938/4–6: click step4→Aunt15133, stage5→Door6 activates Eve19 and reveals Chức Nữ NPC27007 for stage6; check original close flag behavior.
- Verify the game client actually triggers scripted non-warp doors, not just reaches their coordinates; static audit alone cannot prove this.

**Important:** The historical source patchers `tools/patch_dvt_12509_final_santa_return_20261012.py` and `tools/patch_dvt_12936_stage16_scene_entry_20261012.py` are superseded by this correction and **must not be reapplied** to the corrected MarkStep edition. Preserve them as chronological history. Previous PR notes calling these events map-entry triggers are also superseded.