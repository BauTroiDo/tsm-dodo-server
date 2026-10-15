# DVT10122 step2 and DVT10290 step1 — original visible NPC autonav (2026-10-12)

## Original root cause

The original DVT `lua/Logic/MarkManager.lua` uses MarkStep END when the current quest step has no pending condition. Both mission10122 step2 (Dong Quan palace infiltration) and mission10290 step1 (find Dong Tam in the cave) have no item/capture/kill condition and had a **blank END**. Quest click therefore has no destination.

## Source-backed targets

1. **10122/2**, mission text "Đến đường lớn Đông Quận, tìm đến giúp đỡ Gian tế ở phía ngoài quan phủ Đông Quận, đột nhập quan phủ ...": original `gamedata/Eve.emg` map **11002** has **visible NPC17018 placement5, (570,260)**, uniquely attached to Eve14. Eve14 condition3 checks **mission10122 stage2** and offers real portal **door6 → scene11301**. Inside scene11301 **NPC14216 placement8** Eve4 also requires mission10122 stage2. This is the FIRST waypoint for the real palace infiltration, not auto-completion. The identical spy route for quest10126 step4 was separately verified and deployed.
2. **10290/1**, "Đến sơn động Diêu Động tìm đồng tâm": original map **14566** has **visible NPC35013 placement1, (450,360)**, Eve2 condition8 checks **mission10290 stage1** and branches to dialog condition9, which **grants original item29095 Đồng Tâm ×1** and increments quest10290 by one stage. Scene14560's original exit4 leads to scene14566. Stage10290/2 already has the original turn-in NPC14113/map14173 waypoint and is untouched.

`tools/patch_dvt_10122_10290_source_npc_routes_20261012.py` checks both original stage/scene/NPC/dialogue paths, original quest data has no objectives, two exact END windows and approved original SHA editions, backs up and atomically replaces data with idempotent validation. Only **16 bytes**, exactly two 8-byte END fields, are modified:

| Mission/step | New END target | Offset |
|---|---|---:|
| 10122/2 | `(11002,570,260,1,5)` | 14625–14632 |
| 10290/1 | `(14566,450,360,1,1)` | 52563–52570 |

- Git/DEV MarkStep (892087 bytes): old SHA `23714ce568f5bb3bca45645f07573d6810177a8c5c8d430889669a6789a24ed6` → new **`0cecf9af8234f7c31a16ab0937839c1b5bcfcea8e1f7be1872e7e85218e31c2a`**.
- Both local client MarkStep copies (928425 bytes): old SHA `f86a7448ff60e690590b384e248d4410a70ea6eac390f41246dcacc48aa1d21a` → new **`247bcb4c698e033a58e750d819c6861d668fdeb5431c0031c745266ba2648fab`**.
- Total source-valid located targets **5749→5751**; blank END **245→243**. No changes to quest state, player DB, DVT Eve, Lua, Kotlin, drops, rewards, or NPC visibility.

## Explicit non-fixes

Quest12936's missing stage4,9,21 endpoints have NPC candidates tagged `close=true`, and stage16 transitions from a **non-NPC scene event** in map12403. These require more verification of spawn/visibility and scene triggers before safely assigning an NPC waypoint. Qin Tomb puzzle quest12138 stage1–18 describes manual coordinates and has scene-trigger (non-NPC) events: no unverified generic NAV kind invented. `resultClass13` reward code40 in quest10852 remains unsupported and fail-closed.

No end-to-end character test of click→walk→NPC→quest stage transition yet. Keep this as source/navigation regression, not confirmed complete quest gameplay.
