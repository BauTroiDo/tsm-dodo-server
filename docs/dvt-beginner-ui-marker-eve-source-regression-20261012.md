# Original DVT beginner UI icon marker grant audit — 2026-10-12

Checkpoint following committed DEV login marker reconciliation `7d80d1d` (deployment documented in `docs/dvt-login-marker-ui-flag-repair-20261012.md`). This batch is **regression coverage only**: no new game data, Kotlin runtime, client Lua, character flags, or NPC triggers changed.

## Full source Eve grant tracing

Exhaustively decoded original `gamedata/Eve.emg` (3823 scenes), filtered every original `resultType=0,resultClass=2,pStyle=1,value=1` with target Mark IDs 11804/12283/12285/12396, checked the one matching original NPC or Door event owner and the exact condition. There are **14 authentic grant branches**:

| Marker ID | Mark_C bit | Original Eve grant, scene + owner | Original branch gate | Count |
|---|---:|---|---|---:|
| 11804 | 936 | scene10851 Eve1 Door1; scene10853 Eve11 Door2 | source UI Surface selections class10(2,30), class10(3,30) | 2 |
| 12283 | 1375 | scene10851 Eve4 NPC1/type15049; scene10853 Eve3 NPC1/type15049 | mission12282 stage2 or stage12 | 2 |
| 12285 | 1376 | scene10851 Eve11 NPC9/type38257; scene10853 Eve8 NPC3/type38257 | four distinct choices class10(surface4 or1, choice30/31/32/33) | 8 |
| 12396 | 1434 | scene10851 Eve4 NPC1/type15049; scene10853 Eve2 NPC1/type15049 | mission12280 stage4 or stage14 | 2 |

Source does not grant these four markers for opening a menu or merely possessing a level. It grants them **after the specific original NPC or Door Eve branch** succeeds. The corresponding UI `showFlag/requireFlag` values remain original DVT. All four Mark entries have `bitId>0` and **no original MarkStep row**, explaining the need for one-shot marker treatment.

The 11804 Door original effect order is ADD_STEP/marker first, then source resultType2 scene warp. For scene10851 the referenced sceneInfo is ID3 to map12003; scene10853 uses sceneInfo ID1 to map12003. No synthetic teleport or unconditional map unlock is needed.

## Coverage added

- `tools/test_dvt_ui_marker_eve_progression_20261012.py`: parses Eve/Mark/UiOpen originals; requires all **14 and only 14** grant branches and matching original NPC/Door owners, gates, source marker bit IDs, original mission flags in UI, and the 11804 marker-before-warp ordering plus valid sceneInfo references.
- `tests/src/test/kotlin/tw/tsm/tests/DvtBeginnerFunctionUnlockEveRegressionTest.kt`: independently tests Kotlin `EveResultExecutor` with each original marker ADD_STEP result (persist quest stage and `bitId` and send incremental `S024-005`), and verifies a real MarkStep mission doesn't receive an early completion bit. Complements existing `DvtLoginMarkerUiUnlockRegressionTest` which already proves login S024-007 and legacy-marker recovery.
- Dedicated original-Eve Python CI step added to `.github/workflows/dvt-data-audit.yml`.

## No early unlocks

The client's original `File.dat/Data-ios/IOS_UiOpen_C.dat` has **84 untouched** records and SHA256 `af759749cf6d33081df7de99f715cf682e324bb48fb0ff4c57edb932a78c8b4e`. No direct reward, forced quest step, forged mission flag, modified minimum level or game server runtime logic was needed based on the static source chain.

## Deferred character E2E

After the owner tests a brand-new DVT character: (1) start tutorial before 12280/12282 progression; (2) check icons stay hidden/locked until original branches; (3) complete each original branch, check S024-005 makes the correct icon appear without relog; (4) change map/relog, verify flags persist through S024-007; (5) verify choosing different companion options still grants the same 12285 marker and only the correct pet; (6) ensure world map/dịch chuyển stays locked until original 11804 door branch.

Automated parity tests do not replace this future in-game UI test.
