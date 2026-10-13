# DVT C025 SQL receipt + consistent two-character cache reconciliation — 2026-10-13

## Objective / deployment boundary

Continuing after **`7e20e1675b7af2c24ea785e9141676888768e563`** (frozen uploaded C025 item offers and server UUID), add a safe response to an *indeterminate SQL outcome*. Permanent UUID receipt code already exists under `226672b`. The new code inspects the durable receipt plus both authoritative BAG and GOLD states in **one SQL transaction**; if no intervening change exists, it may repair an interrupted pair of RAM cache publications under both player fences. **Staged only: no integration with live C025, no DEV deployment or migration, no server restart.**

## Source changes

- `InventoryRepository.inspectTwoCharacterTradeReceiptAtomic(operationId, first, second)` returns `TradeReceiptInspection(receipt, first?, second?)` in one consistent SQL transaction.
- `InventoryRepositoryImpl` acquires `CharacterMoney` row locks for both characters in **ascending character ID** order, matching the existing durable SQL commit, then locks the UUID receipt row. If UUID, paired participants or complete `TradeSnapshotDigest` disagree, it returns `TOKEN_CONFLICT` without publishing data. If absent, `NOT_FOUND`. Only on a matching receipt does it inspect both full BAG row sets (`FOR UPDATE`) and both gold/auctionGold records, returning these snapshots together. Missing persisted money fails closed to `MATCHED` with absent snapshot so higher layer quarantines.
- New **UNWIRED** `TradeReceiptCacheReconciler` takes the existing `InventoryRepository`, `InventorySystem` and `MoneySystem`. It runs under `NonCancellable`, both `CharacterPersistenceGuard` locks and the synchronized gold-mutation fence.
- The reconciler requires the **matching permanent receipt and both actual SQL account states exactly equal the planned after state**. It also requires every loaded BAG cache and currency cache to be either exactly the planned before or exactly the planned after state. Any unknown/newer item, balance, missing cache, receipt conflict, or progressed database balance results in a typed **quarantine outcome** and NO cache changes.
- If both RAM caches are already in the exact SQL after state, return `ALREADY_SYNCED` (no writes). If SQL shows committed but any online side still holds the exact old snapshot, publish all post-commit BAG and gold snapshots synchronously for both players, with all locks still held; return `RESTORED`.
- `NOT_FOUND` does **not** mean the SQL transaction was rolled back; no new SQL commit/retry is ever attempted by the reconciler. A read error yields `INDETERMINATE`, not `REJECTED`. A matching UUID + unexpected newer SQL or RAM data is **not** automatically overwritten.
- No success packet from the reconciler. Only a future full settlement integration can decide how to notify an online counterparty after authenticated confirmation and cache publication.

## Regression validation

- New `DvtTradeReceiptCacheReconcilerTest.kt` has **10 Kotlin cases** using the real `InventorySystem` and `MoneySystem` with a mocked repository: recovery of old caches, replay/no-op, recovery after half-published BAG/GOLD, safeguarding later local gold award, safeguarding extra BAG item, rejecting later SQL modification, missing and conflicting receipts, unloaded/missing data, indeterminate SQL exception, and a synchronous gold writer race against in-flight SQL read.
- Existing real H2 `DvtTradeDurableReceiptSqlTest.kt` gains **3 cases**: confirmed matched receipt returns both actual SQL BAG+GOLD states and preserves input side ordering; SQL gold changes after the trade appear in observation (not stale planned values); forged full digest and invalid UUID yield token conflict with no exposed account snapshots.
- New Python audit `tools/test_dvt_trade_receipt_atomic_reconciliation_20261013.py` registered with `.github/workflows/dvt-data-audit.yml` checks SQL receipt+money+BAG atomic read and row lock ordering, quarantine and non-retry paths, Kotlin cases, and confirms **no C025 / DI / existing coordinator wiring**.
- Run complete local DVT audits; both exact-SHA GitHub Server CI + DVT audit workflows must pass before claiming JVM regression PASS. Windows client PC does not have JDK on PATH, so Kotlin verification comes from GitHub Actions.

## Remaining high-priority barriers to live C025 SQL release

1. **No single live finalization composition yet:** the old C025 item path still executes RAM-only `executeTrade` while the staged planner, UUID receipts, finalization gate, SQL+RAM cache coordinator and this reconciler are separate components. Must replace RAM-only path with one auditable coroutine state machine OUTSIDE JVM `synchronized(trade)`, never send original S:025-002 reason-4 success before DB+cache poststate.
2. The current receipt-aware SQL API is not wired into `TradeCacheSettlementCoordinator.stageCommit` yet; need an integration path that writes the UUID receipt and recognizes the indeterminate state **without ever retrying the original transfer on a possible lost ACK**. The operation UUID must survive across the same logical attempt; the new `TradeSession.operationId` is in-memory only until commit.
3. Do not call cache publication if the authoritative DB BAG/GOLD or online cache has changed since the receipt plan. A previous successful trade followed by a legitimate new reward is not a reason to overwrite that reward with the trade after-state. If both caches already contain advanced legitimate data, support a separate source-backed revalidation/reload strategy, not blind overwrite.
4. Audit ALL direct BAG and gold writers against their corresponding persistence fences and ensure no deadlocks with autosave/logoff. In the current staged setup, unrelated synchronous BAG mutators without `CharacterPersistenceGuard` remain a blocker. Even a successful unit test cannot prove live thread safety.
5. Test finalization races, packet failures after SQL success, disconnected clients, timeout/unknown commit, server restart, SQL rollback. Keep pet C025-012 and Treasure banking isolated; original DVT one-time and guild-only item rule parity remains unproven.
6. Use SHA-backed per-file backup, selective DEV deploy and migration only after all CI gates. Preserve DEV custom hotfixes. User requested one consolidated manual character playtest only after the systems are finished.

## Checkpoint provenance

- GitHub `https://github.com/BauTroiDo/tsm-dodo-server`, PR #41 branch `fix/dvt-quest-nav-parity-20261011`.
- Local clean isolated worktree `D:\Games\Ts-Offline\_dvt_trade_gate_20261013`, preserve unrelated dirty `D:\Games\Ts-Offline\_dvt_nav_batch_20261012`.
- DEV `tsm-prod` directory `C:\Server-Game\tsm_dodo` remains untouched this step; prior verified JVM PID 12184, TCP 6613, 984 missions / 4505 MarkSteps, gold fence deployed; reference backup `backups\dvt_gold_fence_955333c_20261013_083624`.
- Security: legacy DEV runtime startup line previously exposed DB credentials; never echo in tool logs/PR comments; redact and rotate in a controlled operational step.
