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

## Verified DEV deployment — 2026-10-13 01:47 ICT

- Fix commit `581b55d66186ec54eb048ed8da57c4cb85f93bc5`, pushed to PR#41 branch `fix/dvt-quest-nav-parity-20261011`.
- [Server CI run `38292974716`](https://github.com/BauTroiDo/tsm-dodo-server/actions/runs/38292974716) **PASS**; [DVT data audit run `38292974755`](https://github.com/BauTroiDo/tsm-dodo-server/actions/runs/38292974755) **PASS**; local Python DVT suite **213/213 PASS**.
- SHA-guarded remote `tsm-prod` DEPLOY. Original DEV `ItemUseProcessor.kt` SHA256 `d20e765a66c1abef6b73f6c166d6678ae1f2e84112f189cf0306cdde7dc4fae3` matched Git exact preimage; staged/post-deploy `68d9f8e8d32a1a09fd9884110d5ba0ba62300408a4e3e84b9bb8745d1209a601` matched Git candidate. No changes to other Kotlin, client Lua, original Eve, quest rewards or item files; **no git reset/checkout on DEV's dirty repository**.
- Backup `C:\Server-Game\tsm_dodo\backups\dvt_treasure_card_use_581b55d_20261013_014707\ItemUseProcessor.kt`. Deploy script `C:\Users\Administrator\_deploy_dvt_treasure_card_use_581b55d.ps1` retains original preimage and backup SHA guard.
- DEV Gradle `:app:classes` **BUILD SUCCESSFUL in 25s** before restart; verified compiled `gamelogic/build/classes/kotlin/main/tw/tsm/gamelogic/systems/ItemUseProcessor.class` contains `Treasure card use blocked before ownership transfer`. Old Java game PID `11040`; new PID `11072`, TCP `6613 LISTENING` on IPv4 and IPv6. Runtime startup log `C:\Server-Game\tsm_dodo\logs\runtime-production-dvt-treasure-card-use-581b55d.log` reports ready at 01:47:43 local ICT.
- Independently verified original DEV `File.dat/Data/UiOpen_C.dat` SHA256 unchanged: `af759749cf6d33081df7de99f715cf682e324bb48fb0ff4c57edb932a78c8b4e`; quest-level feature unlock gates remain source-correct.
- Manual card-use check through client still deferred for owner's final combined E2E; **only automation/source tests and active deployment are claimed**, not a manually clicked UI PASS.

### Next real blockers

1. DVT source `S123-001` bank-count changes are not explained by C123-001/002/003 alone. Even though original BlissBag and Exchange distribute card items, depositing/crediting them to TreasureState is **not source-decoded**. No card grant/automatic bank conversion was added.
2. `InventorySystem` and `MoneySystem` still keep independent online caches that may overwrite TreasureRepository direct-SQL decrements on autosave. The dormant `purchaseLevel` remains unwired and production DB migration remains disabled.
3. `StarExp` multiple-roll probability/carry rules are still not verified from native/server reference. C123-003 remains a safe rejection. Do not activate live level/star purchases until blockers are resolved and tested.
