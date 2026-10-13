# DVT Treasure ownership-source and cache consistency checkpoint — 2026-10-13

**Scope:** audited original acquisition sources, introduced a pure Kotlin card-item index and tests preventing accidental double counting. NO live Treasure ownership transfer, database schema migration, inventory/gold mutation, client asset edit, or DEV restart.

## Verified source semantics

Original client sources from both repo `lua` and `D:\Games\Ts-Offline\TSMobileOnLine\_native_dump_dvt115\_lua_client_decrypted`:
- `lua/Logic/Treasure.lua:Initialize` enumerates `itemDatas` with `EItemKind.Treasure=98` and uses `skillLink` as the canonical **Treasure ID**. Original `IOS_Item_C.dat` has precisely 114 such items: itemId `25601..25714` mapping one-to-one to treasure IDs `1..114`.
- `lua/Logic/Item.lua:Item.GetItemCount` reads `EThings.Bag` stacks. The bag copy quantity is used to count regular items, including materials `29869`/ `29870`; gold item `65351` is a **currency pseudo-item**, not a normal bag stack.
- `lua/Common/protocal.lua:S123-001` separately receives `treasureId, quant, level, star, exp` from server and sets `Treasure.treasureItems[id].count=quant`. `Treasure.GetCardCount` subtracts `TreasureLv.count` copies invested into earlier levels from **this banked quant**. It does NOT sum current ordinary bag item quantities.
- Original `C123` client only sends subKinds001/002/003; no verified C123 standalone bank/deposit message. Therefore it is **unsafe to treat an item appearing in the bag as having been transferred into Treasure ownership**, or to let both the bag and Treasure bank use the same card copy.
- Exhaustive scan of original 3,823-scene `gamedata/Eve.emg`, specifically `resultClass=1` item-result parameters, found **8,259 decoded class1 item-effect entries** but **no direct parameter match** with 114 Treasure card item IDs or 29869/29870. This is a scoped result; it **does not** exclude acquiring them from shop, boxes, mail, events, drop tables, or non-Eve result encodings. Do not conflate missing quest awards with impossibility to obtain cards.

## Verified cache overwrite blocker (not yet repaired)

The dormant `TreasureRepositoryImpl.purchaseLevel` updates `Inventories`, `CharacterMoney`, and `CharacterTreasures` together with Exposed SQL transaction and optimistic `expectedVersion`. Its SQL-level atomicity passed CI/H2; it is **still unsafe to expose as a live C123 endpoint**:

- `InventorySystem.bags` holds mutable per-character slot snapshots, and `InventorySystem.save/saveAndUnload` persists the *whole bag* via `inventoryRepo.saveAllItems`. An independent SQL debit can be overwritten by a later unchanged bag-cache snapshot.
- `MoneySystem.moneyMap` retains cached gold; `MoneySystem.saveMoney` writes it directly to the DB. A prior SQL debit can likewise be overwritten by a stale cached value. Its synchronous `addGold/subtractGold` do not acquire the coroutine `CharacterPersistenceGuard` shared by some other save paths. Adding a Mutex only to the Treasure handler is NOT sufficient for all writers.
- There is currently no single transaction/cache publication protocol shared by every inventory and currency writer, autosave and logout. **No account-level currency or bag change is activated until these are reconciled**. The existing Treasure C123-002/003 handler still rejects the operation without any debit.

## Pure source identity and ownership audit API added

`data/src/main/kotlin/tw/tsm/data/loaders/TreasureCardSourceIndex.kt` builds a strict and immutable `Item_C.kind98 + skillLink` ↔ `TreasureLv.id` bijection of 114 cards. It rejects missing cards, duplicate links, out-of-range links, or incorrect item kind. It exposes:
- `itemIdFor(treasureId)`, `treasureIdFor(itemId)` for verified original mapping.
- `unbankedBagCopies(bag, treasureId)`: bag-only diagnostic, never added to S123 bank quantity.
- `bankedSpendableCopies(state, treasureId, TreasureDatLoader.Catalog)`: standalone banked copies *minus original cumulative invested level copies*. If banked count is less than already invested copies, **fails closed** rather than allowing negative/phantom entitlement.
- NO SQL writes, no money mutation, no duplicate bank grant, no Treasure runtime registration. Even 125 unbanked copies cannot authorize a level purchase with 0 banked card copies.

Regression: `tests/.../DvtTreasureCardAcquisitionIsolationTest.kt` validates the 114 source items, exact bijection, bag-vs-bank isolation, cumulative invested copy math, and invalid/missing/duplicated item mapping. `tools/test_dvt_treasure_card_acquisition_isolation_20261013.py` and `tools/audit_dvt_treasure_card_acquisition_20261013.py` lock the original client semantics, missing explicit Eve item ID matches, cache-autosave blocker and deliberately unwired gameplay state.

## Gates before next live gameplay change

1. **Card acquisition:** locate an actual source-supported Treasure entitlement-grant path outside Eve (shops/boxes/mall, item-use result, event rewards). It must atomically *consume or transfer* physical items if needed so a copy cannot remain spendable twice. No new client RPC or forced banking semantics may be invented without source evidence.
2. **Online economy:** decide and implement one authoritative per-character commit/cache protocol for all bag/gold writers and autosave. All paths using synchronous `MoneySystem.addGold/subtractGold` and independent `InventorySystem` mutations need a coherency fence, otherwise SQL-only transactions still allow cache resurrection. Test two writers racing with autosave/logout as well as one successful offline SQL transaction.
3. **StarExp:** original `S123-003` sends star+byte progress, original UI submits multi-count1/5/10; no DVT RNG / threshold/carry policy has been established. Keep star updates disabled until native/server parity.
4. After these, load the source cost index through `TreasureDatLoader`, migrate `character_treasures` with an auditable schema migration, implement in-session economy synchronization, and then enable one C123 subkind at a time. Manual client E2E remains deferred to the owner's consolidated pass.

All existing DEV data, original `IOS_UiOpen_C.dat` unlock gates, original Eve and the live Java PID are untouched by this source-only batch.

## Source reward-origin follow-up: decoded actual BlissBag and Exchange tables

Subsequent exact packed decoders `tools/audit_dvt_treasure_reward_sources_20261013.py` and tests `tools/test_dvt_treasure_reward_sources_20261013.py` identified *actual* distribution definitions outside the Eve items branch (the rough two-byte scan alone was not used as proof):

- Original `BlissBagData.New` fixed record `<HHIIB`: bag containerId(U16), reward itemId(U16), quantity(U32), probability weight(U32), grouping kind(U8). `IOS_BlissBag_C.dat`: **37,257 records**, **175 valid Treasure kind98 card reward rows** from **27 distinct reward-container IDs**, covering **every one of the 114 Treasure item IDs**. Source example container **34617**, card item **25605**, quantity1, weight980, kind1. The probability weights have not yet been normalized or a specific player's bag-roll context verified; no reward probability was invented.
- Original `ExchangeData.New` fixed record `<HHH`: exchangeGroupId(U16), itemId(U16), quantity(U16). `IOS_Exchange_C.dat`: **5,036 records**, **62 Treasure card rows**, **62 different item IDs**, across **12 exchange group IDs**; example exchange group **44238** maps to item **25604** ×1. Definition existence does not establish a currently reachable exchange NPC or unlocked shop.
- Original `AchievementData.New` fixed row 37 bytes: **600 records**, with **zero original Treasure card itemId reward entries**. The earlier raw byte-pattern scan overestimated achievement matches because 16-bit matches can appear in unrelated fields, so these new parsed row results replace it.
- Kotlin `ItemHandler.handleUseItem` already handles GIFT_BOX by calling `inventorySystem.openGiftBagAtomic` and sending `sendSetBagItem` on success. That is a legitimate **BAG item grant**, not a `TreasureState.cardCopiesTotal` bank grant. Existing handler guarantees all-or-nothing with container consumption; no gameplay change in this batch.
- Conclusion: card **acquisition sources are now verified** in original static data; the remaining missing source evidence is the **bank/transfer mechanism** that turns an obtained bag item into the separate server S123 count. Never fabricate that transfer or mark a bag reward as automatically banked.

Follow-up tests pin all three packed distributions and all sample/coverage assertions. No live player item count has been altered.
