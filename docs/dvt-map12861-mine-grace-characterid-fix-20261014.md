# DVT map12861 boss defeat: world player ID passed to DB-keyed Mine cooldown

Date: 2026-10-14. Branch: `fix/dvt-quest-nav-parity-20261011`, PR #41.

## Reproduction and direct evidence

CaoYenDao character database ID **31** and world battle/player ID **13** differ in the live DEV logs. `BattleManager.handleBattleEnd` calls the callback with `(session, playerId, playerWin)` where the battle map key `playerId` is **13**; `session.roleId` refers to DB character **31**.

Before this change `app/.../ServerModule.kt` called `MineEncounterSystem.onBattleEnd(playerId, playerWin)`. The implementation looks up `EveEventStateMachine.getSession(characterId)`, which is keyed on DB character ID, and returns immediately when no session exists. It also stores `MineWalkState` in a map keyed by DB character ID. Calling `onBattleEnd(13, false)` misses the active Eve6/Fight28 state for character31, so the intended `DVT quest defeat MineData grace` log and the 27-second grace period never appeared. The separate `EveFlowController.resumeAfterBattle(session, playerId, playerWin)` call uses the battle player key for its existing API and remains untouched.

Measured DEV:
- 2026-10-14 **03:53:13.066** battle208 scene12861 Eve6 Fight28 defeat, 278ms S011 exit pacing, HP198 sent.
- **03:53:13.372** defeat Eve branch cleanup on charId31.
- **03:53:34.695**, only 21.629 seconds later, `MineEncounterSystem` triggered a new MineData battle211 Fight13 despite intended 27 seconds.
- **03:54:58.920** battle213 same boss defeat, HP198 and event close confirmed.
- Client `Player.log` receives `S020-008 kind45`; latest patch logs `Boss defeat BeePath stopped oldAutoMove=true nowAutoMove=false`. This is independent of the missing Mine cooldown.

## Minimal source fix

Pass `session.roleId.toLong()` into `MineEncounterSystem.onBattleEnd` rather than `playerId`. No change to the Eve callback, fight packets, rewards, character HP, initial MineData RNG, or unrelated battle rules.

Update the DVT source audit to **require** correct character ID and reject the old wrong argument. Add real Kotlin/MockK regression `MineEncounterPacingTest.defeatGraceUsesDatabaseCharacterIdRatherThanWorldPlayerId`: a world ID13 fails to find a state keyed on character42; the correct character ID42 with map12861/Eve6/Fight28 loss arms the cooldown; no Mine encounter at 21 seconds but a new encounter becomes possible at 28 seconds.

## Release gate and remaining investigations

**Staged until CI PASS and DEV build/restart are verified.** On DEV, first verify and back up exact `ServerModule.kt`, selectively apply the one-file change, build `:app:classes`, then restart the JVM only when safe. Confirm new JVM on TCP6613 and the fresh `DVT quest defeat MineData grace: charId=31 map=12861 eve=6 fight=28` line after an actual loss. No manual gameplay test is necessary until then; client test remains pending.

This fixes the proven 27-second cooldown bug. It does **not** prove full recovery from all perceived player control delays or the distinct `0/198` client HP display after death. Those require separate client instrumentation. Avoid speculative quest, inventory, or database edits.
