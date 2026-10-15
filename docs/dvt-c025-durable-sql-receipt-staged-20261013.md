# DVT C025 durable SQL trade receipts — checkpoint 2026-10-13

## Status and release boundary

**STAGED ONLY; NO active C025 SQL wiring; NO new DEV deployment/restart.** This milestone follows commit `b52c704` (pure full-BAG planner), commit `49faa60` (finalization gate), `TradeCacheSettlementCoordinator` staged tests, and the existing two-player atomic BAG+gold SQL repository method.

A C025 transfer needs to survive a lost SQL acknowledgement or process restart without blindly debiting both players again. The pre-existing optimistic snapshot comparison usually prevents duplicate credits, but it cannot distinguish `NOT_COMMITTED` from `COMMITTED_WITH_LOST_ACK`. This new staged interface creates a durable immutable UUID receipt in **the same SQL transaction as the BAG and money transfer**, and provides an independent read-only receipt lookup for uncertain outcomes.

## New source and contract

- `TradeSettlementReceipts` SQL table (`trade_settlement_receipts`) has primary key canonical UUID `operation_id`, two sorted character IDs, 64-character stable SHA256 `snapshot_digest`, and committed timestamp; it is registered with `DatabaseFactory.createMissingTablesAndColumns`. **Do not deploy schema changes to DEV until C025 rollout plan and backup are approved by tests.**
- `TradeSnapshotDigest.of(first,second)` serializes the entire immutable full-BAG before/after plan in sorted character and slot order into a versioned binary SHA-256 digest: item ID, stack quantity, durability, elements, proof/growth, expiry bit representation, locks, affixes, reinforcement, style, special flags and ALL Myth/NFT attributes plus both gold values and auction gold. No Kotlin `toString()` hash, no use of nondeterministic map iteration.
- `InventoryRepository.commitTwoCharacterTradeWithReceiptAtomic(operationId,first,second)` returns one of `COMMITTED_NOW` (only this permits normal cache publication), `ALREADY_COMMITTED` (must reconcile from authoritative SQL, NEVER overwrite RAM with stale planned state), `REJECTED` (no transfer in this attempt), or `TOKEN_CONFLICT` (same UUID, different fingerprint or participants).
- `InventoryRepository.findTwoCharacterTradeReceipt(operationId,first,second)` returns `MATCHED`, `NOT_FOUND` or `TOKEN_CONFLICT` for reconciliation without applying ANY transfer. A missing record is not a license to retry unless the caller has independently established the previous SQL operation cannot still be pending.
- `InventoryRepositoryImpl` shares one trade validation and SQL transfer implementation for the legacy unreceipted function (retaining its Boolean behavior) and the new receipted function. In the receipted path both account money rows are locked by ascending character ID BEFORE checking the UUID row with SQL `FOR UPDATE`, then both full BAG and gold before-snapshots are verified. Both BAG sets + both gold balances are changed in the same SQL transaction, and only then the durable receipt row is INSERTed. Any constraint failure or duplicate UUID transaction failure rolls back ALL modifications.
- The change intentionally does **not** wire the new repository method into the existing `TradeCacheSettlementCoordinator.stageCommit`, so no premature claim that it guarantees RAM publication on replay, disconnect, or process crash. The C025 handler still does legacy RAM settlement until a later controlled milestone.

## Regression validation

- Kotlin H2 `DvtTradeDurableReceiptSqlTest.kt`: two-character SQL+receipt atomic success; reconnect / fresh repository replay returns `ALREADY_COMMITTED` and receipt lookup `MATCHED`; swapped input order is idempotent; forged digest / UUID reuse is rejected without new credit; induced constraint failure rolls back BAG, GOLD and receipt; second UUID with stale snapshot can't debit twice; invalid/forged UUID and gold imbalance fail before writes; legacy unreceipted API remains unchanged.
- Python source audit `tools/test_dvt_trade_sql_durable_receipt_20261013.py` asserts SQL transaction+row lock and receipt ordering, full metadata digest, H2 regression cases, and **NO C025 / DI wiring**; added to DVT data audit workflow.
- Confirm exact SHA GitHub Server CI and DVT data audit pass before claiming all Kotlin tests pass.

## Threat model / unresolved blockers before live deployment

1. There is still **NO operation UUID attached to the live TradeSession**, nor frozen item snapshots at upload C025-002, nor a complete safe C025 `SETTLING` state transition. Do NOT wire just the SQL repository.
2. Database acknowledgement loss may yield an indeterminate exception. On restart, query receipt by the **same** canonical operation ID and same immutable fingerprint. `MATCHED` proves SQL committed; `NOT_FOUND` alone is insufficient if another transaction with that UUID can still be in flight. Explicit reconciliation is required before clearing quarantine.
3. Receipt checks and both gold rows must be isolated against concurrent players and must not blindly re-publish stale caches on `ALREADY_COMMITTED`. Operations involving different character pairs with the same UUID are prevented by the SQL primary key; their losing transaction may raise a unique-key exception and remains indeterminate pending receipt lookup.
4. Source-side online BAG and gold writers, including old synchronous trade and quest rewards, must acquire coherent fences before the new C025 path can run; cannot promise item/coin safety from schema alone.
5. Lock timeouts or transaction failures cannot be treated as proven rejection if the commit outcome is unknown. Distinguish returned `REJECTED` from exceptions; catch an exception above repository and check durable receipt.
6. Pet C025-012, guild-bound one-time tradeability, and Treasure remain separate original Lua/IL2CPP parity tasks. Manual client E2E deferred until consolidated end-of-project pass.

## Existing workspaces / DEV preservation

- GitHub repo: `BauTroiDo/tsm-dodo-server`, draft PR #41 branch `fix/dvt-quest-nav-parity-20261011`.
- Windows isolated clean worktree: `D:\Games\Ts-Offline\_dvt_trade_gate_20261013`; preserve dirty original `D:\Games\Ts-Offline\_dvt_nav_batch_20261012`.
- Running DEV: `C:\Server-Game\tsm_dodo` over `tsm-prod`; source unchanged in this milestone. Prior verified game Java PID 12184 on TCP 6613, MySQL initialized, 984 DVT missions and 4505 MarkStep entries.
- Keep the backup `C:\Server-Game\tsm_dodo\backups\dvt_gold_fence_955333c_20261013_083624`. Do not echo DB credentials from startup logs; isolate and rotate them separately.
