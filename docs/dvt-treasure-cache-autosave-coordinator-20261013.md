# DVT Treasure — staged online BAG/cache settlement (2026-10-13)

**Status: committed source/tests only; NOT LIVE.** No changed client files, original data, DEV database migration, network messages or game-server restart. Manual in-game E2E is deferred.

## Verified race and scope

- `InventorySystem.save` and `InventorySystem.saveAndUnload` wrap full BAG snapshot persistence with `CharacterPersistenceGuard.withLock(characterId)`.
- `TreasureRepositoryImpl.bankCard` previously committed a physical BAG debit + S123 ownership increment atomically in SQL, but it had no mechanism for publishing the BAG decrement to the online `InventorySystem.bags` cache. An autosave or logout could therefore overwrite the SQL debit with the old cache quantity.
- Multiple other bag mutations still do **not** consistently acquire `CharacterPersistenceGuard`. Examples: `removeFromBag`, `moveBagItem`, `setBagItemsAtomic`, and synchronous methods. Thus **sharing this lock with autosave/logout is necessary but NOT sufficient to activate the banking feature**.

## New dormant runtime orchestration

`gamelogic/src/main/kotlin/tw/tsm/gamelogic/systems/TreasureBankCacheCoordinator.kt`: a purely internal source-guarded coordinator, not constructed or injected anywhere at runtime.

1. Validate character ID, card/treasure ID via trusted source-only mapping, BAG slot, quantity and `expectedVersion`; require an actually loaded BAG slot and sufficient physical original item count before making any DB call.
2. Acquire `CharacterPersistenceGuard.withLock(characterId)`, which is also used by the two BAG snapshot persistence paths.
3. In `withContext(NonCancellable)`, call the already-staged and SQL-atomic `TreasureRepository.bankCard`; on rejection or DB rollback leave cache untouched.
4. On a successful SQL commit only, publish `remainingQuantity` (or remove the exhausted slot) to `InventorySystem.setBagItemSync` **before releasing the autosave/logout mutex**. Return a source BAG delta struct to a future caller; it sends no fabricated C023/S123 packet.
5. A cancellation after the SQL commit cannot interrupt the immediate in-memory publication.

This prevents the particular **full-BAG snapshot race against save/logout** *provided other writers also honor the character lock*. It does not solve unguarded item use/move/gift/mall writers racing with the same slot; no bank transfer is activated. `MoneySystem` and its independent cache remain outside this card-only coordinator; C123 level/star purchases stay disabled.

## Tests

`tests/src/test/kotlin/tw/tsm/tests/DvtTreasureBankCacheCoordinatorTest.kt` drives a **real InventorySystem** with a mock repository to prove:
- A SQL operation paused before commit holds the character lock, preventing `InventorySystem.save` from persisting the old BAG quantity. After commit and publication, periodic save sees only the new quantity.
- `saveAndUnload` likewise cannot snapshot/unload a stale slot while the bank transaction is open; full consumption yields an absent slot.
- A rejected/failed SQL transaction leaves the online BAG and later autosave unchanged.
- Cancellation during a pending SQL commit cannot prevent publishing the committed BAG debit.
- Invalid item source mapping, nonexistent slot, insufficient quantity, bad version never contact SQL.

`tools/test_dvt_treasure_cache_coherence_staged_20261013.py` locks these properties, confirms C023/C123 handlers remain unchanged and the existing `ItemUseProcessor` protective guard is intact. The DVT audit CI workflow includes this source check.

**Next blocker:** coordinate all other BAG mutation paths and verify native `specialAbility=245` banking RPC semantics. Only then consider wiring this into the game and sending the source-original network response; no source proof for that behavior is fabricated here.
