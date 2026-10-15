# TS DVT 1.15 — source parity for mine (170)

## Canonical original references (user-supplied)

- PC DVT native IL2CPP dump: D:/Games/Ts-Offline/TSMobileOnLine/_native_dump_dvt115/dump.cs
- Decrypted original DVT client Lua: D:/Games/Ts-Offline/TSMobileOnLine/_native_dump_dvt115/_lua_client_decrypted/
- Kotlin in this repo: gamelogic/.../MiningSystem.kt, app/.../MiningHandler.kt

DO NOT replace these sources with TS Classic rules or invented DVT server yields.

## Verified original client invariants

| Client source | Invariant | Kotlin regression |
| --- | --- | --- |
| UI/UIMine.lua line 6 | Maximum shared reserve 600,000,000 | MAX_MINE |
| UI/UIMine.lua line 23 | Maximum worker hunger/fuel 65,535 | FULL_MAX |
| UI/UIMine.lua lines 30, 395–398 | 360 seconds per hunger point, using floor(elapsed / 360) | TICK_MS = 360000 |
| UI/UIMine.lua lines 404–438 | Process only one worker at a time, indices 1 to 4; stop on dayLimit or empty fuel | MiningSystem.getView / settlement |
| UI/UIMine.lua lines 369–384 | Fuel item IDs 55431–55434; worker skin IDs 22120–22123 | FUEL_ITEM_IDS, MACHINE_SKIN_IDS |
| Common/protocal.lua S170-001 | Mine type + Int64 reserve + OADate + 4 pairs of UInt16 fuel/skin in reverse order 4 to 1 + dayLimit byte | MiningHandler.sendInfo |
| Common/protocal.lua S170-002 | Mine type, Int64 reserve, players count, isMining flag | MiningHandler.sendEnterResult |
| UI/UIMain.lua UpdateMining | Only mine map 49996 shows HUD; percentage rounded with floor | MINE_SCENE_ID = 49996 |
| Common/protocal.lua S035-050 | Currency kind UInt8 and value UInt32, client Role.SetPlayerCurrency | MallResponseSender.sendPlayerCurrency |
| Common/define.lua | ECurrency.TS = 8 | Staged TS currency support in PR #64 |

The ONLY matching 'Mining' symbol in the 18MB dump.cs text search is eLifeSkillType.Mining = 1 at around line 187945. That is a *client life skill enum*, not original DVT backend code for robot payouts.

## What cannot be inferred

- A 360-second hunger interval is NOT proof that TS coin awards occur every 360 seconds.
- The original client Lua does not implement robot TS award amounts or server-side daily reward limits.
- The source-backed currency update S035-050 (kind 8) reports the balance, but not the formula that produced it.
- A client HUD showing 100% cannot prove ore was generated; it is rounded and may remain at 100% for many small changes.
- Do not invent miner rate/TS award/ore debit, including for DEV. This is the user's explicit preference.
- Earlier two client fixes were unsuccessful and rolled back. Follow Issue #61: backup before edits; rollback on failure without stacking speculative patches.

## Proof at this checkpoint

- Added Dvt115MiningSourceParityTest.kt covering constants, exact 360-second boundary, ordered four-worker consumption, dayLimit, exhaustion and reverse serialized S170-001 worker order.
- All tests use mocked/in-memory data, not any player's balance or DEV database.
- Separate atomic TS award infrastructure from PRs #57 and #64 remains DRAFT and cannot be wired to the fuel tick until original *backend* DVT rates and reset conditions are sourced.

## C170-011 action / S170-011 acknowledgement (2026-10-15 audit)

- The exact original Lua `Common/protocal.lua` defines the C170-011 one-byte mine-type request in a protocol comment, and parses S170-011 `result=1` as success and `result=255` as a rejection.
- Exhaustive Lua sender search in three client trees (`_native_dump_dvt115/_lua_client_decrypted`, `luaDVT`, `TSMlua/lua_decrypted`) found **no sender for C170-011**. The mining-related sender in original `UITeleport.lua` instead sends C170-002; `UIMine.lua` sends C170-001 and C170-010.
- Previously Kotlin `MiningHandler.setMiningState` returned S170-011 success on `mineType=1`, but performed **no persistent transition**. A false success is not source parity.
- The server now safely returns S170-011 `255` for a correctly-sized unsupported request and records a warning. Malformed payload remains ignored. It does not invent a DVT mining start/stop state machine; the original server rules are still unknown.
- Dedicated regression `Dvt115MiningUnsupportedStateWireTest` verifies one-byte requests, invalid type, malformed input and no ore/fuel/teleport effects. No TS currency or ore reserve change is made.

## Mining day-limit and worker handover — persisted SQL audit (2026-10-15)

**Original client behavior:** `UIMine.lua` `UpdateTimer` initializes `count=0` when `dayLimit=true`, preserves the received worker fullness, and sets `isMining=false` even if workers have food. With `dayLimit=false`, elapsed 360-second fuel ticks consume worker 1, then 2, then 3, then 4. S170-001 exposes the flag as the final UInt8.

**Kotlin current state:** `MiningRecord.dayLimit` defaults false and is saved/loaded through `character_mining.day_limit`; `MiningView.isMining` and `MiningSystem.settle` honor a true flag. But the ONLY references inside app/gamelogic/domain/persistence were model definition, SQL serialization, protocol serialization, and checks. **No producer automatically sets dayLimit true and no verified source-backed daily reset exists.** Thus support for an externally persisted flag is NOT actual enforcement of daily mining limits.

**DEV read-only query:** aggregated `character_mining` returned 1 record, 0 `day_limit=1`, 1 record with fuel. Executed as SELECT; no accounts/identifiers exported or DB mutated. JVM PID 10012 on port 6613 remained unchanged.

**H2 regression:** Expanded `DvtMiningFuelAtomicSqlTest` (SQL-backed, not mocked-only) with (1) dayLimit=true after three simulated days: unchanged worker fuel, stopped mine state, persistent flag; (2) worker handover 1→2→3 across three ticks remains persisted after reload. H2 fixture now includes `MiningOreReserves`, which is required by real `MiningSystem.getView`. Combined 5 classes = **26/26 PASS**. The first experimental run FAILED solely because the fixture omitted that existing table; those two test edits were immediately rolled back and the original file hash `9cf2f850af4cab017637b6f057f5625e27b23f65` re-verified before a new attempt, under mandatory rollback rule #61. Fixed H2 setup then 26/26 PASS. Final verified test-file Git blob SHA: `44a9bbff8901094b0af5a2386c01e3a7d9a40e71`.

**Do not invent** either daily cap threshold, award formula, day-boundary timezone, or reset behavior merely from the existence of a client `dayLimit` boolean. These remain original DVT backend facts still not established by IL2CPP client/Lua. Meanwhile `MiningSystem.settle` spends fullness but does not pay TS; do not ask users to spend fuel until settlement can be proven.
