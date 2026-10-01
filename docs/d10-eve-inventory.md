# D10 — Eve Quest Engine Inventory

Generated from the production `EveDataLoader` against `gamedata/Eve.emg`. Scratch analyzers are intentionally kept outside the repository.

## Trusted baseline

- Parsed scene objects: 3,797
- Condition rows: 77,001
- Unique condition shapes `(class,param,pStyle,ops,subItem)`: 7,686
- Result rows: 164,910
- Unique result shapes `(type,class,param,pStyle)`: 8,236
- These row totals match the earlier full Eve audit, so this inventory is the D10 baseline.

## D10-A gaps confirmed

### conditionClass 17

- 16 rows, concentrated in scenes 12721/12722.
- Existing evidence maps this family to child/foster/second-generation NPC state.
- Server has no authoritative child subsystem/state for these checks.
- Status: **backend dependency blocker**. Keep conservative false; do not fake.

### class7 param12

- 97 rows; only `pStyle=1/2`, `ops=0`, `value=0`.
- Scene 49211 talks explicitly describe Water War ship crafting and a full warship warehouse.
- Semantics: warship-storage capacity/full-state predicate.
- Server has no authoritative WaterWar/warship storage subsystem.
- Status: **backend dependency blocker**. Do not fake free capacity.

### class7 param11

- 10 rows; only `pStyle=1/2`, `ops=0`, `value=0`, in the Turn2Ball/Tiên Châu flow.
- Lua DVT exposes `EAttribute.Turn2Ball`, S23-77 `turn2BallCount` and activated ball indexes.
- Exact pStyle1/2 predicate mapping is not yet locked.
- Status: **evidence pending**. Do not implement yet.

## D10-B1 — class7 param9 semantic conflict

Production inventory has 41 rows, all `ops=0,value=0,subItem=0`:

- pStyle1: 7 rows
- pStyle2: 7 rows
- pStyle3: 15 rows
- pStyle4: 12 rows

Evidence:

- Scene 12093 uses pStyle1/2 around mission 10869 `Thoát Thai Chuyển Sinh`.
- Scene 12180 forms a progression ladder: pStyle3 with Lv>39, pStyle1 with Lv>79, pStyle2 with Lv>119 — matching Nhị Chuyển / Nhất Chuyển / chưa chuyển sinh tiers.
- Scenes 12064, 12711 and 59411 use pStyle4 to show Talk 52419: `sau khi Nhị Chuyển hãy đến tìm ta`, while pStyle3 enters the post-Nhị-Chuyển flow.
- Scene 49950 has separate battle tables selected by pStyle4 versus pStyle3 across the same level bands, consistent with pre/post Nhị-Chuyển state rather than element.
- `PlayerEventState.rebornCount` already comes from runtime player `turn`.

Corrected semantics:

- pStyle1: reached first reincarnation (`turn >= 1`)
- pStyle2: not first reincarnated (`turn < 1`)
- pStyle3: reached second reincarnation (`turn >= 2`)
- pStyle4: not second reincarnated (`turn < 2`)

Threshold semantics intentionally keep Turn=3 in the already-passed-Nhị-Chuyển branch.

Previous Kotlin incorrectly treated param9 pStyle1..4 as player element. D10-B1 replaces that mapping and updates regression coverage.

## Next D10 audit

Continue shape-level audit; do not return to quest-by-quest hardcoding. Highest-value pending items are class7 param11, class7 param12 dependency planning, class17 dependency planning, then result-policy blockers 11/13/15 only where new authoritative evidence exists.

## D10-B2 — class7 param11 Turn2Ball completion

- 10 production rows, only pStyle1/2 with ops=0,value=0,subItem=0.
- pStyle1 = all 12 activated Turn2Ball indexes present; pStyle2 = not complete.
- Uses authoritative active Turn2Ball persistence/cache, not available-ball count.
- Targeted/full regression: PASS.

## D10-B3 — resultClass15 skip-tutorial operation

- Production Eve inventory contains exactly one resultClass15 row:
  scene10821/eve3, type0, param1, pStyle1, value0.
- Mission12280 is the novice tutorial line.
- Surface choice30 = "Bỏ qua Hướng Dẫn Tân Thủ"; choice31 = "Tiếp tục Hướng Dẫn Tân Thủ".
- choice30 executes resultClass15 then Door warp; choice31 decrements mission12280 by 9.
- Lua/client exposes a separate C:005-008 tutorial-complete contract, but no authoritative evidence equates resultClass15 param1/pStyle1 to guideId1 or any specific bitflag mutation.
- No resultClass15 handler/enum was found in DVT Lua/decompile or PC reference.
- Status: **EVIDENCE BLOCKED**. Do not infer tutorial flags/rewards/state mutation.

## D10-B4 — class7 param16 mount-state predicate

Production inventory:
- 77 rows total, all pStyle1/ops0/value0.
- subItem1: 38 rows.
- subItem2: 39 rows.

Evidence:
- Every subItem1 blocking branch leads to Talk37776: "Xin lỗi, mời bạn xuống ngựa được không?"
- Representative scene12901/eve2:
  - subItem1 is an independent mounted blocker leading to Talk37776.
  - subItem2 is inside the main AND-chain that allows the event Surface flow to continue.
- Therefore the pair is complementary:
  - subItem1 = currently mounted.
  - subItem2 = currently not mounted.

Fix:
- class7/param16 now validates both shapes.
- subItem1 matches isMounted=true.
- subItem2 matches isMounted=false.
- malformed shapes remain conservative false.
- EveConditionEvaluatorTest covers both sides.
- Targeted EveConditionEvaluatorTest: PASS.

## D10-B5 — class7/param0 pStyle2 Element + pStyle3 TransformNpcId — PASS
- Production Eve proves class7/param0 is overloaded by pStyle, not always player level.
- pStyle1 remains player level with compareLevel().
- pStyle2 uses element identity. Lua DVT EElement: Earth=1, Water=2, Fire=3, Wind=4. Representative scene11525/eve5 has four mutually exclusive pStyle2/ops5/value1..4 branches whose rewards are the four elemental items 23086..23089; therefore ops5 is exact identity, not a numeric threshold.
- pStyle3 uses current transform NPC/model ID. DVT S:005-005 writes Role.players[roleId].npcId. Representative Eve values 38028/38029/38030/38073/38079/11050 are real NPC IDs and match Transform Doll malePicId targets; scene12093 checks transform 11050 (Hứa Chử) with ops5/ops6.
- PlayerEventState/EveStateBuilder already carry transformNpcId from PlayerState. ItemHandler Transform Doll path already mutates runtimeState.transformNpcId and broadcasts S:005-005.
- Added compareIdentity(): ops0/5 ==, ops6 !=; unknown ops fail closed. pStyle2/3 use this comparator instead of generic compareStep/Eve numeric threshold behavior.
- Permanent EveConditionEvaluatorTest covers pStyle2 element exclusivity and pStyle3 transform equality/inequality, including protection against accidentally using player level.
- Removed temporary D10B5ScratchTest.kt from repo.
- Full suite initially exposed two stale strict mocks in DvtItemWireTest because ItemHandler now checks ItemDef.kind before NPC-picture handling. Fixture now explicitly sets kind=0 (non-Transform); production logic unchanged.
- Targeted EveConditionEvaluatorTest PASS.
- Targeted DvtItemWireTest PASS.
- Full gradlew test build --rerun-tasks PASS.
- git diff --check PASS except existing CRLF normalization warnings.
