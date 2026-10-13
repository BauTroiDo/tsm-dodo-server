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

## DEV deployment and consolidated regression checkpoint — 2026-10-13 03:23 ICT

- Source commit `27f269001a322df0aab1ccb87817a2149f4fc853` plus corrected pre-existing Python regression `ab558bce1d9d29b0be8c383c7eced41c77f7518c`, pushed to PR#41 `fix/dvt-quest-nav-parity-20261011`.
- **GitHub Server CI PASS**: https://github.com/BauTroiDo/tsm-dodo-server/actions/runs/38303430340 ; **DVT data audit PASS**: https://github.com/BauTroiDo/tsm-dodo-server/actions/runs/38303430346 ; full original DVT Python suite **237/237 PASS** locally. Kotlin tests include reversed lock order, same hash stripe, locked gold autosave and locked logout.
- Guarded SCP/SSH deployment to `tsm-prod` changed only four exact source files: `CharacterPersistenceGuard.kt` (old SHA256 `bb24b8552d0c964e3d95aebffe2de62f7322b167743eb07584cc42d5a463dec6`, new `dc1ceb185044772352e5456a1cf765bbea3c80298703b2f38cb83f1d95a25926`), `MoneySystem.kt` (old `a355146730d9d9b6883278e7734407bf19b5b6f37b792a639855b3a82036b42c`, new `a8cbc455c146788bcc1b21d4bf1a497a3d19d26fa21e51259f71a9276d9deb69`), `TradeSystem.kt` (old `1499ac75b27d63da29b70a855b02be06a1af7f6b15fff109deacd0657651944a`, new `d7c880f172127a77725c45c01bc01ff8b4caa496ce1258cd587c7248d2631543`), `TradeHandler.kt` (old `a77d7348af3b226ab72644c886b4f2d184c8bfee5ea99546710d2b4d3ffbe872`, new `2ab90a27b4b660d76797725156ba879b64e3dc907502cd09ead594911ff69b1e`). All old/ staged / postdeploy SHA checks passed; no remote dirty checkout/reset.
- Original backups: `C:\Server-Game\tsm_dodo\backups\dvt_trade_gold_ab558bc_20261013_032212` (all four filenames preserved). Deployment script: `C:\Users\Administrator\_deploy_trade_gold_ab558bc.ps1`. DEV `:app:classes` **BUILD SUCCESSFUL in 58s**, compiled four classes checked for `withLocks`, `saveMoneyUnfenced`, `confirmOrCancel`, `handleConfirmTrade` before restart.
- Old Java game PID **12260**, new Java PID **11324**. Game restarted and Netty TCP 6613 is LISTENING on both IPv4 / IPv6. Ready at **03:23:20 ICT** in `logs\runtime-production-dvt-trade-gold-ab558bc.log`.
- Original source `File.dat/Data/UiOpen_C.dat` remains **SHA256 `af759749cf6d33081df7de99f715cf682e324bb48fb0ff4c57edb932a78c8b4e`**. Client Lua/IL2CPP, quest maps/events, player inventories/database, reward grants and DVT icon-level gates were untouched.
- **Not claimed:** cross-character trade SQL rollback remains separate / non-atomic; other synchronous MoneySystem.addGold/subtractGold writers may not hold the per-character coroutine guard; in-memory gold can still overwrite a standalone direct SQL TreasureRepository gold debit. `TreasureRepository.purchaseLevel` and `TreasureBankCacheCoordinator` remain unwired, and C123 level/star and kind98 physical-card banking remain rejected. `StarExp` and original specialAbility245 RPC semantics remain unsolved. Do not enable Treasure progression yet.
- The user explicitly requested manual character E2E be deferred to one consolidated end-to-end test pass. Current checks are source/CI/compilation/runtime-readiness only, not player-click validation.
