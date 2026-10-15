# Staged DVT Treasure per-character persistence and atomic level transaction — 2026-10-12

Status: **implemented as dormant Kotlin library and SQL test fixture only**. NOT registered in `ServerModule`, `DatabaseFactory`, or `TreasureHandler`. No production database creation, inventory/gold change, or client upgrade enabled. The existing DEV C123-001 read-only UI and C123-002/003 fail-closed behavior remain unchanged.

## Source facts and unsolved StarExp

Previous checkpoint `docs/dvt-treasure-six-original-tables-and-persistence-design-20261012.md` validated all six source tables, 114 original card items `25601–25714`, 29 groups, 10 original levels, 6 original star rows per card. Original `Treasure.GetCardCount` subtracts cumulative already invested card copies. Source `UITreasure` displays levels, costs in card copies, item **29869** and gold; original C123-002 sends treasureId(U16), count(I32), actual material bag index(U16). Original C123-003 uses item **29870**, repeated attempts (1, 5, 10), and server answer S123-003 includes both star and starExp. **The exact RNG and StarExp threshold/carry-over semantics are not proven by the static tables or Lua receiver**. Native reference `D:\Games\Ts-Offline\TSMobileOnLine\_native_dump_dvt115` contains `dump.cs` plus decrypted Lua, but no verified server-side StarExp state-transition formula has yet been established. For this reason **star changes are forbidden** in this batch.

## New, dormant schema and repository

- `domain/.../TreasureRepository.kt` declares immutable `TreasureState` and `TreasureLevelRequirement`. Values are validated (card ID1..114, level0..10, star0..6, exp0..255 (full original wire byte; UI slider 0..100 is NOT proof of stored-cap rules), nonnegative card total/version, positive costs). `TreasureRepository.purchaseLevel` requires caller-supplied character ID, treasure ID, **expected level and version**, and selected material BAG slot.
- `persistence/tables/CharacterTreasures.kt` declares `character_treasures` with primary key `(character_id,treasure_id)`, `card_copies_total`, `level`, `star`, `star_exp`, `version`, and `updated_at_ms`. Absent row indicates no proven Treasure acquisition: **read and purchase do not create one**.
- `persistence/repositories/TreasureRepositoryImpl.kt` accepts only an immutable server-injected map of original source level costs; it never accepts card/material/gold cost from the incoming packet. No default cost exists: unknown card/level **fails closed**. The only successful route requires already banked original card copies, correct current level and version, a real `Inventories` bag storage1 row at the requested slot containing `item29869`, and a `CharacterMoney` row with sufficient gold.
- Within **one `suspendTransaction`**, lock the CharacterTreasure, inventory-slot and character-money rows with `SELECT...FOR UPDATE`, debit exact remaining material quantity or delete empty stack, debit gold, then advance `level/version`. Every update/delete must affect exactly one row; any failure causes SQL transaction rollback. Multiple identical requests with the same old version/level cannot charge twice. No money, item, NPC, battle or skill benefit is granted in this code.
- The current character gold/inventory services maintain **independent online caches**, and do not share a coordinator with this repository. **DO NOT wire `purchaseLevel` to `C123-002` until a shared cache synchronization and source card acquisition path are implemented**. SQL transactional safety here does not imply live gameplay transaction safety yet. No `StarExp` or direct star purchase API is exposed, intentionally.
- No in-game artificial grant and no manual mutation of the DEV player DB was performed.

## Regression tests

`tests/.../DvtTreasureAtomicPersistenceRegressionTest.kt` uses a fresh isolated H2 MySQL-mode database per test, explicitly creates only the three test tables, seeds fixture ownership and gold **inside test DB only**, and checks:
1. Valid original level0→1→2 progression debits exact amounts and advances version; replaying the same state/level has no second debit.
2. Wrong bag slot or wrong item29870 cannot substitute original level-up material29869.
3. Too few cards/material/gold prevents partial deductions.
4. A missing ownership row cannot be minted by clicking upgrade.
5. Unknown source level/ID, wrong version and invalid slot fail closed.
6. A fully exhausted material stack is deleted only when the transaction commits.
7. H2 intentionally rejects the FINAL level UPDATE with a CHECK constraint, AFTER the inventory and gold UPDATEs have executed; all three state values and their version must roll back to their pre-transaction values.
8. Full StarExp wire-byte validation (0..255) without falsely assuming a stored 100-point cap.
9. Mismatched trusted source cost-map keys are rejected.
10. No Treasure repository is wired to startup and no automatic schema migration is installed.

This is a foundation for a source-complete implementation, not a claim that Treasure can already be leveled in the game. End-to-end testing with a real character remains deferred as previously requested.
