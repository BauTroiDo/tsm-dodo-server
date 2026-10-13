# DVT quests12998 & 12936: five source-proven stage-specific NPC navigation targets

Date: 2026-10-12 Vietnam. **Source/static verification only; no in-game character tests until consolidated pass**, as requested.

## Root problem

The active `File.dat/Data-ios/IOS_MarkStep_C.dat` has blank END for quests **12998 stages1 and2** and **12936 stages4,9,21**, despite original `gamedata/Eve.emg` defining actual quest-stage-specific NPC conversations which advance the same mission by one step. The original DVT `lua/Logic/MarkManager.lua` cannot start destination navigation for a blank END.

## Source-derived targets and transitions

| Mission/step | Original narrative | New END (scene, x, y, type, placement) | Original NPC and Eve quest-stage advancement |
|---|---|---|---|
| 12998/1 | Invite 雁貂雪 from the inn to summer festival | **(12007,590,480,1,4)** | NPC **38414**, map12007 Eve5, quest12998 step1→2 |
| 12998/2 | Invite Đại Kiều / Tiểu Kiều at Trác Quận teahouse | **(12018,850,300,1,5)** | NPC **38412**, map12018 Eve6, quest12998 step2→3; secondary NPC38413 placement6 Eve7 remains unchanged |
| 12936/4 | Find Quách Nữ Vương at Lữ Long Khẩu | **(12421,490,520,1,10)** | NPC **11123** Eve8, quest12936 step4→5 |
| 12936/9 | Find Chân Mật at Hương Dũng quân square | **(12701,270,420,1,9)** | NPC **11074** Eve14, quest12936 step9→10 |
| 12936/21 | Find Mạnh Hoạch at Hương Dũng quân square | **(12701,850,620,1,10)** | NPC **14644** Eve16, quest12936 step21→22 |

Every selected NPC is in its authoritative scene `npcs`, has its exact attached Eve interaction `when_happen=[True,False,False,False]`, an exact `conditionClass2,questId,pStyle1,ops5,stage` gate, and original result `resultType0/resultClass2/same quest/pStyle1/value1`. All gates verified have `and_num==1`, no additional AND preconditions in the selected advancing branch.

**Important dynamic NPC semantics:** All five original scene NPC entries have **`close=true`** (quest-dependent/dynamic), preserved exactly. This fix does **not** force them open, spawn replacements, invent battle targets, or guarantee their visibility in game. Existing original MarkStep **12998/3** already routes to `close=true` NPC38411 in scene12003; **12936/5** already points to NPC11123, **12936/10** has a follower objective for NPC11074, and **12936/22** returns to a quest-conditional NPC. These original references corroborate waypoint use for stage-gated NPCs; actual dynamic display remains a *deferred E2E check*. If they stay invisible in game, inspect the separate client/server Eve spawn/display lifecycle without bypassing quest stage guards.

Quest12936/16 is a **separate map-entry-triggered objective** rather than a normal dialogue NPC. This stage was repaired in the subsequent independent data-only patch documented in `docs/dvt-12936-step16-map-entry-auto-navigation-20261012.md`; no new NPC was invented. Quest12998 stage descriptions still contain original Chinese; this navigation patch intentionally does not rewrite UTF-16 text or modify later offsets.

## Safe binary patch and test

`tools/patch_dvt_12998_12936_source_npc_routes_20261012.py` independently verifies each Eve NPC, exact stage and same-quest +1 transition, quest12998 teahouse secondary guest, next-stage source evidence, original blank END and no active conditions, known full input SHA, exact packed field offset; writes only **5 END fields (5×8=40 bytes)** with per-copy SHA backup, atomic write and readback. Idempotent and refuses any unrecognized source data edition.

- Repo/DEV active MarkStep size **892087** bytes. Old SHA `4acb01edebaf1f2fee8240bd4bb61d949c6146515da792a1869409cd66f20939` → new **`98b8ac110c68acc8e2921b21f965e57e763878cdda4e69f015651a39599f2aa1`**. START offsets by target: 12998/1 **855128**, 12998/2 **855277**, 12936/4 **841682**, 12936/9 **842763**, 12936/21 **844260**.
- Both client staging editions size **928425** bytes. Old SHA `de917171a4ae9acdab65566a93d146e70f5fca691724cbee9a6d03f848b3d2e2` → new **`6de2b89ace1c97c8ff674352da767bf02a57be78ccc1768cc58f4227d204834d`**. START offsets: 12998/1 **859914**, 12998/2 **860197**, 12936/4 **843502**, 12936/9 **844583**, 12936/21 **846080**.
- Static source-backed located destination count **5756→5761**, blank END **239→234**, all **4505** quest records remain structurally identical in server/client editions; all other bytes, NPCs, spawns, quest steps and rewards unchanged.
- New regression `tools/test_dvt_12998_12936_source_npc_navigation_20261012.py` asserts all five targets, exact original Eve interaction and quest advancement, quest-dependent close flags remain, old stage16 scene-entry remains untouched, zero invalid NPC/door/scene IDs, precise checksum/idempotency and adjacent original stages; added CI workflow step and updated earlier test SHA/census fixtures.

## Deferred consolidated character E2E

In DEV with disposable quest-state character: for 12998 at stage1 click mission → map12007 NPC38414, verify NPC becomes visible when eligible, dialogue advances to stage2. Click stage2 mission → map12018 NPC38412, check secondary guest38413 as directed by original dialog and step3 progression, then return to original 12003 NPC38411. For 12936 at stage4,9,21 respectively click → correct original stage-gated NPC, verify visibility, quest event and exact +1 step with no premature effects; also verify quest stages5 (letter item29521),10 (Chân Mật NPC11074 follower),22 (return Chúc Dung) and stage16 special scene trigger unaffected.

**No manual/in-client E2E has been run in this checkpoint.** This task is a precise route restoration, not a claim to fix every quest-chain/Kotlin event failure.
