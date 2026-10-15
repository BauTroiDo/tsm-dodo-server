# DVT12509 stage10 (ten rods) and stage14 (original quiz NPC) source-proven auto-navigation

Recorded 2026-10-12 (VN); appended to draft PR #41. **No manual character E2E in this phase**.

## Root cause

The original `MarkStep_C.dat` has stage10 "Thu thập 10 cành cây roi" with *all five conditions empty and END blank*. Clicking mission can neither lead to the actual NPC that supplies rods nor switch to the NPC that consumes ten rods when collected. Stage14 "Trả lời câu hỏi của Khảm Bộc Từ" has all conditions empty and END blank despite an existing, visible answer NPC. This is an omission in quest guidance data, not a reason to invent Eve or rewards.

## Original Eve evidence

Original `gamedata/Eve.emg`, **scene49946**:

- **NPC24005, placement10, (1910,1580), Eve19**: condition1 guards **quest12509/step10**, with condition2 checking `item29454`, `pStyle1,ops1, val10` (fewer than 10); original result gives **item29454 ×1**, along with item34006. Condition3+4 checks sufficient rods. NPC24005 is visible (`close=false`) and event19 is an ordinary NPC interaction, not scene entry.
- **NPC15422, placement9, (1810,1600), Eve20**: condition10 guards **quest12509/step10** and condition11 requires **item29454 >9**. Original outcomes **consume ten item29454** and advance quest12509 (plus the original optional branch advance and related activity results). Visible, normal NPC interaction.
- **NPC22092, placement11, (2250,220), Eve21**: condition3 guards **12509/step14**, offers original quiz options; original `conditionClass10` correct-answer events across question variants 7–12 advance quest12509 by one. Visible, NPC interaction. The wrong-answer path remains original. This patch changes *only a waypoint*, not correctness of answers, questions or event evaluation.

Existing Lua `Logic/MarkManager.lua` has built-in `condition.kind==3` inventory gate `Item.GetItemCount(condition.id) >= condition.count`, using the acquisition target while below threshold, otherwise the stage END.

## Three byte-bounded fields

| Quest step | Binary MarkStep field | Before | Source-backed after |
|---|---|---|---|
| 12509/10 | Condition slot1 (12 bytes) | Zero/unused | `kind3,id29454,qty10,target(49946,1910,1580,1,10)` |
| 12509/10 | END (8 bytes) | Blank | `(49946,1810,1600,1,9)` |
| 12509/14 | END (8 bytes) | Blank | `(49946,2250,220,1,11)` |

Exactly **27 differing bytes** within these 28 authorized field bytes (one byte remains zero). Git/DEV byte offsets 767455, 767557, 768353 respectively. Client offsets 769275, 769377, 770173 respectively. No changes to quests 12509/9, /15, /16, no Eve/Lua/Kotlin or item/state/DB modifications.

Strict source-guarded `tools/patch_dvt_12509_rod_collection_and_riddle_20261012.py` confirms all original NPCs, no close flags, real item grant and quantity, completion item consumption and quest step effect, original question/answer groups with advancement, original neighboring steps, both expected original MarkStep SHA editions, complete 4505-record binary framing, exactly three fields, backup before atomic overwrite and readback. It supports idempotency and refuses unknown editions.

- Git/DEV `IOS_MarkStep_C.dat` **892087 bytes** old SHA `87e30327e5c42ccec30f788022fc28a5f181505c03bb8102fdb3beb14a489ec0` → **`2edf6533584247e20f9d18e19753595b59d9c47fde6960d0630a38f8d1f0532f`**.
- Both staging clients **928425 bytes** old `ccb3d8a119c7adc4e9503a3aaae721e0d20103c300168075610bbac698790f78` → **`78c2f74b0b4ad82932ad385b8f92cb68315a4ce803d405293f0472c7b4dca364`**.
- Source-valid static located target census **5752→5755** (one item target, two END); blank END **242→240**; 4505 MarkStep rows unchanged.

## Deferred in-game test

With quest12509 **step10**, start with fewer than ten rods, click quest: character should head to original NPC24005. Obtain item29454 repeatedly until ten total. Click quest again: should navigate to original NPC15422; only through original Eve result should ten rods be consumed and quest advance. Stage14: clicking quest should lead to visible NPC22092; choose a correct answer, verify quest progress; choose wrong answer in separate replay to verify no incorrect reward/advance. Stage16 previously changed to original Santa entrance and must remain functional.

This patch does NOT guarantee all events operate in a running game. It is source-backed static fix; final E2E test is explicitly queued for the requested later consolidated pass. Do not skip reward verification, especially class13 in unrelated quests.
