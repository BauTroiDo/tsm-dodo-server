# DVT mined icon click fixed: C170-001 zero-type initial query

2026-10-12 — PR #41 follow-up to `f42559b`, focused on already-unlocked icons whose click path does not activate the expected UI. Exact original DVT source was consulted before any code change.

## Source-backed root cause

**UiOpen_C icon ID77**, `EFunction.Mine=71`, group3, retains original `showFlag=11804`: only characters with authentic DVT tutorial progression can see it. There was **no unlock issue** with this entry.

Original `lua/Logic/Function.lua` `Function.Use(EFunction.Mine)` explicitly writes `sendBuffer:WriteByte(0)` and sends `C170-001`, the initial **query for mine info**. The original UI `lua/UI/UIMine.lua` shows **one mine type**, and when its dropdown changes, `UIMine.OnDropChange_MineChange(step)` adds one and sends `C170-001(type=1)`. Original `lua/Common/protocal.lua` `S170-001` only calls `UI.Open(UIMine)` if response `res=1`.

Kotlin `MiningHandler.queryInfo` previously rejected every request other than `mineType=1`. Therefore clicking the icon sent type0 and received `S170-001(res=255)`, so the original client **never opened UIMine**, even if icon was legitimately unlocked.

## Exact correction

In `app/src/main/kotlin/tw/tsm/app/handlers/MiningHandler.kt`, accept *only* type0 as an alias for type1 in `queryInfo` (`subKind=1`), then call the already implemented original `sendInfo`. Dropdown type1 is unchanged. All other types still get the existing disabled response. `enterMine` (`subKind=2`) **continues strictly requiring type1**, and retains battle/map/party restrictions. `refill` (`subKind=10`) and its actual item checks, resource limits and day limits are unchanged; no auto entry or reward was added. Server response remains the original `S170-001` with success1 and mine type1.

No modification to `UiOpen_C.dat`, Eve, Kotlin mission flags, client Lua or game assets. The earlier request to preserve true level/mission locks is obeyed exactly.

## Automated regression coverage

- `tools/test_dvt_mine_icon_click_source_20261012.py`: four original source tests pin icon gate, Lua type0 icon request versus dropdown type1, Kotlin's *only query* alias and strict mine entry, and original `S170-001` UI-open response gate.
- `tests/src/test/kotlin/tw/tsm/tests/DvtMineIconClickWireRegressionTest.kt`: five tests verify type0 initial click gets `S170-001(res=1, mineType=1)` with exact 38-byte packet, dropdown type1 unchanged, type2 rejected, type0 cannot enter mine, and malformed request has no state transition. Uses only in-memory mocks and a packet capture.
- Explicit DVT audit CI step; full DVT test discovery and Kotlin Server CI must pass before DEV deployment.

## Follow-up audit

All 55 menu icons in original `UiOpen_C.dat` groups1,2,3 have an explicit `EFunction` use branch in source Lua; this does **not** guarantee each underlying RPC is implemented on the Kotlin server. One additional item needing separate implementation audit is `EFunction.Treasure` (icon ID82): original Lua sends `C123-001` after opening `UITreasure`, but initial Kotlin handler search found no explicit mainKind123 route. Do not pretend this mining-only fix also repairs treasure or unlock every menu function.

## Deferred E2E

With a character holding the authentic flag11804, open Mine icon ID77; the page should open without relog, show mine1 information and four machines. The dropdown request type1 must still succeed. Attempt to enter the mine while in a party/battle/forbidden map: game should still refuse. Confirm refill uses valid items and quantities, no duplicate charge, relog preserves mining state. Manual game tests remain deferred until the owner's final combined round.
