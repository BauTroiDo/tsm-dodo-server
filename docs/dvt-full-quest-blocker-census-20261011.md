# DVT active quest-system blocker census — 2026-10-11

## Purpose

This is the initial **source-wide automated audit**, not a declaration that every player quest now works. The user's priority is to complete mission diagnosis and fixes **before** Gia Viên, Định hôn or the child-adoption UI. Only use the authoritative active `gamedata/Eve.emg` and `File.dat/Data-ios/IOS_MarkStep_C.dat` already present in PR #41.

Repeat using:

```bash
python tools/audit_dvt_quest_blockers_20261011.py --out /tmp/dvt_quest_blockers.json
python -m unittest discover -s tools -p test_dvt_quest_blockers_20261011.py -v
python tools/audit_dvt_quest_navigation.py --markstep File.dat/Data-ios/IOS_MarkStep_C.dat --eve gamedata/Eve.emg
```

The full JSON report contains evidence by scene, event, condition, result index, MarkStep ID and target. The analyzer performs no database writes and never modifies Eve or MarkStep assets.

## Source coverage and verified counts

- Active Eve SHA-256: `03c84cb565d4bdd10b7506a4d04a918ae590737f80a3e7e4fd37782b9c40b910`, **3,823 indexed scenes** (26 are genuinely blank; server loads 3,797 populated scenes).
- MarkStep: **4,505 step rows**, corresponding to **984 distinct missions** with steps.
- Unimplemented server-side `resultClass=11`: **35 effects** (city/legion resource branch, concentrated in scenes 11572, 26522, 26525 and 57523). City resource persistence/authoritative mutations not confirmed; no invented city currency changes.
- Unimplemented server-side `resultClass=13`: **89 effects**, activity reward packages without verified mapping from package IDs to actual items/rewards. Do not guess rewards or mark claimed without a table.
- `conditionClass=13`: **99 conditions** for legion/city identity, missing reliable owner/privilege state in some branches.
- `conditionClass=14`: **279 conditions in the authoritative current Eve** (older reports counted 291 using another revision). Their event activation semantics are not yet proved; don't silently return true or fabricate active-time.
- `conditionClass=17`: **16 conditions** in maps 12721/12722 for second-generation child progression; hold fail-closed until there is an authentic client UI and server-created child.
- Class15 effects: **zero** in the currently deployed Eve. Earlier reference scene10821/class15 was from an obsolete archive.

**85 distinct scenes** have one or more of the above gap types. The script links these event trees to **82 MarkStep mission IDs potentially affected** by unsupported conditions/effects. This is a broad, conservative *triage adjacency*, not a verdict that all 82 quests are broken: one event can contain many mutually exclusive branches and conditions, and some quests have no MarkStep definition. The 82 IDs must be checked against actual event branches and player state before patches.

## Navigation target findings

**269** MarkStep rows have no located `endTarget` (includes steps with no end waypoint by design), **229** of them without any objective conditions.

Of the remaining 40 steps with a blank end target, **32** have at least one valid located condition, and **8** have objective conditions but *no located objective or end target at all*:

| Mission/step | Actual MarkStep description | Expected operator handling |
|---|---|---|
| 10852 / 1 | Catch jade rabbit `npcId 38022` in Quảng Hàn cave (via Ngô Cương) | Multiple real NPC placements across maps. Do **not** select a random map/slot as an autofollow destination |
| 12158 / 1–5 | Find successive knives and tools in the Tần Lăng passage | All original target coordinates are zero. Item spawn locations not proved |
| 12162 / 1 | Obtain 30 clay pots (item32043) and hand over to the named NPC | Collect/source and turn-in route not encoded |
| 12509 / 8 | Purchase food from multiple inns (items26106/26013/26016) | Multi-place acquisition objective; cannot replace with a single guessed waypoint |

Overall **96 individual objective conditions** lack a located waypoint: **89 item collection** objectives and **7 NPC capture** objectives (not 96 missing maps). The 7 capture objectives reference NPC IDs `38022`, `13038` or `38265`, which appear at multiple legitimate Eve placements. Avoid choosing an arbitrary position. Missing coordinates can legitimately mean an activity, shop or roaming objective.

Earlier navigation audit already repaired 10 wrong door references (in 11908/12438/12682) and 7 MarkStep target parity mismatches, and verified **zero missing static NPC IDs, zero missing door IDs, zero missing referenced target scenes**. One nonstatic hunt encounter in 11190 must remain classified separately. These facts do **not** prove client interactive autowalk in every route.

## Repository root cause fixed: ten quest doors reverted on redeploy

The first full navigation scan of the tracked repository assets, **before this
fix**, returned 10 missing door references among 5,716 located targets.
The already-running DEV server had the correct hotfixed
\`IOS_MarkStep_C.dat\` (SHA-256
\`cd5d4dfe175ae12fb71a4ad6c52f17d447335944d6efdfb269652534dbf3815f\`),
but GitHub still tracked the old blob (SHA-256
\`fa1729647758eda7c3565e148c9498fcc4775cc8d0d87a2be842bcf1fbe1f9a3\`).

This is a genuine **deployment regression risk**: a later full data deploy
from GitHub would silently undo the previous DEV fixes.

Reapplied \`tools/repair_dvt_mission_door_refs_20261011.py\` to the tracked
\`File.dat/Data-ios/IOS_MarkStep_C.dat\`. The tool checked exact original
scene, coordinates, event kind and incorrect ID, changing **only ten
eventId bytes**, at these missions/steps:

- 11908/1–5: door IDs \`8→3, 9→4, 10→5, 6→2, 7→3\`.
- 12438/2–5: \`5→3, 5→3, 4→2, 4→2\` (last is condition 1).
- 12682/1: \`5→2\`.

The resulting blob matches the **already deployed DEV server SHA exactly**.
Re-running the repair found **zero remaining changes**, and the navigation
audit returned **zero missing scenes, zero missing fixed NPCs, zero missing
doors**, while preserving one legitimate nonstatic hunt objective.
A CI test now enforces the fixed SHA and all actual Eve door/NPC links so a
future repo checkout cannot silently reintroduce this regression. Client
files were previously hotfixed and are not modified by this commit.

## Suggested fix order, with release gates

1. **Mission return / next-step correctness:** verify active character progress, NPC/door event choice, Eve mission resultClass2 semantics, claims and client S024 synchronization. Use actual character trace or test fixtures, not merely static mission-ID counts.
2. **Unlocated objectives:** recover valid original item acquisition / spawn locations, multi-stop routes and capture area; patch only exact verified MarkStep rows, with old and new byte-for-byte backups in both client copies and server. For the eight entirely unlocated steps, provide actionable quest guide messaging when genuinely no route exists; never teleport/claim the objective automatically.
3. **Blocked effects:** decode `resultClass13` reward package table before granting rewards, and `resultClass11` city resource transactions only after server entity and persistence sources are confirmed. Do not bypass `EveResultExecutor.canExecuteResults` guard or discard effects.
4. **Condition gates:** decode `conditionClass14` using real activity state; decode `conditionClass13` only with authoritative legion/city state. Keep the child-only class17 gated until separately implemented, but do not spend more work on menus now.
5. **E2E:** use test characters to repeatedly accept, complete, turn in and navigate priority mainline/side quests, including quest restart and client relog. Source and unit tests alone cannot establish an entire game-wide PASS.

Never mark a mission as fixed solely because its Eve scene exists, and never authorize invented rewards, unrestricted map unlocks or mission flag jumps.
