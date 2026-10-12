# DVT Inventory multi-container writer fence — 2026-10-13

## Goal and changed code

After the nine core BAG writers were serialized in `eb90684`, six remaining item-transfer methods could still mutate the same in-memory BAG while `TreasureBankCacheCoordinator.stageBank` committed a card debit and published its new slot quantity. These paths also persisted EQUIP/BANK/STORAGE slots while autosave/logout worked on container snapshots.

Updated `gamelogic/src/main/kotlin/tw/tsm/gamelogic/systems/InventorySystem.kt` to retain original public signatures but wrap **eight additional suspend methods** with `CharacterPersistenceGuard.withLock(characterId)`:
- `equipItem` and `unequipItem` (BAG↔EQUIP)
- `depositToBank`, `withdrawFromBank` (BAG↔BANK)
- `depositToStorage`, `withdrawFromStorage` (BAG↔STORAGE)
- `addRewardsToBagExact` (entire batch reward validation, write and rollback)
- `commitBattlePassClaim` (claim SQL update, BAG cache and progress publish).

Each complete prior method body is preserved as private `NameUnfenced`; the public coroutine wrapper holds the same per-character lock used by autosave, logout, the nine core BAG writers and the dormant TreasureBankCacheCoordinator. To prevent non-reentrant deadlock, `addRewardsToBagExactUnfenced` now calls `addItemsToBagUnfenced` inside the already-acquired lock.

No new packet formats, original Lua modifications, Treasure bank credit, SQL migrations, NPC reward changes, level/star upgrades or UI feature unlocks.

## Regression coverage

- New `tools/test_dvt_treasure_equipment_bank_storage_lock_20261013.py` checks all eight public guards + private implementations, cross-lock protection, nested reward reentry and lack of C123/C023 banking activation; linked in `.github/workflows/dvt-data-audit.yml`.
- Extended `DvtTreasureBankCacheCoordinatorTest.kt` with two real `InventorySystem` races: `depositToBank` cannot move the original 5 BAG cards while a staged S123 physical-card debit is awaiting SQL commit; `withdrawFromBank` and autosave wait for the same commit before reading/writing BAG. Tests verify final BAG/BANK quantities, not just mutex counts. A fixture supplies a realistic capacity of 100 empty-slot locations, instead of MockK's default zero.
- CI Server Kotlin/H2 + DVT data audit must pass before guarded DEV deployment. Original DVT `UiOpen_C.dat` level/mission gates are not to be changed.

## Remaining blockers — NOT ready to enable Treasure banking or upgrades

1. `InventorySystem.setBagItemSync` and `addItemDirectSync` synchronous writers have no implicit coroutine mutex. Some upstream systems may call them without a guard; no claim is made about total BAG safety.
2. Multiple inventory operations and external systems can still violate cross-system atomicity at failure boundaries. Applying the coroutine mutex prevents races but does **not** make separate BAG/EQUIP/BANK/STORAGE `saveItem` calls one SQL transaction. Refactoring all storage transfers to atomic multi-container SQL commits requires a separate validated change.
3. `MoneySystem` continues to cache gold independently and does not coordinate currency writers with the Treasure transaction; C123 level/star upgrades remain rejected.
4. The source-native DVT ability245 bank/transfer event and StarExp progression are still not proven; physical-card use for kind98 remains safe rejection, with no arbitrary ownership grants.

Player-driven consolidated E2E remains deliberately deferred, as requested. No live DB migration or direct player-data mutation in this batch.
