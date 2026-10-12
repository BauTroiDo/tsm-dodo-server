# DVT group patch — 16 source NPC chest guides in four arenas

2026-10-12 VN. Owner wants faster completion of flagged missing-END destinations. Source-supported **static alternate-choice navigation only**. Actual in-game E2E explicitly postponed to one combined test later.

## Original problem / source authenticity

Original MarkStep has zero END at steps2–5 across quests **12330,12346,12350,12354**, four three-chest event arenas. Their original stage1 entrance Doors already have source routes. Original `gamedata/Eve.emg` has precisely six real source NPC20001 chests in each map, `close=false`, each linked to original quest ClickNpc Eve with `when_happen=[True,False,False,False]`. Every chest Eve checks quest progress `conditionClass2,pStyle1,ops2,val1` and shows the same quest progress/finish branch. Opening the selected source chest runs original `resultClass3,param=<chest placement>,pStyle3,val2000` and `resultClass2,param=quest,pStyle1,val+1`; original quest-stage3,4 events exist and when stage5 is met, clears that quest (`resultClass2,pStyle3,val0`) and grants follow-up quest+1 and common quest12321. The patch **does not trigger or change any Eve**, grant rewards, choose random chest, alter NPC flag, bypass chest interaction or force stage completion.

Each arena has six interchangeable original chests, but binary MarkStep END is one fixed target. Therefore stages2,3,4,5 guide to **four DIFFERENT real source chest NPC placements**, reserving two other original chests. These are **suggested, not adaptive, not enforced**. If a player opens chest out of order, a later suggested chest may have already been hidden. Original remaining chests still exist. Full dynamic availability resolution would require client/Kotlin state-aware navigation, not a binary END.

| Quest | Scene | Stage2 | Stage3 | Stage4 | Stage5 |
|---|---|---|---|---|---|
| 12330 | 62003 | NPC2 (3090,2800) Eve5 | NPC3 (290,2300) Eve6 | NPC4 (290,760) Eve7 | NPC5 (3090,1500) Eve8 |
| 12346 | 62004 | NPC1 (310,2300) Eve6 | NPC2 (1410,1460) Eve7 | NPC3 (2850,1660) Eve8 | NPC4 (3630,2800) Eve9 |
| 12350 | 62005 | NPC1 (2810,180) Eve7 | NPC2 (290,200) Eve8 | NPC3 (1950,1460) Eve9 | NPC4 (1130,2740) Eve10 |
| 12354 | 62006 | NPC1 (330,160) Eve12 | NPC2 (1270,2980) Eve13 | NPC3 (2250,2920) Eve14 | NPC4 (2830,220) Eve2 |

All `END=(scene,x,y,kind=1,placement)`. No original stage1 Door1 route was changed.

## Binary guard, checksums, audit

Patcher `tools/patch_dvt_123xx_four_chest_choice_routes_20261012.py` verifies four original map Door1 entrances, six source NPC20001 per scene, real ClickNpc owner/placement, each source quest progress and +1 effect, current chest `resultClass3` hide, stage5 original finish+next+common missions, original stage text, expected predecessor full SHA, exact packed MarkStep END fields. Checks every other byte unchanged, creates SHA-checked backups, writes atomically and is idempotent.

- 16 original packed 8-byte END fields, **124 actually changed bytes**.
- Source Git/server+DEV MarkStep 892087 bytes old `7306310a6d8599f847c1c25d8db57356a9a15bf1d5e8c9e5a4dcd4b03e312f4c` → new **`16b5a3430afcf0cc8cbf0115b3d4ca8d7035269537fef0a55e9b2db6a3845043`**.
- Both staging clients 928425 bytes old `9812fb7c733af74629f5d6eb1f385e5519139844ff32a5b3e2f72e1dba4f9ded` → new **`4ef26a52beb9d8a0a864b6688bff0264e3b71022dcc343e0db9e11cf91c66c15`**.
- Git/server END offsets: 12330/2–5 `737185,737420,737613,737790`; 12346 `738176,738411,738604,738781`; 12350 `739167,739402,739595,739772`; 12354 `740158,740393,740586,740773`. Client offsets for THESE quest records +1260. Don't generalize to others.
- 4505 original MarkStep records, no invalid original scene/NPC/Door targets, source-valid static navigation census **5891→5907**, broad blocker-audit missing-END **104→88**, raw zero END **100→84**, unlocated condition objectives unchanged **87**. Other 88 reported missing END include kind3 item collection with real `condition.target` (not necessarily blocking navigation), intentional time gates and puzzle clues. Do not indiscriminately patch.
- Prior stage1 entry Door patcher and regressions updated to explicitly accept the newly source-verified per-stage chest choices, as had already been done for four monster capture quests.

## Deferred combined gameplay E2E — NOT TESTED YET

On disposable character at each q12330/12346/12350/12354: stage1 map entrance Door1, stages2/3/4/5 click quest and follow correct distinctive real NPC20001 chests, confirm each ClickNpc Eve consumes one chest (hides it), advances mission only through source result, no double-progress/reward; stage5 clears + starts next quest and common quest12321. **Test out-of-order manually opened chest**, when a later fixed waypoint may already be hidden, and retry another original NPC; verify each quest can complete without choosing prescribed chest order. Test relog, repeat chest use, visibility, pathfinding and quest reward IDs with original Kotlin semantics. This is **not dynamic chest availability** and not an E2E pass.
