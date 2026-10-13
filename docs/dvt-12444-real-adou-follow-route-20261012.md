# DVT12444 — restore the unique genuine A Dou follower navigation in haunted house

Checkpoint 2026-10-12 / PR #41, following commit `430f95f`. This is a precise **one-condition** MarkStep waypoint fix. No in-game E2E until the planned consolidated sweep.

## Source proof and root cause

Original DVT `gamedata/Eve.emg` was scanned across **all scenes and events** for the exact follower attachment effect `result(type0,class8,param38265,pStyle1,value0)`. There is exactly **one** match worldwide: **scene54947 Eve1, condition7**, gated by `conditionClass2 mission12444 pStyle1 ops5 value1`. The owner is original visible **NPC placement1 / npcType38265** at **(1050,540)** and is a true `ClickNpc` Eve.

Stage2/3/4 each have NPC38265 clones in that same scene (placements2/3/5) with original stage-gated dialogue, but **none** has the follower-attachment result. Adjacent haunted-house scenes **54924–54927** have more NPC38265 dialogue/misleading trial steps but likewise lack that result. Pointing these three stages to clones or treating them as certain recruitment endpoints could spoil the puzzle, so they remain untouched.

MarkStep mission12444 steps1–4 all have original condition kind1/NPC38265/count1 and a return END `(54922,1310,960,1,1)`. END is the *different* original quest giver **NPC38266** (Lưu bá bá); do not route the unmet follow objective back to that NPC. Original Lua `MarkManager.Navigation` prioritizes an unmet condition target over END: stage1 CONDITION_1 being `(0,0,0,0,0)` breaks click-to-navigate at the start. Route the true original follower NPC instead.

## Single authorized binary mutation

**Quest12444 step1 CONDITION_1 only**: `(0,0,0,0,0)` → **`(54947,1050,540,1,1)`**. No END changed; quest stages2/3/4, all NPC placements, events, Lua/Kotlin, dialog, item/reward logic unchanged. Navigating to A Dou does not automatically attach the follower, complete the quest, advance a wrong step or grant items — original Eve dialogue still controls it.

- Server/Git file `File.dat/Data-ios/IOS_MarkStep_C.dat` length **892087**, full SHA256 **a2ddb57f24debb314d2d5bc59b7721d99561717e6bad3252c35c1280150c80da** → **11bba0ad361ff96266be77db1a3b5d6fcac37f82cd0345fb0c44777e8dbc1df2**.
- Two localized staging client data copies length **928425** each, full SHA256 **511563050ea38ca86e8b85a9ec954fb1b8165ec12403b34521f3acf6b33e40f7** → **47c39a0b43e0e68117c1a964ade04af50adb879533d865a3d6df7d86cf2e6eed**.
- Exactly **8 changed bytes** per edition in one packed condition waypoint. Atomic backup, complete input SHA allowlist, independent packed roundtrip, source stage proof and idempotent global SHA check via `tools/patch_dvt_12444_real_adou_navigation_20261012.py`.
- **4505** original MarkStep records intact. Source-located targets **5934 → 5935**; missing per-condition targets **85 → 84**; blank END remains **63**, of which **8** are condition-guided while unmet and **55** without such guidance. No static NPC, door or scene references missing.

Dedicated tests: `tools/test_dvt_12444_unique_adou_route_20261012.py` assert uniqueness across entire source Eve, genuine correct NPC, decoy preservation, SHA/idempotence/tamper rejection, full audit. Prior full-SHA and census tests updated, dedicated CI job step inserted into `.github/workflows/dvt-data-audit.yml`.

## Deferred manual walkthrough

1. With active mission12444 step1 and no NPC38265 following, click mission to navigate to original 54947 NPC1 (1050,540), not the return NPC38266 or a clone.
2. Talk to the correct A Dou and make the original interaction. Only source Eve may attach him. Verify no premature completion/rewards.
3. With A Dou already following, check original return destination (54922,1310,960,NPC1 NPC38266); original quest giver must remove follower and handle the source reward exactly once.
4. For stages2–4, deliberately explore decoy room dialogues/maze choices; do not expect this patch to solve their multiple-path puzzle or forcibly advance them.

DO NOT infer that the three remaining unlocated condition targets are bugs until their special stage transition/capture semantics have been tested.
