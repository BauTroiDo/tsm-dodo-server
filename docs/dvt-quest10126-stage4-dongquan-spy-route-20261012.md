# DVT10126/4 — Restore first source-backed Dong Quan infiltration NPC (2026-10-12)

## Evidence

`MarkManager.Navigation` starts with the mission stage's END, then tests the only `condition.kind=1` (follow NPC10099). Before the companion joins, it routes to the existing `(11811,1902,1615,1,8)`; after the companion joins, it switches to the stage END. Stage4 END was blank `(0,0,0,0,0)`, preventing the quest-click auto-route.

Stage4 text (Vietnamese): "**Đến đường lớn Đông Quận, tìm đến giúp đỡ Gian tế ở phía ngoài quan phủ Đông Quận, đột nhập quan phủ đối thoại với Vương Quăng**".

Active DVT `gamedata/Eve.emg`, scene **11002** (road approaching the palace):
- NPC **17018**, placement **5**, position **(570,260)**, non-hidden, bound to **Eve14**. Although the scene contains two more NPC placements of type17018, **only placement5/Eve14** has exact `conditionClass2/param10126/pStyle1/ops5/value4` plus a not-stage4 branch; this identifies the stage4-specific NPC precisely.
- `scene_infos[6]` is the existing portal from **11002 to 11301**, not an invented door or scene.
- Inside **11301**, NPC **14216**, placement8, position (470,380), `Eve12/condition1` requires quest10126 at step4 and follow-NPC10099, and emits `resultClass2/pStyle1/val1` to advance quest10126 to step5.
- Thus the **first relevant waypoint** of this multi-scene mission is NPC17018/placement5 at `(11002,570,260,1,5)`. The subsequent palace scene/quest advancement is original Eve logic; this data-only patch does **not** pretend to auto-complete the entire infiltration.

## Change contract

New `tools/patch_dvt_10126_palace_spy_route_20261012.py` source-verifies BOTH the outdoor mission NPC and the interior quest stage4→5 transition, the original follow-NPC condition, exact expected MarkStep SHA editions, exact END offsets and a byte-only change; atomic backup/write, repeat idempotent.

Only **8 END bytes** for mission10126/step4 at offset **16407–16414** were replaced with `(11002,570,260,1,5)`.

- Repo + DEV file (892087 bytes) previous `6272d35367a8fda2c389380ce4f3aec0c9d1d5c65ddffc542e3cb240da1ac2c3` → **`8abb49e2636ff925af23a259e4b1924d9f7fe557f38c383f6912c84d14af3110`**.
- Client staging two editions (928425 bytes) previous `4d51a729a20694855adfcca266b41d56c5e403d0062be702b8d26f9fb63bde44` → **`60be6337107c15c7f7997542ff0f800b8f7c7fdab7342afa99983b7bacbdbce0`**.
- Prior verified quest10852, 12162, 12509 and door/NPC locations untouched. Audit static target count **5728→5729**; blank END **266→265**.
- Regression `tools/test_dvt_10126_dongquan_spy_navigation_20261012.py` verifies NPC mission binding, real scene portal, interior step advance, original Lua condition, SHA and read-only repeat.

**Limitations:** This is a *waypoint fix*, not confirmation that all steps of 10126 are working. Need end-to-end click→talk→enter palace→talk with NPC14216 test using a character with companion10099; no fake dialogue or quest outcome coded. Unverified class13 stays unsupported, no reward or player data changes.
