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

## Concurrent C170-001 snapshot vs C170-010 fuel commit (2026-10-15)

- Client Lua sends `C170-001` on opening/querying mine information and `C170-010` on refilling a specific worker. Either can arrive close to another UI action or reload.
- Kotlin `MiningSystem.getView` uses `CharacterPersistenceGuard.withLock(characterId)` across reading, settling and persisting the mine state.
- Kotlin `MiningSystem.refill` enters `InventorySystem.consumeMiningFuelAtomic`, which acquires the **same per-character guard** before the database fuel commit and RAM BAG publication. This serializes snapshot writes against refills, rather than relying on unaudited packet arrival order.
- **SQL-backed regression:** `DvtMiningFuelAtomicSqlTest.concurrentQueriesAndRefillsDoNotLoseTheCommittedWorkerFuel` launches 96 simultaneous queries/refill attempts on a single character, expecting exactly 3 successful refills from a 3-item bag, 0 remaining items, and 30 stored fullness in only the selected worker. It also reloads persistent DB state and compares with another read.
- Gradle target `DvtMiningFuelAtomicSqlTest` (10), `Dvt115MiningSourceParityTest` (6), `Dvt115MiningUnsupportedStateWireTest` (3): **19/19 PASS**, isolated H2. Git SHA for tested file and pushed blob: `a6cb74f3454dbc2bea3c97a9f4d5759795286afc`.
- **No race fix was needed** in this scenario; preserve the existing guard. This test does not claim completion of TS currency rewards and does not establish DVT-original daily cap or payout rates.

## Refill HUD / 360-second timer baseline — protocol 170 wire check

Original `UI/UIMine.lua` handles successful S170-010 in `UIMine.Mining` by adding the reported fullness to `machineData[itemIndex].full`; it does **not** update `oldMachineData` which `UIMine.UpdateTimer` uses to recalculate the live fullness. Therefore the backend must follow S170-010 with the authoritative full S170-001 snapshot to reset both live values and the timer baseline via `UIMine.SetData`.

Kotlin `MiningHandler.refill` already does this:
1. S023-009 BAG reduction
2. S170-010 success, using original reversed worker wire index
3. S170-001 fresh `MiningSystem.getView` after commit

New regression `Dvt115MineRefillHudRefreshWireTest` asserts the actual byte sequence and worker-fullness positions for a representative C170-010 (worker1, two foods), rejection behavior without BAG debit or timer refresh, and no mutation from malformed packets.

Combined isolated Gradle `:tests:test` on six DVT mine test classes: **30/30 PASS**, including new 3 tests, prior SQL 10, client source parity 6, fake-state 3, icon wire 5, clock 3. The tested file SHA-1 Git blob matches pushed `9d1381a955b88323d53e99fb928930a1997d1a8e`.

No Kotlin gameplay source change, no client change, no DEV deployment. This still does not prove original server TS payout amounts, daily cap/reset, or actual mining income.

## DVT original IOS item DATA — four miner foods and restart fractional timer (2026-10-15)

**Exact original runtime bytes:** user's staged client `D:/Games/Ts-Offline/TSNPCVN_9PET_STAGING/AssetBundles/Data/IOS_Item_C.dat` is byte-for-byte identical to server `C:/Server-Game/tsm_dodo/File.dat/Data-ios/IOS_Item_C.dat`, both SHA256 `51f93c59411105d4de61f154bb29c0db10ca41c36b40ab4b1c99f728abc7845b` (6,472,692 bytes). Server `GameDataManager` loads this `Data-ios/IOS_Item_C.dat` through `ItemDatLoader` as the authoritative `ItemDef` map, not the older `Data/Item_C.dat`.

Lua DVT original `UI/UIMine.lua`, `OnClick_Set`, uses `itemDatas[machineData[itemIndex].item].threshold` to compute refill per food. Loading the exact IOS DAT with the SAME Kotlin `ItemDatLoader` produces:

| Food item ID | Threshold, DVT source |
| --- | ---: |
| `55431` | 10 |
| `55432` | 10 |
| `55433` | 10 |
| `55434` | 10 |

New `Dvt115MineFuelOriginalItemDataTest` **pins each exactly to 10**; the value is no longer only a mocked test input. This is **FOOD/fullness**, not DVT original reward TS yield. **Never treat 10 hunger as coin payout rate.**

**Reconnect boundary check:** `DvtMiningFuelAtomicSqlTest.fractional360SecondTickSurvivesMultipleReconnectsAndSqlReloads` uses real isolated H2 persistence. Read mỏ at 359,999 ms after timestamp, then 360,001 ms, then 719,999 ms, then exact 720,000 ms, each with a newly constructed Kotlin `MiningSystem`. It checks the 360,000-ms remainder survives repeated reads and that exhaustion freezes at 0 without dipping into negative food. This mirrors client `UIMine.UpdateTimer` floor(elapsed/360), prevents each UI refresh from resetting the mining timer.

**Validation:** isolated worktree, `gradlew :tests:test` against all seven mine regression suites, **32/32 PASS** and `BUILD SUCCESSFUL`. Exact Git tree blobs from tested files pushed to this Draft PR: `DvtMiningFuelAtomicSqlTest.kt` `3de39da71c2fa9486e46656b5985b2f2e48c0e09`, `Dvt115MineFuelOriginalItemDataTest.kt` `c81601029692457c228dfcbcf2ef04852d136376`. The initial prep script correctly **refused** any edits when it compared a raw Windows CRLF SHA1 against a Git blob SHA1; corrected to use `git hash-object`, backed up the two test files before changes, then normalized LF. All subsequent tests passed. No failed game patch has been applied.

**Important remaining scope:** backend DVT original four-worker TS currency reward formula, daily cap trigger/reset and ore debit still unproven by the client and not implemented. DEV actual gameplay files, client, DB, and JVM PID10012 unchanged.
