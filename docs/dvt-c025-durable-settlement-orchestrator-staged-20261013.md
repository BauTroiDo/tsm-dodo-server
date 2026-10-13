# DVT C025: staged full durable item trade composition — 2026-10-13

## Branch, intent, and release boundary

Continuation of checkpoint `19a3c2285648b772b7a3bc47521dc34bb6b84592`. This milestone puts together existing source-backed components in ONE explicitly staged item-trade flow:

- `TradeSession.operationId` (added earlier; canonical server UUID) and deeply frozen `C025-002` offers
- `TradeItemSnapshotPlanner` (whole before/after BAG + GOLD plan, dynamic bag capacity 1..500, full ThingData)
- `TradeItemFinalizationGate` (elect exactly one two-party confirmer, no suspend within JVM synchronized)
- `TradeCacheSettlementCoordinator.stageCommitWithReceipt` (new method: one durable atomic SQL receipt + both guarded BAG and gold RAM publications)
- `TradeReceiptCacheReconciler` (prior milestone: atomic SQL observation and fail-closed repair of interrupted cache publications)

**Critically, this code is NOT YET wired to active `TradeSystem`, `TradeHandler`, `ServerModule`, or any DVT game packet. No DEV deployment, database migration, or restart, and no manual in-game testing.** Existing legacy RAM-only item trade and pet trade are unchanged.

## Exact additions

- `TradeCacheSettlementCoordinator.stageCommitWithReceipt(operationId,first,second)`: validates participant IDs, online full BAG and both MONEY cache before snapshots under `CharacterPersistenceGuard.withLocks` and synchronous two-character gold mutation fence; enters `NonCancellable` for SQL plus two RAM publications. Calls `InventoryRepository.commitTwoCharacterTradeWithReceiptAtomic` exactly once.
- Only `TradeReceiptCommit.COMMITTED_NOW` publishes both complete BAG + GOLD online caches synchronously **while BOTH persistence and gold fences are held**. `ALREADY_COMMITTED` returns without blind cache overwrite; `TOKEN_CONFLICT` is quarantined; `REJECTED` implies no successful commit on that attempt; thrown SQL exceptions return `INDETERMINATE` rather than fabricating rollback or retrying.
- New `TradeItemDurableSettlement` is one-use and immutable (canonical UUID, frozen deep-copy per-player full BAG and offered item maps + slots). It wraps `TradeItemFinalizationGate` and calls `planner.plan`. A failed or stale offer is rejected before SQL. Only the **one** elected winner invokes `coordinator.stageCommitWithReceipt`.
- On committed SQL result and completed cache publication, the gate invokes the provided original DVT success callback once. On `ALREADY_COMMITTED` or `INDETERMINATE`, the flow calls `reconciler.reconcile` **AFTER coordinator releases both locks**, to avoid reentrant mutex deadlock. Only `RESTORED` or `ALREADY_SYNCED` can produce a confirmed success; missing receipt, newer gold/item, absent cache, conflicting digest and database failure are quarantined as `INDETERMINATE` without resubmitting the transfer.
- Cancellation/disconnect during `SETTLING` may suppress outgoing packets in the future active adapter, but cannot stop already-running SQL; packet send exceptions do not reverse the committed gate or allow a second settlement.
- The coordinator's original `stageCommit` method is retained for pre-existing tests; no behavior changes to legacy C025 execution.

## Automated tests

- New `DvtTradeItemDurableSettlementTest.kt`: **10** staging integration cases with REAL `InventorySystem` and `MoneySystem` and mocked SQL repository: 24 concurrent confirmations elect one commit/packet, cancellation before commit, SQL `REJECTED`, lost ACK with MATCHED receipt/paired SQL recovery, unresolved NOT_FOUND receipt quarantine, idempotent ALREADY_COMMITTED cache repair, intervening direct cache writer rejected, stale/frozen NFT affix rejected, packet callback failure after commit, disconnect/cancel during SQL failure never double-notifies.
- New `tools/test_dvt_trade_staged_durable_orchestrator_20261013.py` 4 source release gates, added to DVT GitHub audit workflow.
- Run full local DVT Python audit and exact-head GitHub Server CI + DVT data audit; CI must pass before claiming the Kotlin integration tests passed.

## Remaining blockers BEFORE turning on C025 SQL on DEV

1. Active `TradeSystem` still settles item trade synchronously under `synchronized(trade)`; future adapter must take the confirmed session UUID and immutable C025-002 offer, acquire two character persistence locks to capture both complete BAG+MONEY snapshots + real `RoleCountSystem.getBagCapacity`, then construct exactly one durable settlement and move SQL outside the JVM monitor. This **must** replace, not run alongside, old `executeTrade`.
2. Current staged snapshot composition takes `TradeItemSnapshotPlanner.Side` from caller. Only a trusted server-side under-lock adapter can supply those values; passing client BAG or client currency snapshots is forbidden. Input freezing protects against mutable maps, but the eventual live adapter must bind `TradeSession` lifecycle and store/resume the same UUID.
3. Some unrelated synchronous BAG writers still do not use per-character coroutine persistence locks and some direct SQL effects may bypass trade fences; root-cause audit and safety tests needed. Do not activate SQL while these races exist.
4. Unexpected cache publish exception AFTER confirmed SQL leaves the transaction committed but one RAM side possibly only partially published: flow treats it as `INDETERMINATE`; reconciler may repair only if SQL/online cache are still exact old/new snapshots. Never signal `REASON_SUCCESS=4` before confirmed durable commit **and** both cache publishes.
5. Original DVT `UITrade.lua` guild/one-time specialKind trade rules and original gold/item cap semantics need separate source-parity audit; planner currently fails closed on unclear restrictions. Pet C025-012 and Treasure card exchange remain separate.
6. To roll out: pass all automated H2/concurrency tests; plan SQL schema migration and credential redaction; back up DEV divergences per file with SHA before selective deployment/restart; user requested one consolidated manual player-character test near end.

## Workspaces and provenance

- `BauTroiDo/tsm-dodo-server` draft PR #41, branch `fix/dvt-quest-nav-parity-20261011`.
- Isolated local Windows worktree `D:\Games\Ts-Offline\_dvt_trade_gate_20261013`; preserve unrelated dirty `_dvt_nav_batch_20261012`.
- DEV `tsm-prod` `C:\Server-Game\tsm_dodo` unchanged this milestone. Prior reference `backups\dvt_gold_fence_955333c_20261013_083624`; do not reveal database credentials in earlier runtime production logs.
