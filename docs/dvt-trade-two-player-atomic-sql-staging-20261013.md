# Staged two-player BAG and gold SQL transaction — 2026-10-13

**Scope: source-only repository transaction and rollback regression. NOT wired to C025 handlers and NOT deployed to DEV.** Original DVT Lua, `UiOpen_C.dat`, quests, item tables, current game Java runtime and player inventories remain untouched.

## Verified current blocker

The existing Kotlin `TradeSystem.executeTrade` synchronously transfers player A/B's BAG slots via `InventorySystem.setBagItemSync`/`addItemDirectSync`, then adjusts cached gold through `MoneySystem.subtractGold/addGold`. The previous 2-player persistence fence prevents parallel autosave/logout inside that trade, but **cannot rollback both SQL players after a partial subsequent failure**, because `InventorySystem` and `MoneySystem` independently save data. Neither a coroutine mutex nor a `synchronized(trade)` monitor is a database transaction.

## Dormant SQL primitive

Added `TradeAccountSnapshot` and `InventoryRepository.commitTwoCharacterTradeAtomic(first,second)`. Interface default throws `Atomic trade integration is not enabled`, so mock repositories and live call sites cannot accidentally gain a success-by-default new feature.

`InventoryRepositoryImpl` overrides with one `suspendTransaction` covering *both* character BAG snapshots and *both* GOLD rows. Requires two distinct positive character IDs, valid source BAG slots, positive source item IDs/quantities, bounded nonnegative gold, same sum of pre/post gold, and exact conservation of the multiset of `ThingData` (including Myth, damage, affix, expiration and all fields except stack quantity). Sides are processed in ascending character ID.

Before *any write*, it locks the two stored `character_money` rows and the two BAG sets with `forUpdate`, validating every original `ThingData` and exact gold/auctionGold against the supplied before-state. An out-of-date or forged snapshot returns false with no writes, and successful transfers replace both stored BAGs plus both gold balances in the **same SQL transaction**. Exposed rolls back all changes on an SQL constraint failure. No Treasure bank state, pet ownership or real inventory events are affected.

**Limit:** This is not yet an end-to-end cross-player trade guarantee. Current TradeSystem C025 is still synchronous RAM-only; before wiring the SQL primitive, its cache mutation/packet publication must be moved *after* verified DB commit, with both players' snapshots under `CharacterPersistenceGuard.withLocks`, and a failure must notify clients using source C025 error messages. The current source ability245 and Treasure C123 transfer remain unresolved.

## Tests and CI

`DvtTradeTwoPlayerAtomicRollbackTest.kt` uses an independent H2 MySQL-mode DB per test and asserts: successful balanced two-player exchange of entire `ThingData` stacks and gold, replay rejected, SQL CHECK constraint on recipient-side insert rolls back already-attempted source-side changes + gold, forged item quantities/metadata and imbalanced/negative gold rejected, stale DB snapshots fail closed without mutation.

`tools/test_dvt_trade_two_character_sql_atomic_staged_20261013.py` pins source contracts, required rollback cases, full metadata preservation, and explicitly confirms C025 and Treasure handlers remain unwired. Add to DVT data audit and Kotlin Server CI before any deployment.

User requested one consolidated client-character E2E only after completing the repair sequence.
