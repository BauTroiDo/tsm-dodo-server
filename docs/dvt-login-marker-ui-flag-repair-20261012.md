# DVT login marker mission bit parity for level/quest icon unlocks — 2026-10-12

Commit on PR #41 following `09dd474`. Only source DVT mission marker reconciliation is added; **all 84 icon records remain byte-for-byte original** with level/mission unlocks. No GM completion, client Lua patch, quest auto-skip, map unlock, item/reward generation or new quest step.

## Root cause / source proof

Original `File.dat/Data-ios/IOS_UiOpen_C.dat` is SHA256 `af759749cf6d33081df7de99f715cf682e324bb48fb0ff4c57edb932a78c8b4e`. The Lua original checks `showLv/showFlag` in `Function.CheckShow` and `requireLv/requireFlag` in `Function.CheckUse`. `MarkManager.GetMissionFlag(missionId)` resolves `Mark_C.dat` `bitId` and `MarkManager.InitMissionFlag` reads the initial packed flags from server `S024-007`; later `S024-005` updates the icon state.

Original `IOS_Mark_C.dat` entries with **no MarkStep_C row**:
- `11804` (Vào Trục Quận Thành) bit **936**
- `12283` (Sơ nhập thế giới TS) bit **1375**
- `12285` (Bạn đồng hành đầu tiên) bit **1376**
- `12396` (Hướng dẫn mang, tháo trang bị) bit **1434**

These source markers have `bitId` but no regular step definition. For legacy characters, an earlier server could store a positive marker step after source Eve ADD_STEP without setting its completion bit. The existing `EveStateBuilder.buildPlayerState` already repairs the missing marker bit when any Eve event is evaluated. **But `LoginService.executeCharacterEnterSequence` sent S024-007 before building Eve state**, so the client may begin with empty marker bits and hidden/locked UI even though the source marker step was already persisted.

## Focused server correction

`QuestSystem.reconcileStoredMarkerMissionFlags(characterId)`: under the existing per-character mutation mutex, loads persisted quest steps and flags, then (only when authoritative `GameDataManager.markStepMissionIds` is available) repairs a missing bit if and only if:
1. The character already owns that mission with positive persisted step.
2. Original `markDefs[missionId]` exists with a positive, packet-range `bitId`.
3. `missionId` is **absent** from original DVT MarkStep mission IDs; real main/side quests must not be completed or unlocked early.
4. The bit is not already set.

No synthetic marker stage is created, no changes to ordinary completed quests or other flag types; repeated relogins are idempotent. If source MarkStep membership is unavailable, fail closed (no bits altered).

`ServerModule` injects the actual loaded DVT MarkStep mission IDs into `QuestSystem`. `LoginService` invokes reconciliation after daily/weekly dungeon refresh and **before** reading flags, sending `S024-007`, or `S003-000 PlayerAppear`. Existing `EveStateBuilder` self-heal continues to cover historical in-session legacy states; no changes to it or Eve executor were necessary.

## Regression gates

`tests/src/test/kotlin/tw/tsm/tests/DvtLoginMarkerUiUnlockRegressionTest.kt` asserts authentic source MarkDef and absence of MarkStep for four UI milestone markers, stale saved positive marker produces only the missing corresponding bit(s), no flag for real MarkStep / unknown / inactive marker, no duplicate writes on second login, no reconciliation without authoritative source membership, original S024-007 byte packing (bit936→byte117 bit7, bit1375/1376→byte172 bits6/7, bit1434→byte180 bit1), and login ordering before client init/appearance.

### Deferred in-game checks

Use a saved character whose real marker step exists but whose completion flag is missing, log out/in, verify the icon appears only after corresponding legitimate DVT marker and required level. On a clean new character, verify UI stays locked until source Eve naturally advances and sends flag. Verify stage-dependent show/use, relog, early levels, and world map. This code cannot prove all game icon functions are fully implemented without a live account test.

## Deployment safety

Deploy source changes via a clean Git commit, wait for Server CI and DVT data audit PASS; build on the DEV server **without resetting its dirty worktree**; backup any changed production artifact before restart. The patched Kotlin requires game-server binary deployment; simply replacing the UiOpen data file would not implement this correction. No client data changes in this batch.

## Deployment checkpoint — 2026-10-12 18:50 ICT

- Source commit: `8021ab46e6c93fa02ab45900b38a2a4dd31f7f64`; test import correction: `7d80d1da395c382619da9f06709d934606bdba5d`.
- GitHub Server CI run `38244708534` **PASS**. DVT data audit run `38244708310` **PASS**.
- DEV host `tsm-prod`, installation `C:\Server-Game\tsm_dodo`. Three predeployment Kotlin file hashes matched commit `09dd474` exactly despite a dirty DEV worktree, so no Git reset/rebase/checkout was used. Exact file backup: `C:\Server-Game\tsm_dodo\backups\dvt_login_marker_7d80d1d_20261012_185011`.
- First attempted Gradle compile was aborted by PowerShell 5.1 treating a harmless Java stderr startup banner as a terminating exception; deploy script automatically restored all three original Kotlin files and did not stop the Java game process. After SHA re-verification, a corrected script redirected Gradle's output in `cmd.exe`, resulting in **Gradle BUILD SUCCESSFUL** before restart.
- Successful restart: Java game process PID **2728**, TCP **6613 LISTENING**, game ready in `C:\Server-Game\tsm_dodo\logs\runtime-production-dvt-login-marker-7d80d1d.log`. The new `QuestSystem.class` bytecode contains `reconcileStoredMarkerMissionFlags`; all three source SHA256 digests match the patched Git commit.
- Original DVT UI table on DEV remains unchanged, SHA256 `af759749cf6d33081df7de99f715cf682e324bb48fb0ff4c57edb932a78c8b4e`. No client asset changes and no blanket icon unlock. Gameplay E2E by character remains deferred as requested.

**Original source Kotlin SHA256 → deployed SHA256**:
- `app/src/main/kotlin/tw/tsm/app/ServerModule.kt`: `19cec74c4a1b89f1eed082118bfd08c48ca4f50d958fa2c1428233eca7ba84ef` → `92abce86d5fb3894b45578582f6fc98a57e888db80f4c41c7c7863c073cc9efe`
- `gamelogic/src/main/kotlin/tw/tsm/gamelogic/systems/LoginService.kt`: `2dcf67bf3c708d8bdfee7d6c4143a7a03172d30281cfcb019e7410ae5ae2d91b` → `6bbe3b6d58a239f7919758df10a8a07947d1c564194929bf506bf1e4b891ce72`
- `gamelogic/src/main/kotlin/tw/tsm/gamelogic/systems/QuestSystem.kt`: `baaaafd2a6812e5717fc8b0a671e3535ce5ed56cff838d07b69f5b5fa95ea589` → `5a726485ddb31dd44350365d378d3017c9869bf20b10a1c293bfd259a2cbbc6c`.
