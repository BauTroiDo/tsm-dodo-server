# DVT Treasure direct-use kind98 item-loss prevention — 2026-10-13

## Source-backed bug

Decoding original `IOS_Item_C.dat` exactly via `ItemData.New` field widths confirms **all 114 original DVT Treasure cards** (item IDs `25601–25714`, `skillLink=1..114`) have the same:
- `kind=98` (source `EItemKind.Treasure`);
- `specialAbility=245`;
- `directUse=1`;
- `fitType=0`.

They are legitimately present in reward pools: 175 source `IOS_BlissBag_C.dat` rows spanning 27 containers, and 62 `IOS_Exchange_C.dat` rows. The client can submit the generic direct-use item request `C023-015`; Kotlin `ItemHandler.handleUseItem` previously fell through `ItemUseProcessor.processUseItem` because neither class covered ability245 / kind98. `processGenericAttrItem` ends with `ItemUseResult(success=true)` and `consumed=true` even if no supported player attribute changes are applied. `ItemHandler` interprets it as success and calls `InventorySystem.removeFromBag`, **destroying the Treasure card without recording any S123 banked entitlement**.

This bug affects normal character use and forged `followIndex>0` attempts; sending a card to the pet processor can likewise mark it consumed, so the fail-closed guard must come **before** the pet use branch.

## Minimal Kotlin fix

In `gamelogic/src/main/kotlin/tw/tsm/gamelogic/systems/ItemUseProcessor.kt`, immediately after verifying a source item definition exists and before any runtime/pet mutation:

```kotlin
if (def.kind == 98) {
    logger.warn { "Treasure card use blocked before ownership transfer: itemId=$itemId" }
    return ItemUseResult(success = false, consumed = false)
}
```

**No card, item, gold or TreasureState mutation** occurs. `ItemHandler` already returns immediately on `!result.consumed`; therefore the card **stays in the bag**. No source-unverified automatic banking, no early icon/mission unlock, no client Lua edits, no changes to other item kinds. Generic BlissBag opening still goes through `GIFT_BOX` and the existing `openGiftBagAtomic` branch. Existing Soul240 handling and other special-use item routes remain unchanged.

The rule intentionally matches item kind98 irrespective of specialAbility, so altered/malformed treasure ItemData cannot fall through and lose a card.

## Regression coverage

- `tests/src/test/kotlin/tw/tsm/tests/DvtTreasureCardItemUseSafetyRegressionTest.kt` exercises **all 114 original kind98 items** directly from original Item_C, verifies their ability245 and directUse1, and verifies no consumption, no success, no phantom attributes/rewards for both normal-character and pet-use requests. Further tests ensure a corrupted kind98 ability cannot bypass protection, and Soul/BlissBag use categories are not swept into it.
- `tools/audit_dvt_treasure_card_direct_use_20261013.py` decodes packed original Item_C fields, `tools/test_dvt_treasure_card_direct_use_safety_20261013.py` proves source coverage, original Lua client separation, server guard placement and ongoing gift-box behavior. CI workflow DVT data audit gains a dedicated step.
- Full DVT Python and GitHub Kotlin Server CI must PASS before any DEV deploy. Guarded DEV deployment compares old/new exact SHA, backs up original source, compiles `:app:classes` without stopping current Java, then restarts only after successful compile, and verifies PID/6613.

## What this does **not** do

- No ownership transfer from bag to TreasureState. Original C123 currently sends no verified explicit deposit packet. The old source S123-001 count still remains separate from bag quantity.
- No level/star purchase enabled. In-session currency/bag caches are independently mutable and could overwrite direct SQL debits; `TreasureRepositoryImpl.purchaseLevel` remains unwired, and `C123-002/003` still reject.
- End-to-end character test is reserved for the owner's final consolidated in-game run, as requested.
