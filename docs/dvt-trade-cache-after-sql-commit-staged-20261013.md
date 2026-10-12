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
