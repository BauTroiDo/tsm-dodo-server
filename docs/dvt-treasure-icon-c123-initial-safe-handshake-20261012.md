# TS DVT Treasure icon C123 handshake — 2026-10-12

Scope: restore *only the initial request/response UI handshake* for an originally unlocked Treasure icon, with deliberately disabled upgrades until persistent treasure records and validated level/star/item rules are implemented. Full Treasure gameplay is **not** claimed as fixed.

## Source proof

- Original `File.dat/Data-ios/IOS_UiOpen_C.dat`, ID82: `EFunction.Treasure=74`, group3, showFlag & requireFlag **11804**. The original table is unchanged SHA256 `af759749cf6d33081df7de99f715cf682e324bb48fb0ff4c57edb932a78c8b4e`.
- `IOS_Mark_C.dat` mission11804 corresponds to original **bit936**. Kotlin `TreasureHandler` requires this persisted completion bit and `IN_GAME` state on **every** C123 RPC. New characters without the real event-derived unlock get no treasure response; no fake or automatic unlocking.
- Original `lua/Logic/Function.lua` opens `UITreasure`, sends **C123-001** (empty body) and sets `Treasure.sendOpenRequest=true`. Original `lua/Common/protocal.lua` `S123-001` reads a 32-bit signed row count followed by `(u16 treasureId, i32 quantity, u8 level, u8 star, u8 exp)` tuples; an empty count is valid, triggers `Treasure.Initialize()` from item data and opens the UI when sendOpenRequest is set.
- The DVT Lua source explicitly notes the Treasure protocol was *not yet implemented* in an earlier N version. Original resource tables `IOS_TreasureActivityCriteria_C.dat`, `IOS_TreasureGroupInfo_C.dat`, `IOS_TreasureGroupLV_C.dat`, `IOS_TreasureLv_C.dat`, `IOS_TreasureRoleCriteria_C.dat`, `IOS_TreasureStar_C.dat` exist. Those **static rules are not a server inventory/ownership/progression repository**; inventing positive levels, stars, quantities, materials or rewards would be incorrect.
- No Kotlin handler for opcode123 existed before this batch, so the initial request received no S123 reply. There was no client-side wait-state resolution.

## Safe partial correction

Register `TreasureHandler` in `ServerModule` using handler-local opcode `TREASURE_OPCODE=123`. This intentionally leaves the DEV `domain.constants.Opcodes.kt` file **untouched**: its current SHA differs from the Git checkout, and overwriting it would destroy unrelated DEV modifications.

- C123-001, no payload, after valid bit936: S123-001 `[7B 01 00 00 00 00 00]` (7-byte packet, count0). This completes original client handshake; initializes its **static** treasure catalog, not owned treasure quantities.
- C123-002, exact 8-byte body, after flag: original failure S123-002 kind255 `[7B 02 00 FF]`. No level advancement or inventory/currency reduction.
- C123-003, exact 8-byte body, after flag: original S123-003 kind255 with echoed u16 treasure ID and star/exp bytes0 (8 bytes). The Lua receiver may read those two trailing bytes even when failure is returned; include them so parsing remains aligned. No star progression or inventory/currency reduction.
- Malformed body, wrong session state or missing bit936: **no response/state transition**, matching fail-closed semantics.
- `QuestSystem` only reads committed marker flags. No GM bypass, no opening during tutorial, no mutation of quest flags.
- Existing original Lua, Eve, MarkStep, item definitions, original UI table, and DEV inventory/quest databases are unchanged.

## Tests

`tools/test_dvt_treasure_icon_protocol_20261012.py` locks original source UI gate, Lua C/S wire layout, Kotlin registration, inability to forge quantities or levels. Kotlin `DvtTreasureIconWireRegressionTest.kt` verifies the empty S123 handshake, wrong/absent marker, malformed requests, error message bodies, and outside-game sessions. Both CI workflows must PASS and DEV source must match expected SHA before guarded deployment.

## Outstanding full Treasure work

Real Treasure leveling/starring requires building a separate persistent per-character repository; rules must be decoded and reconciled from the six source `IOS_Treasure*_C.dat` tables plus item inventory/ingame currencies, including idempotent atomic deductions, max-level/star handling, packet acknowledgements, and stat recalculation. **Do not enable mutations or present the current handshake as full Treasure feature parity.**

The manual E2E round later should check unlocked char with flag11804 opens UI, static combinations/cards are readable, upgrades return an explicit unavailable message without consuming items, and a new char before flag11804 cannot access the feature. If the original client UI errors on an empty combination/card, additional client asset/data investigation is required; source and CI tests alone cannot replace gameplay testing.
