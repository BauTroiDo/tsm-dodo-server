# DVT novice mission 12280 orphan step6–10 — 2026-10-11

## User priority
Finish diagnosing and fixing quest navigation, next-step progression, completion, map unlocking and rewards before the Gia Viên/Định hôn/second-generation child UI.

## Exact source evidence

Authoritative DVT staging/client/server `Eve.emg` SHA-256:
`03c84cb565d4bdd10b7506a4d04a918ae590737f80a3e7e4fd37782b9c40b910`.
The tracked `IOS_MarkStep_C.dat` is now repaired to match live DEV SHA-256:
`cd5d4dfe175ae12fb71a4ad6c52f17d447335944d6efdfb269652534dbf3815f`.

The real DVT tutorial mission `12280` defines **only steps 1,2,3,4,5,11,12,13,14,15** in `IOS_MarkStep_C.dat`. No steps 6–10 are defined, so they cannot provide quest navigation instructions.
- First starter route: scene 10801, then 10851. Scene **10851 Eve4 condition7** handles **mission 12280 step5** with `resultType0/resultClass2/pStyle3` (complete/remove mission, no additional ADD_STEP).
- Independent alternate DVT entry: **scene 10821 Eve1 condition1** checks **mission 12280 absent (step0)** and grants `resultClass2/pStyle1/value11` — starts stage **11**, followed by DVT steps 12–15, including scene10853.
- No authentic Eve path yet verified that can legitimately create steps 6–10. A stale duplicate ADD at stage5 would persist stage6, leaving a client quest with no MarkStep and no known completion branch.

## DEV MySQL read-only proof

At this checkpoint DEV has **423 character_missions** rows and **1,158 persisted completed-Eve counts**. Exactly **two distinct character profiles** persist mission12280 at **step6** (no names or character IDs displayed):

| Anonymized profile | Current scene | Other active markers | Completed beginner Eve records |
|---|---:|---|---|
| A | 12003 | 11804/1, 12394/1, 12396/1, 12398/1 | 10801 Eve1=1, Eve2=1, Eve4=2; 10851 Eve1=1, Eve2=2, Eve4=1 |
| B | 10851 | 12398/1 | 10801 Eve1=1, Eve4=2; 10851 Eve2=3 |

They have different histories; **do not bulk-set both to step5 or step11**, remove the mission, set completion bits, reset Eve counters, teleport or replay rewards without a source-verified reconstruction of each character's state. Profile A already has starter Eve4 completion while B does not. These differences may indicate an old client/server version or repeated event dispatch; source audit alone does not distinguish those causes. No claim remains pending, but completed EveCount alone is not a financial receipt.

Other unexpected stored step records: **10120/3** and **10164/3**, one each. They also have valid terminal Eve chains but their MarkStep table ends at step2; evaluate separately. Other 367 mission records without a MarkStep definition reference real Mark_C marks and cannot automatically be classified corrupt.

## Fix deployed to GitHub branch, **not a migration**

`DvtNoviceStepGapPolicy` is a narrow source-backed validation for mission12280: **ADD_STEP landing in 6..10 is invalid**. The independent alternate DVT branch starting 0→11 and steps 11→15 remain allowed. Other missions are unaffected.

`EveMissionPreflightPolicy` rejects an Eve result chain before ANY item, gold or quest changes if it tries to create those missing stages. `QuestSystem.addMissionStep` rechecks inside its per-character mutation mutex, protecting against a stale repeated event whose preflight may have seen a prior step. When refused, existing `EveResultExecutor.handleMissionResult` already avoids sending an S024 add packet and signals failure. Neither gate changes/creates a step, a flag, a claim or a reward. This prevents **new** orphaned steps; it does **not** repair the two existing profiles or fix an incomplete old tutorial UI.

`DvtNoviceStepGapRegressionTest` verifies actual active Eve/MarkStep source, both valid routes, disallowed stage6–10, full-chain preflight ordering, and repository write never invoked on a repeated invalid step.

## Next action

For the two already-stuck test profiles, inspect authoritative event logs/mission receipts and client script history to distinguish a duplicate replay from genuine past progression. Propose individually verified replay-safe recovery with backups and exact SQL compare-and-set only when a valid target stage is demonstrated; do not infer it merely from the current map.

Continue validating 10120/3 and 10164/3, the 89 class13 reward effects, 35 class11 resource effects, class14 conditions, unlocated waypoints and client auto-navigation. No child/marriage/household work until mission system is cleared.
