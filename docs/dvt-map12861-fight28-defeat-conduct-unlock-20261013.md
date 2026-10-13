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
