# DVT custom miner economy — NOT official Chinese Gamer rates

Status: DEV-only, explicit opt-in.

The original DVT client protocol and fullness timing are preserved, but the reward economy is a custom server rule set because official 1.15 backend values were not available.

## Config loaded on restart (environment variables)

- DVT_CUSTOM_MINE_ENABLED=false by default; set true only on DEV.
- DVT_CUSTOM_MINE_RATES=1,2,3,4 (TS per 360-second worker hunger tick, in worker priority order)
- DVT_CUSTOM_MINE_DAILY_TS=120 (per character per local calendar day)
- DVT_CUSTOM_MINE_ZONE=Asia/Ho_Chi_Minh (midnight reset in that local timezone)

One 360-second tick consumes one fullness point; selected worker receives its configured TS credit. Custom shared ore debit is one ore unit per TS received; ore reserve starts at 600,000,000. The user has approved defining private-server rules, NOT calling them official DVT rates.

## Atomicity / reset

MiningSystem.getView acquires CharacterPersistenceGuard, then PlayerCurrencySystem writer fence; repository SQL locks character_mining, initializes and locks the shared mining_ore_reserves row, locks wallet kind8 and commits worker fullness + cursor + day_limit + wallet + ore debit + idempotent daily receipt together in one transaction.

All daily cumulative limits are derived from durable custom1:YYYYMMDD:charId:startTime receipt rows in mining_ts_award_receipts. Reconnects, restarts and new day midnights do not mint rewards twice. The user-visible S170-001 day_limit stops fullness while capped, then a new local day releases it. A 30-second server autosave settles online miners; opening mine UI or refilling settles via getView. S035-050 kind8 carries wallet after confirmed SQL success.

Unsupported/empty ore and full wallet never consume a hunger point for unpaid earnings. Additional first-ore-row simultaneous miner regression guards against lost ore updates.

## Test evidence

DvtCustomMiningEconomySqlTest: 8/8 PASS including four worker rates, same receipt replay, local-midnight quota reset, SQL transaction failure, shared ore exhaustion, first-time 2-character simultaneous ore debit and safe disabled mode.

Broad mine+ore+currency+clock baseline: 47/47 tests across ten classes PASS, Gradle BUILD SUCCESSFUL in isolated branch based on Draft PR #67 (itself stacked on #64 and #57).

## Before DEV activation

Do not overwrite the owner's dirty DEV source tree at C:\Server-Game\tsm_dodo. Compare source changes, backup every target file with hashes, build on DEV source and restart only after verifying no unrelated changes are displaced. Enable explicitly with DVT_CUSTOM_MINE_ENABLED=true and test S035-050 / S170-001 / ore ledger on isolated player data. Issue #52 holds audit checkpoints and #61 contains rollback policy.

This design is PRIVATE-SERVER CUSTOM and does NOT claim numeric parity with Chinese Gamer.
