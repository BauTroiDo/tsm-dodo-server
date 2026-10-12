# TS DVT BAG writer serialization — 2026-10-13

**Scope: actual code change to InventorySystem's nine core suspend BAG mutation APIs**, committed with Kotlin + source tests. The staged TreasureBankCacheCoordinator and TreasureRepository still remain **unwired**. This milestone neither activates C023-015 Treasure bank transfers nor C123 level/star purchases, never creates `character_treasures` on DEV, and never changes mission- or level-gated icons.

## Proven concurrency issue

`InventorySystem.save` and `saveAndUnload` already use `CharacterPersistenceGuard.withLock(characterId)`. The staged `TreasureBankCacheCoordinator.stageBank` uses the same per-character mutex while executing its atomic SQL debit and publishing a BAG cache delta. Prior to this patch, independent inventory writes (`moveBagItem`, `removeFromBag`, `setBagItemsAtomic`, gift-box rewards, etc.) did not use that mutex; they could still mutate/persist a stale BAG slot while the staged transfer was in progress. SQL-transaction atomicity alone does not prevent overwriting item quantities from memory.

## Narrow correction

`gamelogic/src/main/kotlin/tw/tsm/gamelogic/systems/InventorySystem.kt` now serializes **the complete body** of these nine existing suspend methods with `CharacterPersistenceGuard`:

`addItemsToBag`, `addItemsToBagExact`, `addItemBundleToBagExact`, `removeBagItemsExact`, `removeFromBag`, `moveBagItem`, `setBagItem`, `setBagItemsAtomic`, and `openGiftBagAtomic`.

The public methods keep their original signatures and response values; each holds the character mutex from its first slot read/validation through its DB writes and in-memory publication. The former bodies are now private `*Unfenced` implementations. For nested workflows, `addItemsToBagExactUnfenced` calls `addItemsToBagUnfenced`, and `openGiftBagAtomicUnfenced` calls `setBagItemsAtomicUnfenced`, avoiding reentry into the non-reentrant coroutine `Mutex`. There are no new packet formats, item rewards, gold changes, Treasure ownership grants, or implicit feature unlocks.

This reduces the likelihood that BAG operations overwrite each other during autosave/logout and staged transfer, and keeps rollbacks for existing exact/bundle operations under the same guard.

## Regression tests

- Three new `DvtTreasureBankCacheCoordinatorTest.kt` Kotlin tests hold a mocked Treasure SQL commit open, start concurrent BAG move, remove/set and bulk atomic changes, assert these operations do not complete while the character lock is held, then release the SQL commit and verify each sees the updated BAG quantity.
- `tools/test_dvt_treasure_core_bag_writers_lock_20261013.py` pins all nine public wrappers, unguarded-only nested private call sites, original icon and card-use safeguards, staged coordinator's identical lock and lack of runtime wiring.
- Full Python suite `python -m unittest discover -s tools -p test_dvt_*.py -v` and GitHub Server CI Kotlin/H2 must pass before considering DEV deployment.

## IMPORTANT remaining blockers

This patch **does NOT** make *every* inventory operation atomic and does not justify enabling Treasure transfers:
- Synchronous direct cache methods `setBagItemSync` and `addItemDirectSync` remain outside the coroutine mutex. The staged coordinator owns the lock before calling its sync publication helper; unrelated invocations may not.
- Other BAG mutation methods (equipment, BANK/STORAGE deposit/withdrawal, compound/claim partial paths and some external cache mutations) are not all fenced and may interleave. A follow-up should audit/wrap them carefully while avoiding reentrant lock deadlocks. Do not assume this single commit covers all item writes.
- `MoneySystem` maintains an independent gold cache and most modifications do not participate in `CharacterPersistenceGuard`. Real C123 level purchases are still disabled.
- Original DVT `specialAbility=245` banking request/reply semantics and `StarExp` rules are not proven from native dump; protect all 114 physical cards from generic consumption, but never mint S123 entitlements on a guessed packet.

The user's request for consolidated manual in-game testing remains deferred.
