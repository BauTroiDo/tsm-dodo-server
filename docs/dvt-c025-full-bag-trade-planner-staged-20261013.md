# DVT C025 full-BAG two-player SQL trade planner — 2026-10-13

## What changed
**Staged source and tests only. NO active C025 SQL transfer, NO DEV redeploy, NO JVM restart, NO new packet or database migration.** Continuing from `49faa602cef83b86df37d8704f9bbce83ceabdb8` (finalization gate), this milestone creates the pure `TradeItemSnapshotPlanner.kt` that constructs TWO complete, immutable before/after `TradeAccountSnapshot` records, suitable for the existing dormant `InventoryRepository.commitTwoCharacterTradeAtomic` and `TradeCacheSettlementCoordinator.stageCommit`.

### Source-confirmed root observations
- Live `TradeSystem.countEmptyBagSlots` loops only `ItemConstants.INIT_BAG_SLOTS = 250`. Real `RoleCountSystem.getBagCapacity(characterId)` can expand to `ItemConstants.MAX_BAG_SLOTS = 500`. Thus a player with an available BAG slot above 249 can still be incorrectly told the destination bag is full by legacy item trade.
- Live C025 still transfers items using multiple synchronous `InventorySystem.setBagItemSync` / `addItemDirectSync` operations and uses `MoneySystem.subtractGold/addGold`. Database persistence happens separately. Its `REASON_SUCCESS = 4` packet follows RAM changes, not a two-character SQL commit.
- The repository's **staged, already-tested** two-player SQL primitive checks complete BAG snapshots, preserves the exact `ThingData` record including myth attributes, verifies gold conservation, checks DB rows under SQL locks, and rolls back if writes fail. The planner must produce exactly this input without touching RAM or DB.

### Planner contract
- Caller must hold the **same two-character persistence guard** for the entire read/plan/SQL/cache-publish boundary and a safe synchronous gold fence; the planner alone is **not** a concurrency lock.
- C025-002 upload must record the exact **expected selected item snapshot** for each chosen BAG slot and its gold offer. Finalization supplies current **full loaded BAG** snapshots, `MoneySystem.getLoadedMoney`, current dynamic bag capacity and both original offers. Missing/mismatched snapshots fail closed. This prevents substituting another item/NFT enchantment after the partner saw the offer.
- Fail closed if duplicate selected slots, 1-byte offered slot outside 0..255, nonexistent/invalid slot, invalid BAG capacity or quantity, stale Myth/affix/damage, insufficient gold, or receiving gold over max.
- Fail closed on a locked item; same-guild item types (`ItemDef.kind = 53` / `restrict2 & 1`) while org membership parity is unavailable; and any `restrict & 2` transfer ban including `specialKind & 1`, since one-time trade consumption semantics remain unproven. This is intentionally stricter than the current client for special one-time items until the original DVT mechanism is implemented.
- Both parties' selected slots are removed from **copies** before calculating placement. Incoming whole stacks are placed into the first free slot within the **actual dynamic capacity**, without merging, splitting or recreating `ThingData`. Metadata (damage, affixes, expiry, locks, myth arrays, quantity) must match exactly. If any destination has insufficient slots, produce `Rejected(BAG_FULL)` without changing either side.
- Currency is calculated using 64-bit arithmetic: `newA = oldA - offerA + offerB`, `newB = oldB - offerB + offerA`. `auctionGold` stays unchanged. A final conservation check compares the combined total quantity keyed by complete `ThingData` metadata and both players' total gold.
- Produces two full `TradeAccountSnapshot` values for optimistic DB compare-and-commit, or a typed `Rejected` reason; no side effects, no SQL, no cache writes and no success packet.

### Tests and release gate
- Kotlin JUnit `DvtTradeItemSnapshotPlannerTest.kt`: 11 scenarios for both-full bag swap, expanded capacity, full NFT metadata transfer, duplicate/stale/untrusted offers, invalid slot/quantity/capacity, overflow/insufficient gold, full recipient bag, blocked guild/one-time restrictions, invalid participants, and no-op preservation.
- Python source contract `tools/test_dvt_trade_item_full_bag_planner_20261013.py` registered in DVT data audit workflow: asserts no live C025 wiring, exact planner invariant checks, and Kotlin cases.
- Confirm GitHub **Server CI and DVT audit** on the exact commit SHA; local Windows PC may not have JDK. Do not claim passing Kotlin tests merely from passing Python tests.

### Remaining blockers before safe live C025 activation
1. Capture original C025-002 offered items and reject stale/changed full BAG snapshots under the two-character lock; a read before acquiring the lock is not safe. The current live offer stores only BAG indices and gold, not frozen ThingData metadata.
2. Stop the legacy synchronous C025 `executeTrade` path from emitting success before SQL commit. Wire `TradeItemFinalizationGate` to the planner and `TradeCacheSettlementCoordinator` **outside any JVM synchronized monitor**. Ensure one active settlement per pair, prevent concurrent uploads/cancel racing with settling, and map typed failures to original DVT reasons.
3. Add durable trade operation IDs/reconciliation for ambiguous SQL outcomes: a lost ACK/transport timeout cannot be treated as rollback. Avoid blind retry/duplicate debit, including across process restarts.
4. Audit remaining direct BAG writers, especially synchronous mutations outside `CharacterPersistenceGuard`, while retaining compatibility with quest/Eve and EXP-slot flows. Some EXP Slot/Treasure code already holds the guard; Treasure banking remains dormant.
5. Pet trade (C025-012) has its own separate owner/pet/state rollback; do not assume this item planner covers it.
6. Client-character manual playtest remains postponed until consolidated end-to-end validation as requested.

### Files / workspaces
- GitHub PR #41 branch `fix/dvt-quest-nav-parity-20261011`.
- Clean isolated worktree `D:\Games\Ts-Offline\_dvt_trade_gate_20261013`, created from previous checkpoint; preserve dirty `D:\Games\Ts-Offline\_dvt_nav_batch_20261012`.
- Running DEV `C:\Server-Game\tsm_dodo` unchanged by this planner. Last verified game PID `12184`, TCP `6613`, MySQL initialized, 984 missions / 4505 steps (prior step).
- Previous safe DEV backup `C:\Server-Game\tsm_dodo\backups\dvt_gold_fence_955333c_20261013_083624`.
- DB credentials were printed in an earlier server runtime startup line; restrict/redact that log and rotate credentials appropriately, but do not paste secrets into GitHub or ChatGPT.
