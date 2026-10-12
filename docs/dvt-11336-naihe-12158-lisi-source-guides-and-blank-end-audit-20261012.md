# DVT quest11336/1 Naihe + quest12158/7 Li Si — source NPC guides and accurate blank-END triage

2026-10-12 VN. Owner explicitly requests source-backed bulk DVT quest fixes with GitHub PR#41, dev server+staging client deployment and deferred one-pass manual in-game E2E. This commit changes **only two packed MarkStep END locations**, plus improves a **read-only audit** to distinguish source-usable conditional navigation.

## Precise Eve source targets

- **Quest11336 step1**, original source description `Thử tìm đường đến cầu Nại Hà`, originally `END=(0,0,0,0,0)`. Original `gamedata/Eve.emg` scene**60801** has NPC**27074** placement**1**, **(2950,360)**, `close=false`, owner Eve**3**, `when_happen=[True,False,False,False]`. Eve3 has `conditionClass2,param11336,pStyle1,ops2,value0` (in-progress branch), and a separate source absent-quest `pStyle2,ops0,value0` activation branch that adds quest11336+1. New stage1 END **`(60801,2950,360,1,1)`** guides to the *actual source NPC* near Naihe; navigating there does **not** itself satisfy quest. Original following step2 END `(60806,1040,380,1,1)` is preserved.
- **Quest12158 step7**, source description `Cùng Lý Tư đi sâu vào trong Lăng Tần`, originally `END=(0,0,0,0,0)`. Source scene**14661** NPC**40107** Li Si placement**1** at **(270,420)**, `close=false`, owner Eve**3**, ClickNpc `when_happen=[True,False,False,False]`. Eve has original `conditionClass2,param12158,pStyle1,ops5,value7` branch and original **victorious battle `conditionClass8,param9,pStyle1`** clear `resultClass2,param12158,pStyle3,val0`, opens next `quest12159 +1`. New END **`(14661,270,420,1,1)`**. Prior stage6 turn-in at `(14649,510,270,1,1)` unchanged; stage1–5 original source item collection targets and all original Eve/battle effects untouched.

There is NO manipulation of original Kotlin, Lua, Eve, NPC flags, item inventory, mission state or rewards. Binary MarkStep patch is navigation-only.

## More accurate residual audit, based on genuine client Lua

Former read-only blocker tool `tools/audit_dvt_quest_blockers_20261011.py` counted **every nonlocated END as a missing target**, including steps that DO contain a correct original **per-condition navigation destination**. Original DVT client `lua/Logic/MarkManager.lua` `MarkManager.Navigation` selects the **first unmet condition**: `kind1` follower NPC not present or `kind3` required item count not satisfied. While unmet, the target is the original condition's `sceneId,x,y,eventKind,eventId`, overriding an empty END. This behavior is also represented in server-side parity `gamelogic/.../QuestNavigationPolicy.kt`; that server tool does not itself move players.

Added two explicit audit fields **without changing the original raw missing END count**, and a CLI summary:
- `pending_condition_guided_end_targets`: includes a row ONLY if **all** nonzero objectives are kind1 or kind3 AND each has a valid full scene and x or y. Labels each scope **`only_while_a_source_condition_is_unmet`** — **not** an unconditional E2E pass and **not** guidance after the objective is complete.
- `missing_end_without_pending_condition_guidance`: the remainder of the raw blank-END rows, not a statement that all are actual bugs; contains intentionally blank riddles, calendar or multi-option puzzles too.
- After this commit: **69 raw nonlocated END**, **8 condition-guided when pending**, **61 without conditional guidance**. Exactly eight source MarkStep cases are quest12158 stages1–5 with kind3 item targets and quest13047 stages1/3/5 with kind1 monk follow targets. Source locations are validated and remain untouched. **87** separate unlocated objective-target records still exist in overall corpus and are counted separately.
- Most of the 61 remaining no-conditional-guide records have explicit sightseeing/riddle descriptions (quests12782–12924), the **four 12511 next-day Christmas tree reward stages**, the four 12517 observational scene49949 stages (whose known Door1 is an EXIT, not automatically correct first interaction), 12070 chest choice and 11022 alternative battle branches. **Do NOT fabricate destinations for these to force count to zero**.

## Binary and source safety

Guarded patcher `tools/patch_dvt_11336_12158_source_waypoints_20261012.py` checks original NPC id, placement, coords, `close=false`, Eve ownership, click trigger, original exact 11336 activation/active branch and 12158 stage7/battle completion, expected neighboring MarkStep targets, expected mission row counts and description, edition full SHA, original packed END field scanner + independent readback, SHA-backed rollback, atomic replace and idempotence. The patch changes **two original 8-byte END fields, exactly 16 actually differing bytes**, no other bytes.

- Git/DEV MarkStep length **892087** old SHA `e3ba70127cb69cfa9b51e47864015eca288fa91b56d6d5616b98a203cd793f5c` → new **`e0d5135b01ea5331c69384174a6ab0a0db05a7aa1189dfd1b3d7aa94cdbeca35`**.
- Dual localized staging client edition length **928425** old SHA `fd257e2defe6a83c3bdd4205e3930892ef3fbbdd179e7194f650ec8425576fe0` → new **`3521cd4e21059ecac3f2fe9b82de87f6fb892e221997f088673c2f2d21835a37`**.
- Git+DEV byte offsets start 8-byte END: quest11336/1 **337351**; quest12158/7 **716207**; offsets in staging files must be computed by the parser, NEVER assumed offset shift from Git.
- Static source-located targets **5924→5926**, raw blank-END metric **71→69**, all-zero END **67→65**; **4505** unchanged MarkStep rows, zero unknown source scene/NPC/Door endpoints.

## Deferred consolidated game E2E — NOT PERFORMED

11336 stage1: click quest → scene60801 NPC27074, genuine source story/event and next stage advancement only if original condition allows; stage2 NPC27074 scene60806 remains correct. Map access, relog and auto-walk cross-scene remain untested.

12158 stage1–5: before item collected, click mission → five original Qin tunnel condition kind3 item targets; on collecting item validate original item count, step advancement, no invented return NPC. Stage6 returns original Li Si scene14649; stage7 now navigates original NPC40107 scene14661 (270,420), verify correct battle trigger, victory only clears mission and starts12159, loss/flee not counted, no duplicate reward, relog. On already-met inventory condition + empty END, Lua fallback can still have no nav: audit deliberately says *only while pending*, do NOT claim pass.

Both quests manual E2E remain in the single owner-approved later gameplay run.
