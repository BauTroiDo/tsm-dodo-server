# Four DVT Nặc Ba Đậu Yêu capture arenas — 16 source-valid ordered NPC guides

2026-10-12 VN. This is another batch after 30 source area guides to accelerate the owner's request to reduce 150 originally missing MarkStep ENDs. Source-backed binary guides **only**. User explicitly postpones manual in-game character tests to one combined E2E session.

## Mechanics and important adaptive-availability limitation

Four quests **12358 / 12362 / 12366 / 12370** have authentic first-stage entrance Door1 routes already restored in prior commit542ccd4, but stages2–5 still have completely blank END. Their original `gamedata/Eve.emg` arenas have **five original equivalent quest-aware catch monsters** (placements2–6) each, initially visible (`close=false`). Every one of the five original NPC Eves has the same quest gates: `conditionClass2/paramQuest/pStyle1/ops2/value1` (quest progress >1), exact `pStyle1/ops5/value3`, `value4`, and `value5`, plus its own battle-outcome `conditionClass8,param=placement-1,pStyle1,ops0/value0`. **Only source battle outcome** has quest resultClass2 `pStyle1,value+1`. The source quest stage5 condition has original quest clear `resultClass2,pStyle3,value0` and starts the next mission (`quest+1`) and common quest12321.

Because the source gives **five alternatives** and no single forced combat order, the new MarkStep guides deliberately choose **FOUR DISTINCT ORIGINAL NPC placements2/3/4/5 for stages2/3/4/5**, never the same NPC repeated, while leaving fifth original NPC6 available. This is a **source-valid suggested battle/capture route**, not guaranteed adaptive to a player who manually fights a different NPC first. If an already-used NPC is hidden or unavailable, choose an alternative in that arena; static MarkStep END cannot automatically choose based on per-NPC used-state without implementing real client/Kotlin dynamic guidance. This critical limitation must be tested, **not hidden by reporting merely '16 fixed'**. No NPC spawned, no battle outcome forced, no quest state/visibility/loot/rewards changed.

## Exact stage2–5 guide targets

| Quest | Scene | Source template | Stage2 / NPC2 | Stage3 / NPC3 | Stage4 / NPC4 | Stage5 / NPC5 |
|---|---:|---:|---|---|---|---|
| 12358 | 62007 | 22087 | (210,260) | (1310,420) | (810,1120) | (30,1000) |
| 12362 | 62008 | 22088 | (210,260) | (1290,440) | (50,980) | (810,1120) |
| 12366 | 62009 | 22089 | (90,360) | (1270,480) | (70,1020) | (850,1120) |
| 12370 | 62010 | 22090 | (210,300) | (1270,460) | (1330,760) | (850,1120) |

Each END is packed as `(scene,x,y,kind1,npcPlacement)`. All four quest stage1 Door1 waypoints untouched, and source NPC6 remains a valid alternative. The repair does not alter companion/training/teleport NPC1 of any scene, nor the original stage5 completion.

## SHA, exact fields and safe rollout

- Source patcher `tools/patch_dvt_123xx_four_capture_battle_routes_20261012.py` checks all 20 equivalent original NPCs across four scenes, validates distinct chosen NPCs, source ClickNpc Eve trigger and four original quest stage gates, specific battle-win resultClass2/+1, stage5 clear+followup, existing stage1 Door1, original all-zero stage2–5 END, complete predecessor SHA, independent packed-field scanner and checksum-verified atomic backup. Exactly **16 original 8-byte END slots, 121 actually differing bytes**; no other binary data changed.
- Git/server+DEV 892087 bytes predecessor SHA `8ee5a121997a2e1a1ab38338215da956e42ac86d51d1ca7b93bad2b607c65f0a` → **`7306310a6d8599f847c1c25d8db57356a9a15bf1d5e8c9e5a4dcd4b03e312f4c`**.
- Both local staging client files 928425 bytes predecessor SHA `a0814cec7dd5d8d272170fa995ec1741b4cdfb3048e7645f54f9ef3b917432c2` → **`9812fb7c733af74629f5d6eb1f385e5519139844ff32a5b3e2f72e1dba4f9ded`**.
- Server END offsets for q12358 stages2–5: `741135,741360,741585,741736`; q12362: `742098,742323,742548,742735`; q12366: `743097,743322,743547,743712`; q12370: `744074,744299,744524,744713`. For these quest rows, client END offsets are server+1260 due to client-vs-server field layout. **Do NOT assume same offset shift for unrelated quests.**
- Static valid located targets **5875→5891**, broad missing-END metric **120→104**, literal zero END **116→100**, total 4505 records unchanged, no missing source scenes/NPCs/Doors. The net gain since user's 150-milestone acceleration request is **46 navigation END fields** across six quest groups; **this does NOT mean 46 complete quest chains or E2E passes**.

## Deferred E2E

For each q12358,12362,12366,12370, test step1 Door1 correct source, then steps2→3→4 capture first source NPC2 then3 then4, stage5 NPC5 final branch, with event-specific conditionClass8 proper battle victory vs loss/flee and no repeated mission+1. Test **alternative ordering**: fight NPC3 first at stage2 and confirm static stage3 guide may now point to already-used NPC3; user can still use NPC6 or another source alternative. If that is an unacceptable UX blocker, follow-up requires a **dynamic availability-aware nearest eligible source NPC resolver** grounded in original client Lua/IL2CPP and Kotlin state, not a fake fixed MarkStep target. Validate quest completion and follow-up, relog, A* path within arena, visible/hide source objects, no duplicated rewards. **No manual gameplay character E2E yet per owner instruction.**
