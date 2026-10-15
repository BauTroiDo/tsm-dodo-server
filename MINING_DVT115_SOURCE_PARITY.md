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
