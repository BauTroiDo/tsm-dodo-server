# DVT two-player C025 SQL → BAG/MONEY cache publication checkpoint — 2026-10-13

## Scope and status

**STAGED SOURCE + Kotlin tests; deliberately UNWIRED and NOT DEPLOYED TO DEV.** This milestone follows repository SQL transaction `e7dce20` and live duplicate-slot mitigation `baf3c59` / DEV checkpoint `4054202`. The running DEV version still rejects repeated C025 BAG indices and protects Treasure kind98 cards; it does not use the cross-player SQL transaction.

### Previously verified

`InventoryRepositoryImpl.commitTwoCharacterTradeAtomic` performs before-state SQL compare-and-lock for both player BAG snapshots, checks exact `ThingData` quantity/metadata conservation and gold conservation, then debits and credits both stored players in one Exposed transaction. H2 rollback tests exercise SQL failure after partial writes. But direct use of that primitive from the existing `TradeSystem.executeTrade` would write SQL first and **leave both live online BAG and MONEY caches unchanged**, allowing later autosave/logout to overwrite the committed transfer.

### New staged publish boundary

- `TradeCacheSettlementCoordinator.stageCommit(first,second)`: same two-player striped `CharacterPersistenceGuard.withLocks` as live TradeSystem; requires distinct positive IDs and **loaded** BAG/MONEY caches matching *both* supplied pre-trade snapshots before any SQL operation.
- Calls the already-staged atomic two-character repository `commitTwoCharacterTradeAtomic` under the two-player locks. If SQL returns false or throws, neither online BAG nor MONEY is published and no C025 success packet is emitted.
- For successful SQL commit only, `withContext(NonCancellable)` surrounds the SQL await **and both cache publications** so coroutine cancellation cannot interrupt after the commit. No suspending calls between post-SQL updates.
- `InventorySystem.getLoadedBagSnapshot` differentiates absent online cache from empty inventory. `publishCommittedTradeBagSync` publishes a **new ConcurrentHashMap snapshot by reference assignment**, instead of exposing intermediate clear/put operations.
- `MoneySystem.getLoadedMoney` distinguishes absent money from zero. `publishCommittedTradeGoldSync` updates both currency fields in one `synchronized(arr)` monitor.

No new C025 response, no new owner, no physical Treasure ability245 bank conversion, no character-data writes, no SQL migration, no changes to DVT original Lua/UiOpen maps or mission gates.

### Regression coverage

`DvtTradeCacheSettlementCoordinatorTest.kt` runs the **actual** InventorySystem and MoneySystem in a coroutine environment using a mocked SQL repository: two concurrent full-BAG autosaves cannot snapshot stale data while DB commit is suspended, SQL rejection and exception leave both caches unchanged, cancellation after SQL starts cannot omit either cache publication, partner logout waits and persists only the fully committed new BAG/GOLD, invalid/stale/unloaded pre-state prevents SQL debit. Source audit `tools/test_dvt_trade_two_player_cache_publish_staged_20261013.py` pins these semantics, checks C025 handler/DI are **not wired**, and is added to `.github/workflows/dvt-data-audit.yml`.

### Blockers before live C025 activation

1. `MoneySystem.addGold/subtractGold` and some other synchronous reward handlers can mutate RAM outside the coroutine character fence while a staged SQL operation is pending; they could be overwritten by post-commit snapshot publication. They must become coherent or use a measured transaction-safe write journal/resync policy before activation.
2. Other direct InventorySystem BAG writers outside fully guarded C025 can race. Their ownership and all call sites must be audited, especially pet trade and EXP-slot helpers.
3. `TradeSystem.confirmOrCancel` still performs final C025 item execution synchronously inside `synchronized(trade)`; integrating a suspending SQL commit requires a safe state-machine transition that prevents replay, disconnect race and duplicate completion without holding a JVM monitor across suspension. Do not patch this by simply making `executeTrade` suspend inside synchronized.
4. Genuine post-commit host process loss is recovered by loading committed SQL on new login, but all live writer paths must honor persistence consistency. Full peer-to-peer item+gold+pet SQL rollback is not yet implemented.
5. Original DVT Treasure `specialAbility=245`, C123 ownership and StarExp rules still not decoded; Treasure transfer/level/star remains disabled.

**User-requested final client character E2E:** still deferred, with this checkpoint preserved for next chat.

## Checked and verified 2026-10-13, next-conversation handoff

### Commits and CI

- Source + tests + initial checkpoint **`3f39aeadd811e75a971a0327b8ed5d1194d48cdf`** pushed to `BauTroiDo/tsm-dodo-server` branch `fix/dvt-quest-nav-parity-20261011` in PR **#41**.
- [GitHub Server CI run `38307156764`](https://github.com/BauTroiDo/tsm-dodo-server/actions/runs/38307156764) **PASS** (Kotlin compilation and tests, including `DvtTradeCacheSettlementCoordinatorTest`).
- [GitHub DVT data audit run `38307156534`](https://github.com/BauTroiDo/tsm-dodo-server/actions/runs/38307156534) **PASS** (source isolation).
- Full local DVT suite **249/249 Python tests PASS**, run with `python -m unittest discover -s tools -p test_dvt_*.py -v`, log `docs/_working_trade_two_player_cache_publish_all_dvt.log` (untracked, intentionally not committed).
- Remote DEV SSH `tsm-prod`: **game Java PID 5564**, TCP `6613` listening on IPv4 and IPv6 during the checkpoint. No new Kotlin sources from this staged-only milestone deployed, no Java restart. Live deployed TradeSystem duplicate-slot patch remains `baf3c59`, separately backed up from prior deployment under `C:\Server-Game\tsm_dodo\backups\dvt_trade_dupguard_baf3c59_20261013_033729\TradeSystem.kt`.

### Source invariants, actual coverage

- New staged-only coordinator coordinates both player `InventorySystem` BAG snapshots and `MoneySystem` gold/auctionGold caches AFTER the existing strict SQL transaction succeeds, inside one two-character persistence guard with `NonCancellable` around SQL and immediate RAM publication. Reject stale/unloaded pre-state before DB call; reject/exception from DB means no cache writes.
- `DvtTradeCacheSettlementCoordinatorTest`: SIX coroutine/Kotlin scenarios: successful two-player commit blocks concurrent autosave, SQL returns false, SQL throws before commit, cancellation while SQL pending, second-player logout during commit, stale/unloaded BAG or gold state blocks SQL. Source tests pin no DI/handler wiring.
- Repository prior `commitTwoCharacterTradeAtomic` retains H2 tests for conservation and rollback. New cache coordinator is a separate STEP, **NOT C025 integration**. Original DVT client Lua/IL2CPP, Eve, quest flags, UiOpen, all players' DB rows, Treasure grant/upgrade systems untouched.

### Critical unresolved blockers — do next in order

1. **Audit and fence the synchronous gold writers** `MoneySystem.addGold/subtractGold` and their real call sites (battle, quests, shop, trade, activity, NPC, etc.) so none can silently alter online gold while the two-player staged SQL transaction is in flight. Do NOT naively wrap synchronous functions in coroutine locks; use a safe mechanism that respects existing non-suspending call sites, avoids deadlock and never drops genuine rewards.
2. **Audit remaining synchronous InventorySystem writers** `setBagItemSync`, `addItemDirectSync` outside TradeSystem and EXP-slot, plus any direct map references; coordinate with same character fence or a proven mutation epoch/replay policy.
3. **Rework C025 trade Confirmed state machine** to move a suspending SQL commit safely OUTSIDE `synchronized(trade)`, implement exactly-once guarded state transition, disconnect/cancel handling and original C025 success/failure packet parity. Do not suspend inside a JVM monitor or fire success before durable commit; do not apply a guessed protocol response.
4. Add more H2 failure injection tests and complete DEV dry run with staged two-player characters **only after** these paths are safe. Keep real player-controlled manual client E2E for final consolidated pass, per user instruction.
5. Pet trade SQL rollback, DVT Treasure ability245 card-bank origin, C123 level/star/StarExp and MoneySystem vs direct-SQL cash debit are separate unsolved issues. Treasure kind98 card use remains fail-closed (no silent item loss, but NO deposit), Treasure repository/coordinator remain unwired; do not blanket grant bit flags or unlock DVT UiOpen/quest gates.

### Paths / workflow

- Local Windows checkout: `D:\Games\Ts-Offline\_dvt_nav_batch_20261012` (contains many unrelated `_working_*` untracked files; stage only exact changed files). Exact remote DEV game: `C:\Server-Game\tsm_dodo` via SSH alias `tsm-prod`.
- Key source: `gamelogic/src/main/kotlin/tw/tsm/gamelogic/systems/TradeCacheSettlementCoordinator.kt`; `InventorySystem.kt`, `MoneySystem.kt`; domain `InventoryRepository.kt` and persistence `InventoryRepositoryImpl.kt`; C025 handler `TradeHandler.kt`, existing live `TradeSystem.kt`.
- Tests `tests/src/test/kotlin/tw/tsm/tests/DvtTradeCacheSettlementCoordinatorTest.kt` and `tools/test_dvt_trade_two_player_cache_publish_staged_20261013.py`.
- Do not edit DVT mission/UiOpen gates or deploy unwired work solely to restart DEV. Code pushed and checkpoint is the durable handoff to the next chat.

### User preferences

- Proceed autonomously and quickly across milestones; push GitHub, run CI before DEV, preserve SHA-backed source backups, never blindly reset diverged DEV code, keep written chronological checkpoint.
- DEV is a test server and may be restarted only when a live runtime source change is actually ready. Defer all manual client-character testing to one comprehensive final gameplay check.
