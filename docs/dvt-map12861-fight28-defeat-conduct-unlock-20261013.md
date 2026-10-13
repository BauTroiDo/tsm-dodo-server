# DVT map12861 Eve6 Fight28 — post-defeat frozen world controller (2026-10-13)

## Production observation

User screenshot shows `CaoYênDao` HP **0/198** after losing a storyline battle at **Rừng Triệu Ca (map 12861)**, with NPCs still crowded around and the character unable to move. DEV log `C:\Server-Game\tsm_dodo\logs\tsm_dodo.log` reports battle **43**, opponent `14671 Hoàng Phủ Tung` plus four `17501 Nghiêm Hán Sĩ` NPCs, loss at **16:54:43.860**. `EveConditionEvaluator` selected `class8 fightKey28/28 pStyle2 battleResult2 = true`. At **16:54:45.812**, `EveFlowController` resumed exactly **six** defeat results, and at **16:54:45.813** it sent first talk `resultType1/class3/param9`. No subsequent EveFlowController dispatch after this talk appears in the inspected log window, despite the user seeing no dialog: client apparently never sent the expected C020-006; the original Lua `EventHandler[1]` sets `SetConduct(false)` while waiting for the talk callback.

The server earlier syncs a post-battle HP floor of **1** while still in the battle UI, but the user screenshot shows **HP0** after defeat. The final damage notification may overwrite the visible world HUD until a post-exit HP sync arrives.

## Exact DVT source

`gamedata/Eve.emg` scene **12861**, `npcEvents[6]`, condition **16** `class8/fight28/pStyle2` and condition **17** `class8/fight28/pStyle3`, each have identical six results: dialog `talkId41353` spoken by NPC clickId9, then five **cosmetic NPC hide** results `resultType0/class3/pStyle3/val2000` for NPC clickIds **9,10,11,12,13**. This branch contains **no reward, quest increment, inventory debit, gold, warp or battle restart**. The separate win branch `condition18 / pStyle1` contains distinct quest effects and MUST NOT be changed.

Client source `lua/Logic/Event/EventHandler.lua` `EventHandler[1]` calls `eventManager.SetConduct(false)` until `ShowTalkMessage` callback; that callback may not appear after the client exits the losing battle. An unresolved server event retains its C020-006 continuation and traps movement.

## Fix (staged source on PR #41)

- New strictly gated `DvtBattleDefeatRecoveryPolicy.isCosmeticDefeat(...)` requires exact `scene12861/eve6/fightDataKey28/!playerWin`, a six-result sequence with exact dialog and NPC-hide attributes. Any win, different fight, extra result or reward fails closed.
- `EveFlowController.resumeAfterBattle`: **before** normal talk dispatch, on that exact defeat shape, apply and send only five NPC-hide cosmetics; then **always** `cancelAndEnd` (clear EventSession and send S020 end). Do **not** mark EveCount completed or award a quest/item. No waiting on invisible dialog C020-006. All other events keep original logic.
- `BattleManager.handleBattleEnd`: after `S011` exit animation and `cleanupBattle(battle)`, resend the actual authoritative `>=1` HP attribute when `playerWin=false`; do not artificially change HP for wins or recredit any rewards. This addresses screenshot HP0 persisting into the world after combat.
- New Kotlin `DvtBattleDefeatConductRecoveryTest` four tests read real Eve.emg for source parity, real `EveConditionResolver` defeat selection, reject forged/changed/win shapes and verify escape branch. Python `tools/test_dvt_defeat_conduct_recovery_20261013.py` validates strict guard, exact source 6 results, end-event ordering, no reward, and post-exit HP sync; registered in the DVT GitHub workflow.

## Safe deployment boundary

- The fix is justified by observed log trace and screenshot, but is **not** a direct automated recreation of the client softlock. Real client loss test remains outstanding; do not claim proven game-client recovery without one.
- Verify local Python full suite and exact-commit GitHub Server CI + DVT audit. Then selectively back up and patch only DEV `BattleManager.kt` and `EveFlowController.kt`, and create the new `DvtBattleDefeatRecoveryPolicy.kt`. DEV source contains unrelated dirty hotfixes; do not reset/pull whole repo. Preserve SHA256 preimages and rollback paths; compile `:app:classes` before restart.
- Restart the DEV JVM only after successful rebuild and startup/port checks. No SQL/GM quest, HP, gold, or inventory mutations. Restart clears stale RAM Eve sessions; the user's new login must receive persisted player state normally. Preserve staged/unwired C025 durable trade.
- Future broader audit: identify other defeat branches with hidden TALK-only continuation, avoid applying this exact shape globally; compare client Lua behavior and resolve nullable/vanished NPC dialog talk data. Do not suppress genuine victory reward / scene battle scripting.

## Verified DEV deployment — 2026-10-13 17:13 +07

- GitHub exact-source commit `446fcb11368f083ff5e8927d9f3e47b99ab28472`. Server CI #38381080059 **SUCCESS**, DVT data audit #38381080044 **SUCCESS**. Full Windows `test_dvt_*.py`: **288/288 PASS** (40.748 seconds).
- DEV source was already dirty with other quest and battle hotfixes; selectively patched original `EveFlowController.kt` and `BattleManager.kt`, created `DvtBattleDefeatRecoveryPolicy.kt` (no git checkout/reset/pull), after SHA-backed per-file backups in `C:\Server-Game\tsm_dodo\backups\dvt_defeat_conduct_20261013_170702`.
- Original DEV hashes: EveFlowController `65B57B07ECE4316C3A74B21D6B39E895A4FB1BB1DC384116A3D17B103B96D897`; BattleManager `85D6BFBF75B5AF8DF728D0796CCCB9F9FDE7C4C44CD420E5923387734A50E966`.
- Patched DEV hashes: EveFlowController `4E2C2CF9009C2AE4CF0D55833804AA424AE3050302443E8BB43B4FDCFB0E4DC7`; BattleManager `0B5ACD9625E8991A9AE360319D60FC810F667B50DCBAB471F83869394FB4B224`; DvtBattleDefeatRecoveryPolicy `5D2E7CF1924FBDE40DC1640BC606EC852E6D51156D89C5DF1FA99974E5148307`.
- Ran DEV `:app:classes --offline --no-daemon --console plain`: **BUILD SUCCESSFUL in 58 seconds** with existing hotfixes preserved. Stopped old JVM PID6532 and relaunched existing game launcher via detached process creation (return code 0). New JVM PID **8204** confirmed live listening TCP **6613** with `MainKt`; startup log at 17:13:45 confirms DVT data loaded and NettyGameServer bound `0.0.0.0:6613`.
- No SQL, GM edit, quest advance, gold/HP or inventory manual write. Client gameplay re-test after defeat **not yet performed**; cannot claim the actual softlock is proven resolved until client verifies movement and HP after another loss.

## Follow-up after failed live gameplay test (2026-10-13 evening)

The user reported the same freeze **after successful loading** of the initial post-battle fix. DEV log confirms that old fix really did execute on battles **927, 931 and 941** (timestamps 19:07:47, 19:08:43, 19:11:00): each had `Battle defeat post-exit HP resync ... hp=198` and `Recovering post-defeat NPC talk lock`. However, wild MineData battles started immediately after losing the story battle; e.g. battle929 at 19:07:50 and battle933 at 19:08:45 (~2 seconds later). This proves that simply skipping a dialog did not prevent the follow-on loop.

**Native client Lua root:** `EventManager.ReceiveEndEvent(kind)` calls `ClearEventState()`, then `MarkManager.StopNavigation()` **only for endKind=45**, while all other kinds (including default 0) call `MarkManager.ContinueNavigation()`. EndKind 45 additionally sets `Role.deadMsgData.show=true`, the native death/recovery prompt. Previous loss cleanup sent default 0, silently restarting quest auto-navigation after defeat. The server `MineEncounterSystem.onBattleEnd` also rearmed the 27-second cooldown **only for MineData-triggered battles** (triggerKind=7), not the actual map12861/Eve6/Fight28 story loss.

**Second, source-backed staged fix:** The exact scene12861/Eve6/Fight28 six-result cosmetic loss uses `cancelAndEnd(..., DEATH_STOP_NAV_END_KIND=45)`, leaving unrelated event endings at 0. The battle callback now passes `playerWin`; only this original quest defeat arms a full 27-second MineData grace, resetting distance budget while retaining any longer existing cooldown. No change to normal wins, unrelated Eves or item/reward grants. Added Kotlin source policy regression and Python audit validating original client Lua end-kind semantics, the full callback and grace.

**Deployment status:** The authorized Windows Desktop Commander stopped responding during the follow-up; this staged GitHub commit requires Server CI and exact SHA verification, then selective DEV backup/build/JVM activation. **Not deployed or client-proven at this point.** Do not claim the live freeze has been resolved until tested on CaoYenDao. Never SQL/GM-edit HP, items, quests or other dirty DEV fixes to simulate success.


## Actual second-fix DEV deployment — 2026-10-13 20:37 +07

**Commit deployed:** `42547ab6547dcb88ea5c9fbeebd6fcd04fbd5ee8` (not this documentation-only checkpoint). GitHub Actions on that exact code SHA: **Server CI #38404457822 SUCCESS**, **DVT data audit #38404457869 SUCCESS**. Local Windows `python -m unittest discover -s tools -p test_dvt_*.py` exit 0 (40.58s), staged diff check PASS.

Desktop Commander recovered; SSH `tsm-prod` connected to `C:\Server-Game\tsm_dodo`. Verified old game JVM **PID8204**, TCP **6613** prior to update. Saved SHA-256-verified individual copies of exactly four affected DEV Kotlin files in:

`C:\Server-Game\tsm_dodo\backups\dvt_defeat_followup_42547ab_20261013_2030`

The precise four-file Git diff from `c5740aa` to `42547ab` passed `git apply --check --ignore-space-change` against **dirty DEV** before applying; changes were selectively applied without reset, checkout, bulk pull, SQL, game inventory or mission mutation. Build `gradlew.bat :app:classes --offline --no-daemon --console plain` **BUILD SUCCESSFUL in 37s**. Kotlin warnings were non-blocking.

**Patched DEV SHA256**:
- `app/.../ServerModule.kt`: `155C5E3BA1E61F0DCB58E11B380D2360D535A9EDE0C0FA09C9ECEC3EED183CFE`
- `gamelogic/.../DvtBattleDefeatRecoveryPolicy.kt`: `9411C8AF9E5F6359EC7270492155463F5FB9FA16F47BA4459BFB1E735B98A9A6`
- `gamelogic/.../EveFlowController.kt`: `390EFB2A9FA49837F62C6B222F77C1E63C591C4C0D2AD28F11723578746A8544`
- `gamelogic/.../MineEncounterSystem.kt`: `CD00227CA7688AE4060A73ADE09C97632C3216015309B8E02B024C1F17711FEA`

Stopped old PID8204 after build; relaunched original DEV launcher with Win32_Process.Create return code 0. New **MainKt JVM PID12076** confirmed listening TCP **6613**, startup log `runtime-production-dvt-trade-dupguard-baf3c59.log` reports Netty server bound to `0.0.0.0:6613` at **20:37:31**. The previous Eve defeat remediation and unrelated hotfixes remain in place.

**Remaining verification:** The user has not yet tested an actual new loss on this freshly started JVM. Confirm CaoYenDao relog, original map12861/Eve6/Fight28 defeat: S020-008 endKind45 closes event, `MarkManager.StopNavigation`, client death prompt, `DVT quest defeat MineData grace` log for charId31, no immediate random battle, movement after prompt, HP properly refreshed. A successful server build/start is NOT proof of client recovery. If still stuck, inspect client-side Lua/EventManager and FightManager fight-exit animation/message state, actual C006 move, and any other scene/MineData entry; do not assume resolved.
