# DVT quest13131 Tô Tiểu Muội trick-or-treat — three source-authentic candy Door guides

2026-10-12 VN. Navigation-only change, **not three automatically completed trick-or-treat interactions**. Owner explicitly defers character-level in-game E2E for one later combined pass. Evidence from original `gamedata/Eve.emg` scene54922 and `File.dat/Data-ios/IOS_MarkStep_C.dat`.

## The defect and source proof

Quest13131 stage1 original MarkStep END **`(54922,390,480,1,27)`** already goes to **Su Xiao Mei NPC15062** at placement27. Stages2,3,4 are all completely blank END and say **`帶著蘇小妹去要糖果吧！`** (“Dẫn Tô Tiểu Muội đi xin kẹo”). Original scene54922 contains many doors with genuine, quest-aware **`when_happen=[False,True,False,False]`** source Eve events; all can provide alternative trick-or-treat progression, so no unique compulsory order can be inferred.

Three different real source Door owners were selected as **nonadaptive suggested waypoints**:

| Quest stage | Source map | Original Door | Position | Eve | New packed MarkStep END |
|---|---:|---:|---|---:|---|
| 2 | 54922 | 7 | (540,140) | 31 | `(54922,540,140,2,7)` |
| 3 | 54922 | 3 | (740,220) | 32 | `(54922,740,220,2,3)` |
| 4 | 54922 | 4 | (980,260) | 33 | `(54922,980,260,2,4)` |

Each authentic source Eve contains `conditionClass2,quest13131,pStyle1,ops2,value1`, source eligibility with four **class7,pStyle3,ops6** item IDs **38030,38029,38028,32003**, eligible quest+1 result branch, and a stage4 `conditionClass2,quest13131,pStyle1,ops5,value4` finish branch with exact **resultClass2,pStyle3,0 quest completion, start quest13132 and quest12499, original item29433×1**. Unlike Eve located on an NPC, these events are **Door-owned**, which is why setting event kind 2 is required. Original girl NPC15062, original all item/follower visibility gates and all source rewards untouched.

**Critical caveat:** There are many interchangeable original candy Doors. A player who manually visits an alternative house may exhaust/use a later fixed waypoint. The source still has other valid Doors, but this patch is not a dynamic nearest-eligible-door selector. Do not claim that three exact Doors are mandatory, guaranteed currently eligible, or necessarily the best path. Any runtime Kotlin handling of class7/source follow NPC or individual Door availability still needs the deferred full gameplay E2E.

## Byte-level audit / rollback

Guarded patcher `tools/patch_dvt_13131_three_candy_source_doors_20261012.py` checks original Su Xiao Mei stage1 NPC, exact three original Door placements and Eve owners/trigger kinds, original class7 prerequisite items, quest increment and stage4 source finish+reward, original MarkStep schema and descriptions, exact 8-byte END boundaries, predecessor full SHA, atomic swap plus SHA-checked backups and post-swap readback. **Three 8-byte END fields only, 22 actually differing bytes**.

- Git+DEV binary892087 bytes old SHA **`5d89bbc211ae6c1cbc9cf55e846cbb13634adc8b25cd081897ba7d11a28896f0`** → new **`e3ba70127cb69cfa9b51e47864015eca288fa91b56d6d5616b98a203cd793f5c`**.
- Two localized staging client binary928425 bytes old **`4e85f8647afd5a76345dfef771153b72b7197a36fb2d156100246d4636d88851`** → new **`fd257e2defe6a83c3bdd4205e3930892ef3fbbdd179e7194f650ec8425576fe0`**.
- Server/Git 3 END offsets `865757,865878,865999`; BOTH staging client offsets `878629,878818,879007`. Do not assume uniform client offset shift; client localized text changes record sizes.
- 4505 original MarkStep rows, source-valid located navigation destinations **5921→5924**, audit missing-END **74→71**, raw all-zero END **70→67**, unlocated condition objectives **87 unchanged**. Zero invalid scene/NPC/Door references.
- Tests `tools/test_dvt_13131_three_candy_source_doors_20261012.py` verify original Eve stage gates and class7 rewards, exact source door ownership, full binary SHA and source census.
- Deliberately **did not patch quest12517 steps2/4/6/8**: original scene49949 Door1 event may advance quest *on leaving the village*, but sending player directly to the **exit** would skip observational objectives, while local villagers NPC15018.. etc have no proven stage-specific Eve. Requires runtime source mechanism review and consolidated playtest.
- Remaining flagged blank-END condition objectives in quest12158 (kind3 item acquisition) and quest13047 (kind1 follow monk) have valid per-condition targets, and source DVT Lua dynamically navigates to those targets when pending: do not force fake END while pending.

## Deferred one-pass E2E — NOT RUN

Disposable character with source quest13131 stages1–4: click stage1 → original Su Xiao Mei NPC15062 scene54922; stage2/3/4 click → real three **distinct** Door targets and source Eve trigger, verify correct follower NPC15062 present, class7/other eligibility, source quest+1 only if legitimate, no advancement from walking to doorstep alone, stage4 correct item29433×1 and follow-up quests13132/12499 only once under original source gate. Test two alternative source Door selections out of order, missing item, relog, used/hidden Door and duplicate rewards; test source girls NPC visibility and original Door graphic. **Manual in-game E2E has not been run yet**.
