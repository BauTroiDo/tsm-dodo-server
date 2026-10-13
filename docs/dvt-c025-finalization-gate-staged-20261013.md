# DVT C025 durable item-trade finalization gate — 2026-10-13

## Objective and strict deployment status

**SOURCE AND TESTS ONLY; NOT CONNECTED TO LIVE C025; DO NOT DEPLOY OR RESTART DEV FOR THIS MILESTONE.**

The previous DEV milestone deployed the synchronous gold writer fence and Inn C031 debit fixes from source commit `955333c` (checkpoint `6eb07d6`). The existing player item-trade implementation still alters BAG and GOLD in RAM and sends the native DVT S:025-002 success reason **4** after those RAM changes. It does **not** currently use the already-tested two-character SQL transaction or the subsequent RAM cache publication coordinator.

The staged source `TradeItemFinalizationGate.kt` provides a small deterministic per-trade protocol boundary to be used **later** when all prerequisite writers and recovery paths have been verified. It does not change TradeHandler, TradeSystem, ServerModule or any running player trade.

## DEV health re-verification after last deployment

Verified 2026-10-13 via responding Desktop Commander `DESKTOP-Q96IFHO` and SSH alias `tsm-prod`:

- `WIN-33R6OP5GO1M`, running Java **PID 12184**, TCP port **6613 LISTENING** (same replacement JVM seen in previous checkpoint).
- Current runtime log `C:\Server-Game\tsm_dodo\logs\runtime-production-dvt-trade-dupguard-baf3c59.log`: MySQL Hikari connection acquired ~08:38:55; database initialized ~08:38:56; DVT MarkStep **984 missions / 4505 steps**; Netty listening `0.0.0.0:6613` at ~08:38:58.
- Runtime health is confirmed at initialization and repeated loopback connections. This does **not** constitute an interactive two-player gameplay test or a successful C025 SQL commit.
- **Security follow-up:** launcher/runtime diagnostics include a full Java environment-options line containing database connection credentials. Do not paste that line into reports. Restrict/redact logs and rotate the exposed credential following operational review. The credential value is deliberately not stored here.

## Independent finalization guard design

- `confirm(first/second)`: only a previously unconfirmed participant can move from waiting to confirmed. Once both have confirmed, exactly one contender wins `START_SETTLEMENT`; replay/spoof confirmations are ignored.
- `cancelOrDisconnect`: before both confirmations, cancels the trade (no SQL, no success). Once settlement is elected, cancellation/disconnect returns `COMMIT_IN_FLIGHT` instead of tearing down the transfer or broadcasting an unproven failure. The future session adapter should suppress packet sending to offline users without stopping the durable transaction.
- `settleOnce(commitAndPublish, notifySuccess)`: admits only the elected settler. Rejects an already-cancelled request before SQL starts; executes trusted SQL+both-cache publication under `NonCancellable`; sets terminal `REJECTED` only for a definitive SQL false, but `INDETERMINATE` for exceptions that might occur after commit (quarantine and reconcile; never retry blindly); invokes the success callback **strictly after** successful commit and both cache publications. An outgoing packet exception does not roll back SQL or grant a second settlement attempt.
- No `synchronized` JVM monitor is held across suspension. The only future valid settlement callback is `TradeCacheSettlementCoordinator.stageCommit`; never pass the legacy RAM-only `executeTrade` routine.
- **Important bound:** this guard is an in-process single-execution policy, not a persistent transaction ID or durable notification outbox. It cannot alone provide cross-process exactly-once settlement or packet delivery; do not wire live trade until the database state, BAG writer audits, protocol session adapter, and logout/disconnect semantics are validated.

## Tests and validation

- `DvtTradeItemFinalizationGateTest.kt` covers seven scenarios: simultaneous confirmations/replays and duplicate commit attempts, cancel before confirmation, SQL+cache before acknowledgment, disconnect/coroutine cancellation during SQL, SQL false/throw rollback outcome, post-commit socket failure, and invalid participants.
- `tools/test_dvt_trade_item_finalization_gate_20261013.py` verifies source ordering and cancellation barriers, test presence, and that C025/DI remains unwired; registered in `.github/workflows/dvt-data-audit.yml`.
- On Windows `D:\Games\Ts-Offline\_dvt_trade_gate_20261013`, Python audit returned **3/3 PASS**; `git diff --check` passed.
- Local Windows had Python but **no Java executable on PATH** (including common JDK locations). An attempted Gradle command emitted `'java' is not recognized` and **was not a test pass**. Kotlin tests require GitHub Server CI (and/or a suitable JDK host) before this checkpoint can be marked validated.

## Further integration blockers

1. Build transaction plans from two exact, post-slot-removal full BAG snapshots; conserve every item identifier, quantity, metadata, GOLD and auctionGOLD. Never mutate active items as side effects of planning.
2. Complete a read/write audit of all BAG mutations, including TradeSystem legacy synchronous direct writers, EXP Slot, Treasure Bank (staged), and regular inventory commands, against the same character persistence fence.
3. Move real C025 confirmation into a guarded `SETTLING` phase, release JVM monitor before any SQL `suspend`, and only send S025 success reason 4 after DB commit and RAM cache publication; handle partner disconnect and actor cancellation after SQL start without duplicate messages.
4. Add a persistent trade operation ID / terminal settlement reconciliation strategy for process loss and lost packets. New LOGIN must rely on durable DB state; avoid a second trade after ambiguous commit.
5. Pet C025-012, Treasure, auction GOLD, and original DVT special item restrictions remain separate scope. Preserve original client Lua and IL2CPP parity and failure codes; do not guess packet formats.
6. Keep manual client-character testing deferred to the user's final combined test pass.

## Workspace preservation

- GitHub branch/PR: `fix/dvt-quest-nav-parity-20261011` / draft PR #41.
- New **clean** Windows Git worktree for this milestone: `D:\Games\Ts-Offline\_dvt_trade_gate_20261013`, based on checkpoint `6eb07d62a1888a91fba53ac5db7bb1be66314df3`; do not reset/overwrite the unrelated dirty worktree `_dvt_nav_batch_20261012`.
- DEV source `C:\Server-Game\tsm_dodo` unchanged by this stage; previous MoneySystem and InnHandler backup remains at `backups\dvt_gold_fence_955333c_20261013_083624`.
