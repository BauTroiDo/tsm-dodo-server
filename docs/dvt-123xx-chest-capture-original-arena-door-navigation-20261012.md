# DVT 8 chest/capture arena entry Doors — original source-verified quest step1 navigation

2026-10-12 VN. Incremental, source-backed navigation fix. No in-game character E2E until user's one consolidated pass.

## Problem, exact original trigger and rationale

Eight otherwise independent five-stage DVT quest lines at original scene IDs **62003–62010** have no END targets at any step. Quest1 textual objective sends the player into a treasure chest or monster-capture arena. The source `gamedata/Eve.emg` contains one unambiguous **Door1, Eve1** per scene. That Eve is attached to `doors[].events=[1]`, **not** an NPC; its `when_happen=[False,True,False,False]` original quest gate is exactly `conditionClass2/missionId/pStyle1/ops5/step1/and_num1`; original result advances only its own quest by one step and uses class3 to reveal arena objects/monsters. These arena objects have multiple independent NPC conversations and/or battles. No safe single unique target can be inferred for stages2–5: leave those END fields blank until separate scene puzzle/monster mechanic analysis.

| Quest/step | Mode | Map | Source Door1 position | DVT source effect |
|---|---|---|---|---|
| 12330/1 | 3 treasure chests | 62003 | (1680,1580) | Eve1 reveals original chest scene objects |
| 12346/1 | 3 treasure chests | 62004 | (2060,920) | Eve1 reveals original chest scene objects |
| 12350/1 | 3 treasure chests | 62005 | (2900,1000) | Eve1 reveals original chest scene objects |
| 12354/1 | 3 treasure chests | 62006 | (1240,1580) | Eve1 reveals original chest scene objects |
| 12358/1 | Capture three times | 62007 | (780,680) | Eve1 reveals original NPC22087 monsters |
| 12362/1 | Capture three times | 62008 | (780,680) | Eve1 reveals original NPC22088 monsters |
| 12366/1 | Capture three times | 62009 | (780,680) | Eve1 reveals original NPC22089 monsters |
| 12370/1 | Capture three times | 62010 | (780,680) | Eve1 reveals original NPC22090 monsters |

Each new END is `(scene,x,y,2,1)`. The original Door1 may be a scripted non-warp Door; only source scene62010 has a scene-info warp. That does not invalidate scripted Door interaction. Stage1 original Eve includes a `resultType1/class7` side-effect in chest arenas and `resultType1/class3` in capture arenas; these remain untouched, and successful navigation alone does **not** prove all original side-effects work in Kotlin. The correct game E2E must validate effects and actual objects.

**Original stages2–5 intentionally remain `END=(0,0,0,0,0)`** for all eight quests. They represent real chest choices / multiple capture battle rounds, no unique original next target proven. Do not fake map/monster progress, invent one NPC ID or treat blank END as safe to auto-complete. The dynamic stage progression is not fixed by this route-only patch.

## Exact data-only changes and automated checks

`tools/patch_dvt_123xx_chest_capture_entry_doors_20261012.py` verifies all eight Door source records, exact quest/stage gate and `resultClass2/pStyle1/+1`, source class3 arena setup effect, original monster/trap NPC templates, every other stage END blank, known complete source SHA, exact 8-byte packed END fields, atomic checksum-verified backups, idempotent re-run. **Eight 8-byte fields, exactly 64 changed bytes; all other MarkStep data untouched.** No Eve, Lua, Kotlin, inventory, quest flags, monster drop tables or NPC spawns modified.

- Git/DEV 892087-byte MarkStep predecessor SHA `91eee7bb0f02b1357c4c166a5b32467a71842a292e74d1d66057d0e706b7595e` → new **`94bbeeb0fde711f0435d490928ffba94d86afaaa880f6868e4dc6320dff1c424`**. Server offsets `12330=736992,12346=737983,12350=738974,12354=739965,12358=740954,12362=741917,12366=742916,12370=743893`.
- Both staging client editions 928425 bytes predecessor `c7e1b563fbba6a366556130657172febf196657ca3dfbaa32c0c45ff0b6a2969` → **`985eac0351fc923ab904cf34791e96cdb983e02d1a521cfcdd6cf04230950825`**. Client offsets `12330=738252,12346=739243,12350=740234,12354=741225,12358=742214,12362=743177,12366=744176,12370=745153`. Client/server payloads are different editions; parity must be compared at decoded MarkStep record level.
- Source-valid located static targets **5797→5805**, audit missing-END metric **198→190**, literal zero END count **192→184**, total **4505** MarkStep rows each edition. No missing source scene, NPC or Door.
- `tools/test_dvt_123xx_chest_capture_entry_doors_20261012.py` covers source Door/Eve stage1, original arena class3 effects, no ambiguous NPC substitute, stages2–5 unchanged, exact SHA/idempotency, source static census, and original chest/monster interaction existence. Regression CI workflow step added and previous checksum/census regression tests updated.

## Deferred one-pass gameplay test plan

Create disposable DEV character at step1 of every quest, click quest and verify path to Door1 in each original scene and original Eve1 triggers exactly once. Inspect dynamically revealed **chest objects** (quests12330,12346,12350,12354) and complete three distinct chest interactions without duplicated step/reward. For quests12358,12362,12366,12370, verify source monster NPCs22087–22090 are visible and combat/capture events increment only correct quest, exactly three wins/captures, no unintended reward from unrelated monster. Verify stage1 immediate +1, stage2–5 handling by manual inspection, original scene62010 warp and relog duplicate behavior. Explicitly test resultType1 side-effects of original stage1 events; current route repair does not implement these semantics. **NOT YET TESTED on game client**, per owner instruction.
