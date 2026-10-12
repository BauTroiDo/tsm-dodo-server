# C025 duplicate BAG index item-duplication prevention — 2026-10-13

## Found real live issue

In current Kotlin `TradeSystem.uploadItems` the `bagIndices` values received from `C025-002` are parsed as UInt8, but no uniqueness check was made. The old `TradeSystem.executeTrade` reconstructed `itemsA` and `itemsB` directly from the offered index list, then deleted the source slot and inserted each offered entry separately into the counterparty BAG. Sending duplicate indices like `[7, 7]` therefore could transfer the *same physical item stack twice* into the partner's BAG. The trade session synchronized monitor and both-character persistence mutex do not prevent this logical duplication.

## Minimal fail-closed correction

`TradeSystem.hasUniqueTradeBagSlots` enforces `indices.size == indices.toSet().size` and every index in `0..255` (original C025 U8). `uploadItems` rejects duplicates immediately before storing the proposed offer, using the existing `REASON_SYSTEM` C025 cancellation response. The final `executeTrade` revalidates **both** offer lists before reading/mutating either BAG so in-memory tampering and late forged states cannot bypass the protection. Original valid distinct index offers, NPC/quest drops, item-definition restrictions, level/mission-based UI gates and packet encoding are unchanged.

`DvtTradeDuplicateBagSlotSafetyTest.kt` verifies valid unique list, duplicate choices across positions and illegal out-of-wire ranges. `tools/test_dvt_trade_duplicate_bag_slot_guard_20261013.py` requires checks at *both* upload and final settlement, and confirms `CharacterPersistenceGuard.withLocks` still protects trade and the staged SQL atomic repository is not accidentally wired. DVT data audit CI also runs this test.

**Separate staged work:** `InventoryRepositoryImpl.commitTwoCharacterTradeAtomic` covers BAG+GOLD SQL debit for two players in one Exposed transaction with full before-state comparisons and rollback-on-error H2 tests, but is not yet wired to TradeSystem or deployed to DEV; the live trade still uses in-memory mutations followed by independent saves. Current `MoneySystem` mutators can bypass coroutine guards. Do not claim live cross-player SQL atomicity until proper cache publication and end-to-end verification exist.
