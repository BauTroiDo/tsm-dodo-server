# DVT12106: 20 source-backed poisoned-soldier delivery waypoints (2026-10-12)

## Root cause

`lua/Logic/MarkManager.lua` starts mission auto-navigation at MarkStep END, then checks pending kind3 collection objectives. DVT quest12106 `Giới diệp vân hương` has 20 rows (**steps6–15,18–27**) each requiring item **29342×5**, with correct existing acquisition/merchant objective `(30108,550,470,1,1)`, but **all 20 END fields are blank** `(0,0,0,0,0)`. Consequently a player with five medicines has no route to the soldier whose dialog applies the medicine. Sending the user back to the ingredient vendor is incorrect once they have enough.

## Original-source proof

All ten consecutive pairs represent one poisoned soldier hand-in each (two progress states per soldier). The authoritative live `gamedata/Eve.emg` scenes **30892** and **30891** each contain *two* NPC17491 instances valid for each stage pair. The patch verifies for each selected NPC:

- Its original NPC placement exists in the active scene, is not closed, is bound to a genuine event.
- The same Eve event has class2/param12106/pStyle1 condition for the **even step** and a type0 class2 quest advancement effect `value1`.
- That event also has condition for the **following odd step**, with original type0 **item29342 / pStyle1 / value=-1** and the original quest advancement `class2/param12106/pStyle1/value1`.
- Exactly **two independently valid original soldiers** exist at each pair. Pick one as the guide destination and preserve the other as a fully functioning alternative. The selected path is a grounded practical route, not an assertion of shortest navigable distance.

| Original steps | Scene | Selected placement | Coordinates |
|---|---|---:|---|
| 6, 7 | 30892 | 8 | (1430,2960) |
| 8, 9 | 30892 | 10 | (390,2380) |
| 10, 11 | 30892 | 11 | (290,2000) |
| 12, 13 | 30892 | 14 | (270,700) |
| 14, 15 | 30892 | 15 | (350,1180) |
| 18, 19 | 30891 | 9 | (3390,420) |
| 20, 21 | 30891 | 11 | (2490,420) |
| 22, 23 | 30891 | 13 | (1230,420) |
| 24, 25 | 30891 | 16 | (1610,520) |
| 26, 27 | 30891 | 17 | (1410,960) |

Preceding and following original destinations, notably stage16/17 NPC14652 in 30891 and stage28 NPC12001 in 30107, stay untouched. No claimed shortcut, artificial medicine, fabricated NPC, fake quest completion or reinterpretation of packed effects is added.

## Binary editing / rollback

`tools/patch_dvt_12106_poisoned_soldier_delivery_20261012.py` requires known SHA before write, source-verifies each Eve NPC and effect, ensures all twenty MarkStep records retain `kind3/item29342/count5` at the original ingredient waypoint, allows only **twenty isolated 8-byte END fields**, reparses afterward, creates a checksum-verified backup, atomically replaces and supports idempotent verification.

- Git/DEV MarkStep **892087** bytes: old SHA `8abb49e2636ff925af23a259e4b1924d9f7fe557f38c383f6912c84d14af3110` → new SHA **`23714ce568f5bb3bca45645f07573d6810177a8c5c8d430889669a6789a24ed6`**.
- Both staging clients **928425** bytes: old SHA `60be6337107c15c7f7997542ff0f800b8f7c7fdab7342afa99983b7bacbdbce0` → new SHA **`f86a7448ff60e690590b384e248d4410a70ea6eac390f41246dcacc48aa1d21a`**.
- Exact **160 changed bytes**, twenty fields (offset 659553..664021, with separate 8-byte windows; intermediate non-12106 records untouched).
- Static located target census expected **5729→5749**, blank END census expected **265→245**, no newly missing NPC/scene/door targets.
- `tools/test_dvt_12106_poisoned_soldier_navigation_20261012.py`: 4 regression checks original Eve pair choices, selected NPC coordinates, exact potion objective, LUA selection, checksum and idempotency.

## Boundaries

This is only a **navigation data patch**. No Kotlin, Eve, DB, NPC inventory, or reward logic changed. The original 10 groups have two selectable soldiers; the guide intentionally chooses just one original option per group. No in-client test character has handed in medicine or triggered quest progress. These dialogue branches may contain additional unsupported effects or flags, and must not be represented as fully E2E verified. In particular `resultClass13` is still unresolved in unrelated quest10852 and not bypassed here.
