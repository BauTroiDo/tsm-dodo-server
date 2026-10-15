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

## Deployment checkpoint (DEV, 2026-10-12 20:52 ICT)

- Initial implementation commit `b93e9386f8b1b701aa8498f0a0ca74ac0b9c9257`; safer DEV-local opcode revision `952612504930f391ff5f7be5552b8eca415b0aa8`.
- GitHub Server CI run `38258332365` **PASS** and DVT data audit run `38258331954` **PASS** on `9526125`. Python DVT suite **184/184 PASS**, Kotlin wire tests executed through CI.
- DEV `tsm-prod` had **different** `domain/.../Opcodes.kt` SHA from the Git checkout (`6c18a0d0...` instead of `4d13fddb...`). Initial preflight **refused** to overwrite it. This was addressed *in source on GitHub* by using a local constant `TREASURE_OPCODE=123` in the new handler and removing the change from tracked domain Opcodes. No server git checkout/reset or unrelated file overwrite.
- PRE-DEPLOY original `ServerModule.kt` hash: `92abce86d5fb3894b45578582f6fc98a57e888db80f4c41c7c7863c073cc9efe`; new hash: `250dcbbc033c65a8d09b0712c5fcc1010da0f2d47a47e58745562b195bab6f84`. New `TreasureHandler.kt` SHA: `c2be19a7b1ba5e6d29cf62e31372f9e4329bd13843b42ea6fe8b93de85c563bf`.
- Backup: `C:\Server-Game\tsm_dodo\backups\dvt_treasure_9526125_20261012_205117\ServerModule.kt`. Previously absent Treasure handler file is **new**, not an overwritten file.
- DEV Gradle `:app:classes` **BUILD SUCCESSFUL in 31s** before restart. Old Java PID `6148` stopped, new **PID11040** listening on **TCP6613**; startup log `C:\Server-Game\tsm_dodo\logs\runtime-production-dvt-treasure-9526125.log` reports game ready at 20:51:58.
- Independent post-deploy SHA and compiled class verified. `UiOpen_C.dat` retains original SHA `af759749cf6d33081df7de99f715cf682e324bb48fb0ff4c57edb932a78c8b4e`, and DEV domain `Opcodes.kt` retains its pre-existing SHA `6c18a0d0b3480a66f1aef21b2e7d88a4f9a656e91126443967ba8fda2826d5e0`.
- No inventory or treasure state modification, no server character account E2E. Future full Treasure implementation requires authoritative static-data decoding, persistent per-character holdings and safe resource-deduction transaction logic; *never infer reward quantities/stars from a client icon click alone*.
