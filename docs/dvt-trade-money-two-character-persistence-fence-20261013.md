# DVT two-player TradeSystem + MoneySystem snapshot fences — 2026-10-13

Scope: source-grounded protection of synchronous BAG cache exchanges in C025 and MONEY autosave against concurrent coroutine character persistence. **No Treasure card banking, level purchase, StarExp or DEV database migration enabled.**

## Root cause and narrow fixes

Original Kotlin `TradeSystem.executeTrade` synchronously invokes `InventorySystem.setBagItemSync` and `addItemDirectSync` for **two** players, then `MoneySystem.subtractGold/addGold`; these methods do not obtain a coroutine `CharacterPersistenceGuard` on their own. Since the prior milestones now fence normal BAG mutations, autosave and logout under this mutex, item trading must hold **both characters' locks** around final validation and cache changes. Merely locking the initiating player allows the partner's autosave to write an intermediate partial BAG.

`CharacterPersistenceGuard.withLocks(charIdA,charIdB)` reuses the exact 256 striped locks shared by inventory + staged Treasure; acquires physical stripe locks in **ascending stripe order**, with only one acquisition when both characters hash into the same stripe. The existing `withLock` keeps its original hash algorithm.

`TradeSystem.confirmOrCancel` becomes a suspend function, taking both characters' fences **before** entering the existing synchronized(trade) state machine and, for confirmed deals, `executeTrade` or `executePetTrade`. This preserves existing C025 mode check, uploaded/confirmed order, client packets, anti-forgery validations and player ownership grants. `TradeHandler.handleConfirmTrade` becomes suspend, matching its already-suspend PacketHandler.handle. Existing CollectionBonusScopeTest pet trade MockK expectation uses `coEvery` for the now-suspend API. Final BAG cache mutations now cannot interleave with the guarded autosave/logout or other 17 protected inventory writer paths. **It does not make both players' database changes one SQL transaction**.

`MoneySystem.loadMoney`, `saveMoney` and `saveAndUnload` use the same per-character fence. `saveAndUnload` saves and removes the cached state under one lock using private `saveMoneyUnfenced`, so it cannot delete the cache while an ongoing guarded trade settles. The persisted (gold,auctionGold) pair is copied under synchronized(arr), the same monitor used by the synchronous gold changes; `getMoney` also reads the pair under that monitor. **Independent synchronous addGold/subtractGold writers in other systems remain outside the coroutine lock**, so this change only guarantees coherent save snapshots when participating writers honor the character fence.

## Tests

`DvtTradePairAndMoneyAutosaveLockTest.kt`: simultaneous reversed ID ordering cannot deadlock, stripe collisions 1 and257 never reacquire their own Mutex, autosave does not flush stale gold while a character settlement lock is held, logout cannot discard cache before guarded settlement + save.

`tools/test_dvt_trade_gold_fence_20261013.py` pins pair lock ordering and C025 handler call chain, MoneySystem coherent snapshot/synchronized money update and continued Treasure guard. DVT audit CI workflow runs these source tests. CI Server Kotlin tests/compilation must pass before deployment. Direct client E2E is deferred to the user's one-shot final validation.

## Remaining real blockers

- Synchronous inventory writers outside the guarded TradeSystem/ExpSlot/staged Treasure path can still mutate caches while a coroutine holds the guard.
- Item trade and pet trade do not yet commit BAG + gold + pets across two player rows atomically in one database transaction; a crash between separate savings can still desynchronize ownership and gold.
- `TreasureRepository.purchaseLevel` is still unregistered; direct SQL debit without a MoneySystem cache transaction could be overwritten by later gold save. Source ability245 and StarExp rules remain unresolved. No new Treasure RPC/grants.
