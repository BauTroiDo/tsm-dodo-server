# DVT Hoàng Phủ Tung: audit and selective rollback of unproven hotfixes

Date: 2026-10-14. Branch `fix/dvt-quest-nav-parity-20261011`, PR #41.

## Why we are rolling back rather than adding more patches

The user confirms the character remains temporarily unable to move after losing to Hoàng Phủ Tung. Previous rounds introduced multiple speculative changes that were not proven to solve the problem and may produce regressions. Do not mark this issue fixed based solely on successful compilation.

**Verified historical signals:** Earlier after-battle exit pacing shortened from 500ms startup + 100ms per fighter to 100ms + 15ms on a specific boss loss, but the freeze persisted even when exit-packet wall-clock time measured 273–277ms. An additional unconditional post-exit `playerStateManager.setHp` and `sendSetAttribute(HP)` on EVERY losing battle continued reporting HP198 while the client HUD still showed 0/198, so it was ineffective as a HUD remedy and could affect unrelated PvP/dungeons. Do not continue this globally mutating HP workaround.

## Rolled back now

1. **User client:** Restored exact SHA256-verified prepatch encrypted Lua bytes for **12 unique files** across `AssetBundles/Lua` and `tsnpc_Data/StreamingAssets/Lua`: `EventManager`, `DoorManager`, `Role`, `UIJoystick`, `RoleController`, `MoveController`. These were the unproven 2026-10-13 boss door grace, auto-patrol stop and movement debug edits. The rollback respected the nested history (the final EventManager patch had been layered on the first). Client prepatch hotfixes from earlier dates were retained, no game data changed. The current client files and historical preimages all passed strict per-file SHA256 checks before replacement; current patched files were snapshotted separately at `D:\Games\Ts-Offline\TSNPCVN_9PET_STAGING\_dvt_forensic_client_rollback_20261014`. **Running client must relaunch to use these restored files.**
2. **Kotlin branch / DEV release candidate:** Removes the unproven boss-loss exit acceleration, restoring original `delay(500)`, serialized `delay(100)` and original local-player-last S011-001 ordering. Deletes the obsolete regression tests and CI entry enforcing the bad acceleration.
3. **Kotlin branch / DEV release candidate:** Removes the globally scoped post-exit HP override (including `setHp` and S008-001) added in commit `446fcb1`. This failed to correct the client HP display and created an unnecessary write/packet in all defeat types. Existing authoritative battle HP recovery before exit remains in place.

## Kept pending further proof (not declared solved)

- Original DVT **TALK 41353** followed by the 5 source-backed TimeHide effects, implemented at `30ec837`, is faithful to the original Eve loss content. However the user has not yet completed an observed post-JVM-7200 boss defeat. It has a run-token-fenced no-response watchdog; verify that its C020-006 transitions do not lock the UI.
- Native client S020-008 **kind45** stops quest navigation and enters native defeat UI; only the original DVT scene12861/Eve6/Fight28 loss receives it.
- `MineEncounterSystem` intended 27-second no-wild-encounter grace after the boss defeat, and the proven world player ID13 vs DB charId31 callback fix (commit `494c163`). This fixes a distinct regression; it must be verified with actual `DVT quest defeat MineData grace: charId=31` runtime log.
- Previous general DEV hotfixes for inventory, rewards, shops, quest navigation etc. MUST be preserved. DEV working tree contains substantial uncommitted changes; never `git checkout -- .`, `git reset --hard` or bulk pull.

## Deployment gates

The new source reduction is staged until GitHub Server CI and DVT audit PASS. Before any DEV mutation:
- Copy/verify SHA256 of `BattleManager.kt` and `DvtBattleDefeatRecoveryPolicy.kt` in a fresh backup directory.
- Apply the precise two-file rollback patch with `git apply --check` and guard hashes; compile `:app:classes` on DEV.
- Only restart the game JVM after clean compilation, verify port6613 new PID, source hashes and timestamped Netty start.
- Avoid touching SQL, character HP/quest state, gold, loot, or unrelated staged client visuals.

Only after confirming the reduced build is live should there be **one** real client loss test with log/packet inspection. Expected: TALK41353 visible before NPC hides, no indefinite UI lock, and cooldown on the correct database character ID. If the UI still shows 0/198 or input blocks, instrument S008 payload *at the actual renderer* instead of sending more HP packets.
