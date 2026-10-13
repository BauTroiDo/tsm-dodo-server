# DVT Treasure: six original binary tables, card ownership and safe persistence design
Date: 2026-10-12. Source: original `File.dat/Data-ios/IOS_Treasure*_C.dat`, `IOS_Item_C.dat`, and `lua/Data/Treasure*Data.lua`, `lua/Logic/Treasure.lua`, `lua/UI/UITreasure.lua`. This is a **read-only decode + regression + design milestone**; it does NOT enable live Treasure upgrades, create DB tables, debit player resources or restart the game.

## 1. Byte-exact original DVT data decoder

Script `tools/audit_dvt_treasure_tables_20261012.py`. Each file is `count(Int32 LE)` then `count` packed fixed-size rows; **no alignment padding**. Complete original SHA256/source invariants pinned by `tools/test_dvt_treasure_six_table_source_20261012.py`.

| Table suffix (IOS_*_C.dat) | Source struct | Count | Row bytes | Total bytes |
|---|---|---:|---:|---:|
| TreasureActivityCriteria | `<HIBH`: id, string, type, number | 86 | 9 | 778 |
| TreasureGroupInfo | `<HI6H`: id, string, six treasure IDs | 29 | 18 | 526 |
| TreasureGroupLV | `<HBHHHi`: id, level, environment, character, att, value | 290 | 13 | 3,774 |
| TreasureLv | `<HBHHHiBii`: id, level, environment, character, att, value, **cardCopies**, materialCount, moneyCount | 1,140 | 22 | 25,084 |
| TreasureRoleCriteria | `<HBBBBH`: id, role, element, turn, country, book ID | 18 | 8 | 148 |
| TreasureStar | `<HBHHHiii`: id, star, environment, character, att, value, materialCount, moneyCount | 684 | 21 | 14,368 |

All 114 IDs 1–114 occur exactly once per level 1–10 and exactly once per star 1–6. All 29 combinations have exactly 10 `TreasureGroupLV` rows. Original `TreasureGroupInfo` contains up to 6 treasure IDs and no invalid nonzero references; all six DVT table streams are consumed exactly and costs are nonnegative. ActivityCriteria IDs are NOT unique (e.g., ID5 has 65 rows): **do not load into a key-by-id map that drops conditions**. RoleCriteria 255/65535 are legitimate wildcard values, not missing-data errors.

## 2. Original card item mapping and concrete sample

Decoded all **26,296** `IOS_Item_C.dat` entries using the exact 54 numeric fields + UTF-16LE names/descriptions from `ItemData.New` / Kotlin `ItemDatLoader`. Exactly **114 items `EItemKind.Treasure=98`**, with distinct `skillLink` 1–114 matching 114 TreasureLv/Star IDs one-to-one. Card item IDs `25601–25714`, e.g. `25601/skillLink1 = Thiết kiếm`. This mapping must be persisted by original **skillLink**, not by icon index, name, or fragile UI ordering.

Source `UITreasure.UpdateStrength`:
- `TreasureLv[treasureId][nextLevel].count` is the number of *additional copies of that card* needed for that next level.
- `Treasure.GetCardCount` computes remaining copies as `Treasure.count - sum(TreasureLv[level].count, level=1..currentLevel)`.
- `itemInNeed[2].itemId=29869` is level-up material; the exact quantity is the next TreasureLv row `itemCount`; `itemInNeed[3].itemId=65351` denotes **money displayed by Lua** and the row's `moneyCount` is the gold charge. This is not a request to treat 65351 as a bag item.
- For ID1 going from Lv0→1: need cardCopies1, material29869×20, 100,000 gold. From Lv1→2: need additional cardCopies2, material29869×40, 200,000 gold. Card copies previously invested (1 after Lv1; 45 after Lv9) are calculated from original rows, not erased or refunded by this read-only parser.
- Lv10 is the cap (as `Treasure.MAX_LEVEL`).

Source `UITreasure.UpdateUpgrade`:
- Material **29870**, `TreasureStar[treasureId][star].itemCount` for each star row, plus corresponding `moneyCount`; maximum star **6**.
- **Do NOT assume a C123-003 request directly increments one star.** The Lua UI supports times1/5/10 and protocol S123-003 carries BOTH `Star` and `StarExp` (byte); `slider_Exp.maxValue=100`. The actual progress / carry-over / repeated material policy must be decoded with IL2CPP and reference behavior before any server debit, exp advance, or luck/random claim. `star_requirement` deliberately only reads the *next source row*, does not produce an executable upgrade.
- Static `environmentId`, `characterId`, `att`, `value` are conditions/bonus data; do not apply as unconditional global attributes or concatenate all group levels.

## 3. Persistence model proposed (NOT migrated or deployed)

A proposed Exposed table `character_treasures` with composite key `(character_id BIGINT, treasure_id SMALLINT UNSIGNED)`:
- `card_copies_total INT NOT NULL DEFAULT 0`: how many kind98 cards the player legitimately owns/has banked in the Treasure system, distinct from already invested card copies. Determine the canonical source before accepting future acquisitions. Prevent duplicate ownership between `inventories` and Treasure.
- `level TINYINT NOT NULL DEFAULT 0`: valid0–10.
- `star TINYINT NOT NULL DEFAULT 0`: valid0–6.
- `star_exp TINYINT NOT NULL DEFAULT 0`: wire byte progress, nominal UI0–100; don't assume true cap semantics before IL2CPP parity.
- `version BIGINT NOT NULL DEFAULT 0` and `updated_at_ms BIGINT`: optimistic concurrency and audit trail.
- Additional durable *transaction receipts* `character_treasure_operations` with `character_id, operation_uuid/nonce, treasure_id, action, requested_count, from_version, to_version, consumed_cards, material_id, consumed_materials, consumed_gold, recorded_at` if the client protocol can provide/use a stable nonce. **Original C123-002/003 has no operation ID**; a simple receipt keyed only by packet fields would reject legitimate repeated operations. Sequential per-character transaction serialization prevents concurrent double charge; true retransmit idempotency needs an agreed request identity or reconciliation mechanism, not a fabricated guarantee.
- A `character_treasures` row should **not** be created merely on C123-001 (read-only). Zero-state snapshot can omit untouched IDs as the original client initializes zero values itself.

### Atomic upgrade critical path, to implement only after star/ownership semantics are confirmed

1. Verify `IN_GAME`, legit original **mission11804/bit936**, actual Treasure item ID derived from `Item_C.kind98.skillLink`, valid count1 for C123-002, validated original 8-byte packet; reject malformed/out-of-range requests before touching resources.
2. Acquire a single per-character coordinator lock shared with normal `InventorySystem` / `MoneySystem`; perform Exposed SQL transaction with character and relevant inventory/item rows locked (or compare-and-swap with affected-row-count checks). Require `fromVersion` and validate level, star, exact source curve/cap, possession, material29869 or 29870 and gold balance. Client-supplied bag index must match **the actual correct bag slot and item ID**; never charge by only an untrusted index.
3. Debit the exact item stacks, card entitlement (without double counting invested copies), and `CharacterMoney.gold` within **the same database transaction as the level/star/progress update**. Reject and roll back the *entire* action if any component fails. The repo currently persists `Inventories` and `CharacterMoney` separately and `MoneySystem` caches money online; calling separate subsystem mutators sequentially is NOT a transaction.
4. Once committed, synchronize in-memory inventory/gold cache, push original inventory and gold packets to the client and finally original `S123-002/003` success with persisted new values. Recompute and broadcast conditional Treasure + combination bonuses exactly once, only where original attribute conditions match.
5. On failure, no item/gold/card/bonus mutation; use correct original `S123-002/003` error kind. Log auditable transaction outcome without leaking private character data.

Do **not** enable C123-002/003 progression or write migration on DEV until both the character card-acquisition path and StarExp policy are decoded and transaction integration is testable.

## 4. Test and deployment status

- Source parser: six exact shapes and hashes, 2,247 total packed Treasure rows (86+29+290+1,140+18+684), full 114-card `IOS_Item_C` cross references.
- New regression tests: exact SHA/count/row size, all levels and stars present, valid group foreign keys, nonnegative cost bounds, full item-card index coverage, next-level read-only cost planner, star source row access only, corrupted/truncated file rejection, original Lua material IDs and cumulative card semantics.
- Existing `TreasureHandler` from prior commit `9526125` remains **read-only**: C123-001 count0 when legitimately unlocked; C123-002/003 safe rejects. **No game server restart, no DB writes, no client asset changes in this batch**.
- Remaining next step: implement Kotlin `TreasureDatLoader` + immutable definitions; model guarded persistence and one rollback-safe SQL transaction; first establish StarExp progression in `_native_dump_dvt115` / Lua and card ownership source. Manual test of an actual character is deferred to the requested end-of-project consolidated E2E pass.

## Kotlin source decoder added

Additionally `data/src/main/kotlin/tw/tsm/data/loaders/TreasureDatLoader.kt` now implements **the same six packed formats** as a pure, read-only Kotlin source loader (`TreasureDatLoader.load`). It validates source row counts, unique 114 × 10 levels and 114 × 6 stars, 29 valid combination groups, 290 group-level rows, and nonnegative source cost values. The pure `nextLevel`, `investedCopies` and `nextStarData` accessors do not debit resources or update progress. `DvtTreasureSixTablesLoaderTest.kt` adds Kotlin regression for exact data shapes, representative rows/costs, card-copy math, malformed/truncated input and duplicate level rows. No `GameDataManager` registration or database migration has been added; further feature implementation can opt into the loader after the state-transition design is approved by source tests.
