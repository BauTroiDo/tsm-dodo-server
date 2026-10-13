# DVT C025 upload-time immutable offer snapshot + session UUID — 2026-10-13

## Scope

Continuing after commit `226672b` (staged permanent atomic SQL trade receipts), this milestone adds **source-level C025 offer integrity** to `TradeSystem` and `TradeHandler` on GitHub, plus direct Kotlin unit/integration tests. **The actual remote DEV JVM/code is NOT patched/restarted in this milestone.** The earlier DEV duplicate-BAG-slot anti-dupe protection and gold fence remain deployed.

This change does not call the newly staged SQL trade planner/receipt/coordinator/finalization gate, and it does not emit any extra DVT packet. Current C025 item trade still uses existing RAM-only settlement and retains the original reason-4 success response after RAM mutations. Pet C025-012 upload/execution stays unchanged.

## Exact changes

- `TradeSystem.TradeSession.operationId`: server-generated canonical random UUID unique to each newly created trade session. No UUID is fabricated from a client's packet or sent over original Lua's C025-002 wire. The UUID is **not durable by itself**; SQL receipt linking and recovery integration are separate unfinished tasks.
- `TradeSystem.TradeOffer.expectedItems`: copy of BAG `slot -> ThingData` for the item offer, including complete DVT Myth/NFT attributes, affixes, expiry, damage and quantity. Pet mode defaults to no item snapshot.
- `TradeItemOfferSnapshot.capture`: accept only unique original U8 slots (0..255), existing stacks with positive quantity. Store independent `ThingData` and nested Myth copies, so subsequent changes to an original BAG item's mutable Myth list cannot silently rewrite the uploaded offer.
- `TradeSystem.uploadItems` is now `suspend`, and `TradeHandler.handleUploadItems` awaits it. The item upload acquires `CharacterPersistenceGuard.withLock(characterId)` **before** `synchronized(trade)` — same order as C025 final confirmation with two-character locks. Cancel/disconnect may remove a trade while upload waits for the persistence fence; reject an outdated mapping to avoid resurrection. Repeat upload after `Uploaded` remains ignored.
- S:025-003 trade partner preview is assembled from the exact stored and ordered offered `ThingData` records rather than re-reading live BAG (which may have changed after the offer was presented). This prevents misleading, changing NFT metadata in the offer window.
- Final legacy `executeTrade` compares BOTH frozen offer slot maps against freshly loaded BAG slot contents before ANY `setBagItemSync`/gold mutations; any quantity, item ID, damage, affix, Myth or lock mismatch cancels with existing reason `REASON_SYSTEM=9` and never transfers a substituted stack.

## Verification

- New `DvtTradeFrozenItemOfferTest.kt`: seven tests for deep copy under Myth mutation, forged/duplicate/empty slots, UUID unique/canonical, true C025 two-party preview after source BAG changes, real final confirmation rejection without direct BAG writes, and upload replay preserving the first accepted offer.
- New `tools/test_dvt_trade_frozen_c025_offer_20261013.py` source audit validates exact upload lock/monitor ordering, active mapping, frozen preview/final check and **no live SQL wiring**; registered with DVT GitHub audit workflow.
- Verify full local DVT suite, then GitHub Server CI **and** DVT data audit before claiming Kotlin tests pass.

## Important remaining work

1. **The current live C025 item trade is not yet SQL atomic**: despite source integrity hardening, money/GOLD and inventory still settle in RAM in the legacy route. Do not deploy this stage alone as a purported full trade duplication fix.
2. Freeze money offer + exact BAG snapshot under shared two-character locks for future SQL planner; upgrade final confirmation to `TradeItemFinalizationGate` and `TradeCacheSettlementCoordinator` with durable `operationId` and atomic SQL receipt. The existing finalizer's exceptions remain `INDETERMINATE` until receipt and live cache reconciliation.
3. Audit all synchronous BAG and GOLD mutations (including autosave/logout, quest/Eve, battle rewards and remaining old C025 path), especially avoiding lock inversion or stale cache publication after SQL.
4. Ensure disconnect, concurrent repeat upload, spoofed C025 modes, and replay after SQL timeout cannot transfer twice. Finish H2 concurrency tests and roll out to DEV only with SHA-backed backups and CI PASS.
5. Original `UITrade.lua` kind53 / guild-only restrictions, one-time specialKind transfer consumption and pet-trade transactional ownership are still separate unsolved DVT parity items. Keep fail-closed in future SQL planner.
6. Preserve all mission Lua/IL2CPP source mappings, client navigation fixes, and user-requested consolidated manual in-game character testing for the final pass.

## Workspaces

- Github `BauTroiDo/tsm-dodo-server` draft PR #41, branch `fix/dvt-quest-nav-parity-20261011`.
- Windows isolated worktree `D:\Games\Ts-Offline\_dvt_trade_gate_20261013`, preserve prior `_dvt_nav_batch_20261012` dirty state.
- DEV host `tsm-prod`, `C:\Server-Game\tsm_dodo`; never bulk-reset divergent hotfixes. Prior verified JVM PID 12184 port 6613/MySQL ready. Gold-fence DEV backup `backups\dvt_gold_fence_955333c_20261013_083624`.
- DB credentials appeared in an earlier runtime startup log; do not paste credentials into GitHub, and rotate/redact separately.
