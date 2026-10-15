# DVT reward boundary audit — 2026-10-11

## What is protected in PR #36 (experimental, not deployed)

1. `EveFlowController.dispatchResult`: before recognized server-side item, money, quest, skill, captured follower, Moral/Point, and RoleCount mutations, `ensureDurableEffectClaim` reserves a stable `EventSession.runToken` in MySQL. `EveEffectClaimPolicy` excludes UI-only Talk/Surface/services. It also fences special beginner mission result and `autoCaptureFromEvent`.
2. `BattleManager.handleBattleEnd`: normal winning Eve battles give generic rewards **before** `onBattleEndCallback`. The new `onBeforeEveBattleRewards` callback fences the relevant run before KillNum, Phuc Than debit, item drops, EXP, pet EXP, gold and caught followers. When it rejects, none of these are granted and the Eve continuation is aborted. Tests validate win-only policy and the flow callback's DB reservation and idempotence.
3. `completeEventInternal` settles a claimed Eve atomically: increment EveCount, write permanent receipt and delete the matching claim. Reuse of the same runToken after successful settlement is denied, even after an ACK loss.
4. A claim pending at crash is deliberately not auto-cleared; administrator review is required.

## Risks still outside the Eve receipt's transactional boundary

### 1. Participating players without an Eve session

`BattleManager` can process `battle.playerSessions` for several participants. The per-Eve claim token is owned by each active `EventSession`. `reserveBeforeEventBattleRewards` presently allows a participant with no active Eve session so unrelated group members are not unfairly locked out. Their generic battle rewards do **not** receive an Eve claim. The long-term fix requires a separate battle payout ledger keyed by (battle instance, participant, reward kind) and transactions/idempotency keys for each mutation.

### 2. Normal dungeon terminal reward

`DungeonSystem.settleAfterEvent` delegates to `settleAfterBattle`, which marks `activeRuns[playerId].settled` and calls `persistRun` **before** the daily counter and `inventory.addRewardsToBagExact`. This provides a partial restart fence but not an atomic reward transaction. A crash between persisted settled and inventory mutation can lose a deserved reward. A DB write with lost ACK requires reconciliation.

### 3. Weekly dungeon / soul reward

`DungeonSystem.settleWeeklyBattle` CAS-sets `run.settled` in RAM, then grants inventory rewards and sets the dailyFlag, before `finishWeeklyRun` clears the persisted dungeon state. A process crash during those steps can expose replay or loss on recovery. The weekly-all-pass `claimWeeklyAllPass` grants an item before marking mission completion, creating another independent crash window.

**Do not** reuse an EveCount settlement receipt to resolve (2) or (3): those are independent payout types and might increment EveCount incorrectly. Design a separate durable dungeon claim keyed by dungeon run/period/reward step, with operator audit and correct repeatability.

### 4. Generic battle rewards are not one SQL transaction

The claim acts as a **fail-closed fence**, not an exactly-once transfer. KillNum, EXP, inventory, gold, pet experience, and capture can be written independently. A partial crash leaves an unresolved claim even if only some rewards were granted. The audit tooling must compare reward state and game logs, and an administrator must reconcile rather than blindly auto-retry.

### 5. Not-yet-decoded DVT resultClass

Previous DVT audit identified unsupported resultClass 11/13/15 and incomplete class1/pStyle3 decoding. Until their native Lua/IL2CPP effects are decoded, `EveEffectClaimPolicy` cannot guarantee it reserves *all* real state-changing effects.

## Release conditions

- PASS test suite/CI including the battle fence and pending claim fault injection.
- Review DVT replay/repeatable events and multiplayer battle semantics with a test client.
- Add an independent dungeon/battle payout ledger or explicitly scope out those rewards with transparent limitations.
- Add admin-safe read-only audit plus controlled post-verification reconciliation, especially for crash after some inventory or currency updates.
- Back up the live DB and dirty source tree, apply a selective diff, re-run production Gradle and verify port 6613 and startup logs. Do not merge or deploy while reward paths are unresolved.
