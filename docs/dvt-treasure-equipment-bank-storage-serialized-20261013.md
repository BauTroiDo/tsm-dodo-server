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

## Verified DEV rollout — 2026-10-13 03:04 Asia/Ho_Chi_Minh

- GitHub code commit `224fa6aa4446c6ab82fca9ed34af8e70843a48a5`, PR #41 branch `fix/dvt-quest-nav-parity-20261011`. Server CI run [38301421947](https://github.com/BauTroiDo/tsm-dodo-server/actions/runs/38301421947) **PASS**, DVT data audit run [38301422043](https://github.com/BauTroiDo/tsm-dodo-server/actions/runs/38301422043) **PASS**, Python DVT complete regression **233/233 PASS**.
- Guarded SSH `tsm-prod` deployment patched only `gamelogic/src/main/kotlin/tw/tsm/gamelogic/systems/InventorySystem.kt`; source SHA before `db0091b1529fca82c1daaf1b07ca8cc8a28bbd23ebc3cdd67033efd0c40a7031`, after `502936573bcf18ac6f9bb55e0f1fd0ff482d97b911ec05f26142fe83781a1333`.
- Original source backup `C:\Server-Game\tsm_dodo\backups\dvt_multi_container_lock_224fa6a_20261013_030325\InventorySystem.kt`. No remote git reset/checkout/pull of the divergent DEV source tree.
- DEV `:app:classes` **BUILD SUCCESSFUL in 55s** before restarting the old game. Compiled `InventorySystem.class` confirmed `equipItemUnfenced`, `depositToBankUnfenced`, `withdrawFromStorageUnfenced`, and `addRewardsToBagExactUnfenced`. Old Java PID `11224`; new server Java **PID `12260`** on port `6613` listening, main process log ready at **03:04:30**. Runtime log `C:\Server-Game\tsm_dodo\logs\runtime-production-dvt-multi-container-224fa6a.log`.
- Canonical `File.dat/Data/UiOpen_C.dat` SHA after deployment is still `af759749cf6d33081df7de99f715cf682e324bb48fb0ff4c57edb932a78c8b4e`. Original DVT level/mission-based icon gates untouched, game data and client files unchanged. No DB migration, no player resource adjustments and NO enablement of physical-card banking, C123 level/star purchases or StarExp updates.
- Continuing limitations: synchronous `setBagItemSync`, `addItemDirectSync`, independent `MoneySystem` gold cache, multistep inventory SQL updates and missing original `specialAbility=245` server entitlement semantics still block live Treasure banking and upgrades. Manual in-game player E2E deferred to the requested consolidated final pass.
