# DVT quest12656 Golden Pig rock-paper-scissors — original NPC across 12 stages

2026-10-12 VN. Source-based **mission auto-navigation repair only**. Manual character-level E2E is explicitly deferred by the owner to ONE later combined playtest.

## Original problem, source and safe target

All 12 MarkStep rows of quest12656 have `END=(0,0,0,0,0)`, even though their instructions send the player to Golden Pig Immortal (Kim Trư đại tiên) to play ten rounds of rock-paper-scissors and collect the prize. Original DVT source `gamedata/Eve.emg`, scene **49960**, has an actual **NPC38093**, placement **1**, **(610,360)**, marked **`close=true`**, with original click events **Eve4 and Eve5**. Both have `when_happen=[True,False,False,False]`, and are attached to **npcs[].events=[4,5]**, NOT any Door/scene-entry event. Therefore the only source-authentic direct auto-navigation destination for every quest12656 step is **`(49960,610,360,1,1)`**.

## Crucial source event-stage semantics (do NOT auto-grant or bypass)

- **Stage1:** Original Eve5 `conditionClass2,param12656,pStyle2,ops0,value0,and_num3` initiates the minigame. The original result branch has `resultClass2,param12656,pStyle1,value1`. Original separate `conditionClass2,pStyle1,ops5,value1` branches and choice menus are unmodified.
- **Stages2–11 (ten rounds):** Original Eve4 has **range conditions**, not ten exact stage-specific Eve events: `conditionClass2,param12656,pStyle1,ops2,value1` combined with `ops1,value12` in AND3 and AND4 branches. One qualifying branch invokes the original **resultType6,class3** interaction/dialogue; another original `conditionClass10,param2,pStyle30` event has source result `resultClass2,param12656,pStyle1,value1` (a callback-driven round transition). These mechanisms remain original and **are not guaranteed to work simply because the client arrives at the NPC**. Never fabricate separate steps, NPCs or victory outcomes; the stage range is source-proven.
- **Stage12:** Original Eve4 has a precise `conditionClass2,param12656,pStyle1,ops5,value12,and_num2` branch with additional `conditionClass7,param2,pStyle2` requirement before awarding **item29471 ×10**, clearing quest12656 via `resultClass2,param12656,pStyle3,value0` and starting original follow-up quest12657. This reward logic is NOT modified. Another conditionClass7 branch may show only the dialogue, so in-game conditional state must be checked.
- Source NPC `close=true` untouched. No Eve, Kotlin, client Lua, NPC visibility, game state, reward, inventory, quest step, animation or actual rock-paper-scissors code changed.

**Other quest audits intentionally excluded from this patch:**
- **12511** (18-stage daily Santa repeat): original Eve step triggers are concentrated at stages3/7/12/18 and it is day-conditional; do NOT fill all blanks blindly.
- **12634** (16 item-search puzzle steps at Chu grandfather house): no unique stage-specific Eve event for each substep, and stage17 return waypoint is already valid; no fabricated destinations.
- **13119** (14 Chinese-text Wu Gang chopping actions): zero stage-bound NPC/door events located in the current Eve scan, so no source-backed target yet.
- **12517** observation stages2/4/6/8 at Tu Âu Trang: scene49949 Door1 is **the exit** back to Santa, not the scene's observation targets; do not route the player to an exit door merely to reduce missing END count.

## Exact data guard and audit

`tools/patch_dvt_12656_rock_paper_scissors_npc_navigation_20261012.py` requires original scene49960 NPC38093 placement1 with Eve4/5, no Door binding, original stage1 initiation, both mid-round range conditions and original type6 dialogue, callback `resultClass2` progression, source stage12 **class7-gated reward + completion**, quest12-row MarkStep schema, no other objective, input full-file SHA and independent packed END offsets. Writes **only 12 pre-existing 8-byte END fields (96 actual differing bytes)** with SHA-verified backup and atomic swap, idempotent if already fixed. Neither runtime Kotlin code nor quest effects altered.

- Git/source+DEV 892087 bytes predecessor `55f09d04eca91d488ab85113a33550e207db0943e5784093ae1b42dffa2ab867` → new **`91684c0a287e24b31a5e251322e19a69e96f6961e28d4c90bbadcc40055162f0`**.
- Two staging client files 928425 bytes predecessor `24d936d4f95368888425d2b81b4d4fb33f7d2c2794379743b4c924c20484161c` → new **`3b918753986b8688b24bf3931d2ace6d7051fbf99f2bb5c99f24f83afc224f0b`**.
- Git+DEV byte starts, quest steps1–12: `797937,798132,798325,798518,798711,798904,799097,799290,799483,799676,799869,800084`. Client steps1–12: `799757,799952,800145,800338,800531,800724,800917,801110,801303,801496,801689,801904`.
- Source-located valid targets **5833→5845**; audit missing-END metric **162→150**, literal all-zero END **158→146**, unchanged 4505 MarkStep records, no invalid NPC/scene/Door. All stage routes have the exact **same source NPC** rather than guessed individual per-round destinations.

## Deferred one-pass E2E (NOT RUN)

Use disposable DEV character quest12656 stage1 then stage2–11 with remaining 10→1 rounds, click mission and verify auto-route to **source NPC38093**, reveal correct NPC only when original event allows, open genuine original choice/minigame UI and advance one round only after valid selection/result. At stage12 check `conditionClass7,param2,pStyle2` eligibility, **exactly item29471×10** with actual completion and quest12657 start (only if appropriate), no early or repeated reward; verify relog/repeat click and interaction alongside other scene49960 NPCs/Doors. Also ensure the client does not automatically walk to a nearby unrelated exit Door when quest is clicked. **Manual game-character E2E remains intentionally deferred.**
