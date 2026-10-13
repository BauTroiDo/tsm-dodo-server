# DVT quest12138 Qin tomb: nine original puzzle Doors across 18 stages

2026-10-12 VN. User requested the next source-backed auto-navigation fix, with all in-game manual tests deferred to one later consolidated E2E pass.

## Why all 18 stages were unlocated

The original DVT `IOS_MarkStep_C.dat` had **END=(0,0,0,0,0)** on **quest12138 steps1–18** despite their guide text explicitly naming Qin tomb relics and locations. Source Eve has **9 physical Door interactions**, one per consecutive two-stage pair. These are NOT standard NPC dialogues: source `gamedata/Eve.emg` `doors[].events` attaches the actual Eve, `when_happen=[False,True,False,False]`; `npcs[].events` does not. Kotlin `EveTriggerRouter.routeDoor` and native DVT Lua `MarkManager.CheckNavigationDoor` use the Door ID. Without END the client mission click cannot path to the puzzle target.

| Stages | Map | Door ID, coordinates | Original Eve | Original relic item granted on even stage |
|---|---|---|---|---|
| 1–2 | 14620 | Door8 (1260,1400) | Eve10 | 29363 |
| 3–4 | 14623 | Door2 (420,820) | Eve3 | 29364 |
| 5–6 | 14622 | Door8 (1700,2260) | Eve9 | 29365 |
| 7–8 | 14630 | Door2 (1160,380) | Eve3 | 29366 |
| 9–10 | 14632 | Door8 (2080,980) | Eve10 | 29367 |
| 11–12 | 14629 | Door2 (420,580) | Eve3 | 29368 |
| 13–14 | 14627 | Door2 (1080,360) | Eve3 | 29369 |
| 15–16 | 14621 | Door6 (960,1000) | Eve8 | 29370 |
| 17–18 | 14625 | Door2 (420,380) | Eve3 | 29371 |

**Essential stage/branch semantics remain unchanged:** Odd steps (1,3,…17) have the original step-specific `conditionClass2` gate but **only show/hide puzzle objects (resultClass3)**. Odd steps do NOT grant quest progress or relic items. Even steps (2,4,…18) have a separate result branch with the quest step gate plus a second original **`conditionClass7, param2, pStyle2`** condition (AND=2). Only when original conditions are met does the Eve grant its respective `29363…29371` item and advance quest12138 exactly one step. Static navigation helps reach the actual puzzle interaction; **it must NOT bypass the puzzle, auto-grant relic, assume dynamic visibility, or force stage advance**. Source door positions agree with original quest text coordinates, allowing normal arrival offsets.

Original steps **19 and20** are left exactly as-is, `(14620,450,350,1,1)` at NPC40106 (Phù Tố chi hồn). Original Eve19 requires the nine distinct items before consuming one each; this patch does not edit that exchange, inventory logic or rewards.

## Exact binary boundaries

Source-guarded script `tools/patch_dvt_12138_qin_tomb_nine_door_puzzles_20261012.py` validates for each of nine pairs: source door ID/coords/Eve binding, proper Door `when_happen`, paired original odd/even quest gates, no odd-step item/quest gain, even-step original item grant and quest+1, `conditionClass7` gate preservation, closest original guide-text position, original 19/20 Fusu routes and version SHA. Writes only **18 existing 8-byte END fields** with idempotence, SHA-verified backup and atomic replacement; no Kotlin, Eve, Lua, quest state, DB, item or dynamic spawn data edited.

- Repo/DEV 892087-byte MarkStep SHA old `918133ebee509d28b964de93c0820e1eccc7c75557263e7ba20b735a8a1dc5f8` → **`18291b043c1384d3cbc10305230d2e9798877c8dd5f6c89802bd677832273234`**.
- Both client staging copies 928425-byte MarkStep SHA old `24f4bb1a825c4db902e3f5fd9f713a174ac25feaa0d64f4666fa8bbbe4669288` → **`75f6e387c0af1d0c8ff15aad9ac2fadfd256433fc23e13fb47af6b6967cbc208`**.
- **Exactly 144 actual differing bytes in 18 END fields**. Server offsets 1–18: `697972, 698211, 698446, 698681, 698916, 699151, 699378, 699605, 699842, 700079, 700308, 700537, 700782, 701027, 701264, 701501, 701734, 701967`. Each client offset is server offset +1820.
- Source-located waypoints **5777→5795**, blank END **218→200**, total **4505** MarkStep entries intact. Regression `tools/test_dvt_12138_qin_tomb_nine_door_puzzles_20261012.py` source-verifies odd/even branch distinction, second condition7 guard, nine original item IDs, exact 18 final ENDs, unchanged stage19–20, Kotlin/Lua Door dispatch, idempotence, SHA and source scene/door/NPC census.

## Deferred combined in-game test

Use DEV disposable quest12138 characters at steps1–18; for each click quest, confirm original physical Door and that the visible puzzle object is gated by the correct quest step. Test **odd stage event** displays objects but does not auto-complete or give relic; **even stage** still requires original conditionClass7 and only then grants the item, increases exactly one step, and does not duplicate on repeated click or relog. Confirm all nine relics29363–29371 are needed at stage19 NPC40106 before consumption, then original stage20 reward; no unintended item/reward to alternate quests. Test hidden objects, map entry locks and remote pathfinding of nine scenes. **Not tested in-game yet** — source/automated parity only.
