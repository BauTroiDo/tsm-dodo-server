# DVT gold writer fence and C031 inn fee correction — 2026-10-13

**Status: source / CI staging; run-time rollout only after CI and guarded SHA deployment.** Follow-up to C025 atomic BAG+GOLD SQL staging `e7dce20`, post-SQL RAM publication `3f39aea`, and latest checkpoint `b64f028`. Preserve original IL2CPP/Lua DVT level/mission unlocks, no synthetic items, entitlements or C123 upgrades.

## Grounded full gold writer audit

`git grep` across `app/src/main/kotlin` and `gamelogic/src/main/kotlin` confirms **all canonical synchronous `MoneySystem.addGold/subtractGold` call sites** include EveResultExecutor and quest reward execution, BattleManager combat payout, TradeSystem item/pet trade, dispatch, shop and NPC trade, welfare, astrolabe, GM commands, pets, mount and house board. Merely using the coroutine `CharacterPersistenceGuard` on autosave and the proposed C025 coordinator leaves these **synchronous** cash operations free to change the online gold cache after the SQL before-state check and before the post-commit gold snapshot is published. That would lose a legitimate combat or mission gold reward.

## Correction: shared bounded sync gold fence

`MoneySystem` now provides a 256-stripe fair Java `Semaphore` array keyed by the same character hashing rule as existing coroutine persistence locks. The original non-suspending `addGold`/`subtractGold` signatures and positive-amount / amount-cap guards remain unchanged; each now obtains and releases its character stripe semaphore around the preexisting synchronized-array balance update. No virtual currency creation, free reward, cap bypass or silently dropped mutation was added. The two single-field read helpers `getGold` and `getAuctionGold` also read under synchronized array monitor as `getMoney` already did.

`MoneySystem.withTradeGoldMutationFence(firstId, secondId, action)` (staged, **not wired to C025**) acquires the two distinct physical stripe semaphores in ascending index order; same-stripe pair acquires only once. Permit acquisition uses `NonCancellable + Dispatchers.IO` so a cancellation when moving dispatchers cannot abandon a previously acquired semaphore; releases permits in finally. The already-staged `TradeCacheSettlementCoordinator.stageCommit` now takes these two gold permits INSIDE the two-character coroutine persistence fence, before checking online BAG/MONEY snapshots, and retains them across SQL commit, `NonCancellable` publication of both BAG maps and both gold/auction balances. Thus a synchronous battle or Eve gold mutation that occurs during the SQL window waits and then applies to the **new committed balance**, rather than being overwritten. Calling synchronous MoneySystem writers inside the permit-owning block is forbidden (nonreentrant semaphore).

**Important remaining risk:** A synchronous gold writer called on an event-loop thread will BLOCK if live cross-character SQL owns the permit. Therefore do **not** activate `TradeCacheSettlementCoordinator` in `TradeSystem/C025` until confirmation handling is moved to a safe dispatcher and verified without a JVM monitor or session/disconnect deadlock. The live system currently does NOT hold these permits for a long async SQL operation; normal synchronous mutations acquire/release promptly. Pending new gold mutations remain RAM-only until existing save service flushes; this work is not a crash-proof global currency ledger.

## Real bug found: C031 inn rest fee

`app/.../InnHandler.kt:handleRest` called `moneySystem.addGold(charId,-cost)` despite `MoneySystem.addGold` refusing all non-positive input. This means the server never charged for an inn rest while still sending negative money feedback. Replaced with **single check-and-debit** `moneySystem.subtractGold(charId,cost)` under `CharacterPersistenceGuard.withLock(charId)`, failure sends source existing `sendInnRestResult(session,1)` and does not restore HP/SP, success sends `sendSubtractMoney(session,cost)` followed by unchanged rest and record point flow. The current cost is a source-unverified fixed `100`, untouched in this patch; tracing DVT original cost is a separate task.

## Regression

- `DvtTradeCacheSettlementCoordinatorTest`: real InventorySystem/MoneySystem with mock SQL; combat reward synchronous writer started on `Dispatchers.Default` while SQL pending cannot mutate stale gold and applies after publication, subsequent save snapshots the combined balance. Concurrent high spend rechecks the **new** gold balance and fails if insufficient.
- `DvtTradePairAndMoneyAutosaveLockTest`: reversed two-character semaphore ordering cannot deadlock, same stripe (IDs1,257) does not reacquire a nonreentrant permit.
- `tools/test_dvt_sync_gold_writer_fence_inn_20261013.py` pins the write fences, no C025 or Treasure registration, C031 fee response, and Kotlin tests. CI DVT audit workflow gains this source validation.
- Full DVT Python regression, GitHub Server CI/Kotlin, audit and a guarded SHA DEV rollout are mandatory before declaring deployment.

## Remaining next-conversation blockers

1. Existing `TradeSystem.confirmOrCancel` is still in `synchronized(trade)`: transition to an atomic exactly-once state before *suspending* C025 SQL, then send success only after durable commit + both RAM caches; avoid JVM monitor across a suspension.
2. Audit remaining direct BAG synchronous methods used outside trade, and gold writer behavior under prolonged async window to prevent event-loop blocking. No live C025 SQL activation yet.
3. C031 inn fee amount (currently100) remains placeholder and must be sourced from DVT reference. Cross-player PET trade SQL/ownership rollback is not yet implemented.
4. DVT Treasure `specialAbility=245`, C123 card ownership and StarExp remain unsupported; protected kind98 items are still fail-closed rather than silently lost.
5. User requested final consolidated manual client-character E2E, after all repairs, not per-milestone.

Working repo `D:\Games\Ts-Offline\_dvt_nav_batch_20261012`, DEV SSH `tsm-prod` / `C:\Server-Game\tsm_dodo`; branch `fix/dvt-quest-nav-parity-20261011` PR#41. Checkout has numerous unrelated untracked _working files; always stage exact changed files and do not overwrite diverged DEV sources.
