# DVT in-session icon unlock refresh parity — 2026-10-12

Follow-up to `docs/dvt-beginner-ui-marker-eve-source-regression-20261012.md`. **Static source audit and regression only; no runtime, quest, or client data patch.** Manual character E2E remains scheduled for one combined pass.

## Confirmed original DVT Lua behavior

**Level threshold on the same session:** Kotlin `BattleManager` emits `S008-001` absolute LV after character EXP reaches an original level threshold; its regular and turn3 branches both send `EAttribute.LV`. The Kotlin packet layout is `08 01 00` + attrKind(1) + sign(1) + value(4 LE) + arg(4 LE). Original DVT `lua/Common/protocal.lua` `protocolTable[8][1]` decodes exactly this and dispatches to `Role.SetAttribute(EHuman.Player,...)`; `lua/Controller/RoleController.lua`'s `EAttribute.Lv` branch assigns the new level first, then, only on genuine change to the current player, executes `Function.UpdateVisible()` and `Dungeon.InitAvailableDungeonDatas()`. No relog is required for this path.

**Mission marker on the same session:** Kotlin `EveResultExecutor` sets a Mark bit and sends `S024-005` after a legitimate DVT marker result. `NpcEventResponseSender` encodes UInt32 count and UInt16 bitIndex + Boolean. Original `protocolTable[24][5]` reads these and calls `MarkManager.SetMissionFlag`; it mutates the flag before invoking `Function.UpdateVisible()`. The previous patch `7d80d1d` repairs only persisted legacy marker bits before the login `S024-007` snapshot. Neither mechanism invents mission completion.

**Gate enforcement:** `Function.CheckShow` evaluates original `showLv/showFlag` and `Function.CheckUse` separately checks original `requireLv/requireFlag` and feature-specific restrictions. The original `IOS_UiOpen_C.dat` contains 84 rows; SHA256 `af759749cf6d33081df7de99f715cf682e324bb48fb0ff4c57edb932a78c8b4e`. Examples: Dungeon Lv15, Organization Lv10, Compound Lv15, House use Lv60, Teleport flag11804. A menu that is currently collapsed by `UIMainFunction.showAll` may require expanding it even after an icon becomes eligible — that is not necessarily an unlock failure.

## Regression coverage added

- `tools/test_dvt_live_icon_refresh_source_parity_20261012.py`: five tests pin original source table and thresholds, decoding `S008-001` and refreshing on level changes, `S024-005` mutation/refresh, separate show/use gate checks, and ordered login snapshot before character appearance.
- `tests/src/test/kotlin/tw/tsm/tests/DvtLiveIconUnlockWireRegressionTest.kt`: three wire-level tests of standard LV milestones 10/15/60 via absolute `S008-001`, original markers 936/1375/1376/1434 through `S024-005`, and Boolean zero/one flag removal/regrant encoding. Uses a packet capture only — doesn't grant mission flags to real accounts.
- Explicit DVT data audit CI step added for live source parity.

## Outcome

Source-side level and mission refresh paths are present and schema-consistent; no source-backed runtime bug has been identified in this pass. Therefore **no Kotlin game code, user record, client Lua, UiOpen data, Eve rewards or MarkStep was changed**, and no DEV Java restart or deployment is needed. These automated checks make future inadvertent regression visible; live in-game inspection is still necessary to prove each actual icon appears/works at the right milestone.

## Deferred consolidated tests

On a new DVT character, reach level9→10→15→60 through legitimate EXP (or approved test account without editing quests), monitor organization/dungeon/compound/house show/use thresholds; finish original tutorial flags and watch the icon appear during the same scene, before relog. Confirm no early teleport before flag11804; relog to verify flags persist via S024-007; expand the UIMain function menu when necessary, and watch for runtime activity/server-switch restrictions.
