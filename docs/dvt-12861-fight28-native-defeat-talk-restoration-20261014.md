# DVT Rừng Triệu Ca: restore native defeat talk before NPC TimeHide

Date: 2026-10-14. PR #41, code branch `fix/dvt-quest-nav-parity-20261011`. **Staged until Kotlin CI, DEV build, exact game restart and gameplay verification.**

## Source-backed root of missing dialogue

The user reports losing to Hoàng Phủ Tung, not seeing a dialogue, and being briefly unable to control the character until mission NPCs disappear. The precise original DVT `File.dat/Eve.emg` scene12861/Eve6/FightData28 **failure** branch (condition16) has **six ordered effects**:

1. **TALK**, resultType1/class3, NPC click9, `resultMeanNo=41353`;
2. Five **TimeHide** effects (type0/class3/pStyle3/value2000ms), NPC click IDs **9,10,11,12,13**.

Decoded client `AssetBundles/CompreseData/Talk_C.dat` contains the exact UTF-16LE message 41353: **“Chút năng lực này...”** (42,685 records; complete binary archive consumed correctly; entry length is bytes). Thus the missing dialogue was **not** a missing client text asset. Native Lua `EventHandler[1]` uses `ShowTalkMessage(...,talkDatas[eventInfo.resultMeanNo],showRole)` and `eventManager.SetConduct(false)`. After the player closes `UICheck`, it calls `SetConduct(true)`, then `EventManager.NextEvent` emits `C020-006`. Native type0 TimeHide emits the same continuation after applying each hide.

**Proven server omission:** The prior emergency bypass in `EveFlowController.resumeAfterBattle` deliberately dropped the first TALK and immediately sent all five S020-001 TimeHide effects, then `S020-008 endKind45`. This removed exactly the dialogue the user expected and rendered NPC disappearance while input transitions were still pending. It was intended to avoid a lost `C020-006`, but it did not match the client and data original; **do not infer it was a faithful representation of DVT**.

## New proposed stage

- The exact **scene12861/Eve6/Fight28 defeat** first replaces the active Eve result chain with the six source effects, retains its immutable `runToken`, and sends **TALK 41353 alone** via the native S020-001 wire.
- After each actual `C020-006`, sends **one** original TimeHide effect (NPC 9 through 13), strictly forbidding money/items/mission grants or warps. Only after all five does it cancel the loss session and send **endKind45**. No EveCount/quest completion is recorded.
- The token-fenced `pendingDefeatTalks` only recognizes the same original event run, removing stale entries safely. A **120-second fail-safe**, not a fixed gameplay sleep, calls `clearIfCurrentRun` and sends endKind45 if a client never dismisses the dialogue. If a new Eve replaced the old run, the stale watchdog cannot end it. User may read/close the dialogue naturally; there is no arbitrary 2-second global lock added.
- All other events and fights, achievements, equipment, inventory, rewards, HP, MineData pacing and map unlocks are unchanged.
- Kotlin `DvtBattleDefeatConductRecoveryTest` confirms talk then exactly five NPC TimeHide results and stale watchdog cannot clear another run; Python source audit verifies Eve original shape, client native TALK handler, strict continuation, token fence and guard timeout.

## Separate known issues / release requirements

At 2026-10-14 03:53:13 server previously sent post-exit HP198 but client screenshot displayed 0/198; investigate attribute values and HUD separately. The separate `MineEncounterSystem` char/world ID fix is commit `494c163` (code already selectively built on DEV but restart had not been confirmed in the previous checkpoint). Do **not** conflate missing dialogue with HP desync, BeePath auto-movement or battle-ID mapping.

**No deployment claim until the exact new SHA is CI PASS, Kotlin compiles on DEV preserving dirty hotfixes, backup SHA verified, JVM restarted successfully and client actually displays TALK41353.** Preserve every DEV hotfix and staging Lua cipher file. One manual client test after activation, not endless speculative loss loops.
