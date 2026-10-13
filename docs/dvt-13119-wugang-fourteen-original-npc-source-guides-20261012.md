# DVT quest13119 — 14 original Wu Gang tree-chopping source NPC routes

2026-10-12 VN. Source-backed navigation-only batch. All actual game-character E2E is deferred to one later consolidated pass per owner. Do not confuse 14 new valid MarkStep END locations with 14 successfully performed chopping interactions or full server result support.

## Root cause and original source mechanism

Original server/Git `File.dat/Data-ios/IOS_MarkStep_C.dat` has **14** completely blank END values on quest13119 steps1–14; staging clients have precisely the same structural 14 mission rows but **already Vietnamese-localized** descriptions (`Giúp Ngô Cương chặt cây quế!`) rather than source Git Traditional Chinese (`幫助吳剛伐桂吧！`). Both Chinese and Vietnamese strings are recognized but left unchanged.

Global **condition AND result** scan of authoritative `gamedata/Eve.emg` found original NPC **38465, placement1, at (1610,820), `close=false`, Eve1** in **BOTH scenes 54937 and54938**. Both Eves are genuine `when_happen=[True,False,False,False]` ClickNpc, not Door/auto-entry, and contain the same quest-stage range and interaction result mechanisms. Therefore **`(54937,1610,820,1,1)`** is a valid guide for all 14 steps, and **`(54938,1610,820,1,1)`** is also a real alternative. This patch deliberately chooses fixed scene54937 without falsely asserting that scene54938 is invalid or that Lua dynamically chooses a closer copy.

Eve1 gate `conditionClass2,param13119,pStyle2,ops0,value0` supports starting the mission; ongoing stages use `conditionClass2,param13119,pStyle1,ops2,value0` producing original dialogue/choice `resultType6`. Original callback `conditionClass10,param1,pStyle30/31/32` delivers `resultClass2,param13119,pStyle1,value+1/+2/+3`, **not one forced +1 per visit**. Range guards are source `(ops2 lower0, ops1 upper5)`, `(lower4,upper10)`, `(lower9,upper15)`. Above quest stage14, source `conditionClass2,pStyle1,ops2,value14` with result **complete quest13119, start quest13121 and award item29701×50**. No original mission state, random choice, NPC conditions, class10 callbacks or reward code changed. **This is just location/navigation; runtime game minigame/results must still be verified.**

## Exact guarded binary changes

`tools/patch_dvt_13119_wugang_source_tree_routes_20261012.py` validates both original source scene NPC owners/positions, the original ClickNpc trigger, start, ongoing dialogue, 3 callback increments, all source range guards, completion / follow-up / source 50 item reward, stage1–14 quest and source text, full predecessor edition SHA, independent packed END offsets; refuses unknown editions and modifications outside fourteen 8-byte END fields. It checks backup full SHA, atomic replacement and re-read SHA and is idempotent.

- Git+DEV MarkStep 892087 bytes old SHA **`16b5a3430afcf0cc8cbf0115b3d4ca8d7035269537fef0a55e9b2db6a3845043`** → new **`5d89bbc211ae6c1cbc9cf55e846cbb13634adc8b25cd081897ba7d11a28896f0`**.
- Staging client **both** editions 928425 bytes old **`4ef26a52beb9d8a0a864b6688bff0264e3b71022dcc343e0db9e11cf91c66c15`** → new **`4e85f8647afd5a76345dfef771153b72b7197a36fb2d156100246d4636d88851`**.
- EXACT **14 8-byte END fields = 112 differing bytes**, no other changes.
- Server/Git byte start offsets stage1–14: `863719,863828,863937,864046,864155,864264,864373,864482,864591,864700,864809,864918,865027,865136`.
- Staging client original 14 END field offsets: `875143,875332,875521,875710,875899,876088,876277,876466,876655,876844,877033,877222,877411,877600`. **Client offsets ARE NOT a constant +1820 here** because language/localized mission text changes original binary record length; patcher scans each edition.
- Original 4505 MarkStep rows, static source-located targets **5907→5921**, broad missing-END audit **88→74**, raw completely blank END **84→70**. A blank END with nonzero *condition* target is not necessarily a broken quest: DVT Lua `MarkManager.Navigation` dynamically routes kind1 follower and kind3 item conditions to their per-condition destination while not complete. Such cases must be classified separately rather than patched to artificial END.
- `tools/test_dvt_13119_wugang_source_tree_routes_20261012.py` pins source both scenes, callback gates and threshold, exact 14 source endpoints, full SHA/idempotence, no invalid scene/NPC/Door corpus.

## Deferred combined gameplay E2E (NOT RUN)

On one disposable DEV character, quest13119 stage1..14: click mission should guide to real Wu Gang/NPC38465 in scene54937, or manually use original equivalent map54938; verify start original minigame, original choice/result behavior, proper +1/+2/+3 increments, threshold4/5 and9/10 boundaries without stage skipping bugs, completion only after >14, exactly item29701×50 plus follow-up13121 once only, relog/repeat reward safety, and both original map routes accessible in scene graph. **Do not claim 14 stages E2E pass after only static source/CI.**
