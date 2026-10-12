# DVT Treasure staged physical-card-to-bank transaction — 2026-10-13

Status **dormant, not deployed to DEV and not wired to C023-015 or C123**. Production game still protects all 114 Treasure direct-use items, retains them in the bag and reports no fake success. This checkpoint creates *only* a strict SQL-level transfer primitive and H2 tests, not a claim about full original 245 ability semantics.

## Findings from source

- Source `IOS_Item_C.dat` all 114 kind98 cards (item IDs25601–25714, `skillLink=1..114`) have `specialAbility=245` and `directUse=1`; actual `EItemUseKind` enumeration in original Lua `lua/Data/ItemData.lua` stops at named Soul240, SoulPoint243, GodskinkStone246, ActivityModelCoin247 without a named 245 entry. This **does not prove** what the original server does when C023-015 is sent.
- Original Lua `Treasure.GetCardCount` reads bank count from `S123-001` and subtracts copies already invested in level gains. Lua `Item.GetItemCount` independently counts ordinary BAG stacks. Under no circumstances can a physical card count be double counted without an atomic transfer.
- Source BlissBag defines 175 actual kind98 card reward rows covering all114 card items; Exchange defines62. Kotlin `ItemHandler` delivers ordinary BlissBag rewards to BAG via existing `InventorySystem.openGiftBagAtomic`; it does NOT automatically grant S123 ownership.
- `CharacterPersistenceGuard` currently serializes some autosave paths, but most ordinary BAG writers and synchronous `MoneySystem` writers do not acquire a shared lock. Therefore a SQL debit via an independently wired repository can be **overwritten by stale online snapshots** even though the SQL transaction itself was atomic.

## Staged strict SQL primitive

Added `TreasureRepository.bankCard(characterId,treasureId,expectedVersion,cardBagSlot,quantity)`. `TreasureRepositoryImpl` adds source-only `originalCardItemIds: Map<Int,Int>`, **default empty to disable banking**. Every requested item ID is determined by the trusted source map and must match the concrete locked `Inventories` row at BAG storage1 and selected slot. Reject invalid IDs/slots/version/count, wrong item, insufficient stock and Int overflow. Source map requires distinct item values and Treasure IDs1–114. The repository does not take arbitrary client-supplied material/cost data, has no grant shortcut, and never credits S123 without first decrementing/deleting the corresponding physical card stack in **the same Exposed `suspendTransaction`**.
- First verified transfer creates row `character_treasures` with total count, level0, star0, exp0, version1; **never** on login, C123-001 or a read. Existing row increments cards and version with optimistic compare-and-swap. A repeated request with the old expectedVersion cannot debit a second time.
- Source contents are **not itself permission** to activate transfer. The original server's ability245 protocol response, ownership scope and transfer trigger must be confirmed before C023-015 can call it.
- No currency changes, level increases, StarExp advances, bonus attributes, client network packets, unlock flags, DB migration, automatic server wiring or character data mutations have been introduced.

## Tests

New `tests/src/test/kotlin/tw/tsm/tests/DvtTreasureCardBankTransactionTest.kt`: isolated per-test H2/MySQL-mode database; first transfer creates legitimate row, repeated versions rejected, further correct version transfers decrement exact physical stack, full stack removal, wrong kind/slot/cardId/quantity rejected, missing trusted source map blocks all banking, injected final SQL insert/update constraint failure rolls back prior bag debit, all 114 trusted Item_C links asserted. No DEV character used.

New `tools/test_dvt_treasure_bank_staged_atomic_20261013.py` pins the isolation guard, original Lua unknown 245 semantics, H2 tests and unwired runtime state. Updated prior `test_dvt_treasure_dormant_transaction_20261012.py` to require that **only bankCard** may insert a new Treasure row while `purchaseLevel` cannot create ownership. CI DVT audit workflow gains a dedicated source isolation step.

## Preconditions for real integration

1. Determine authentic semantics for `C023-015` kind98/245 and whether it is the explicit S123 bank event. Build correct `S123`/bag response and fallback from source before gameplay wiring.
2. Coordinate `InventorySystem.bags` changes with the same SQL commit, all writer mutation paths, periodic autosave and logout snapshot. Otherwise cache divergence and item resurrection remain possible.
3. Migrate the dormant `character_treasures` only with versioned safe schema migration; verify existing live character inventories and ownership migration behavior.
4. Only then enable bank transfers and then independently unlock level upgrades once `MoneySystem` coherent commit is implemented; star progression needs verified original StarExp rules.

Use developer-requested single final in-game E2E later; do not claim client click testing in this stage.
