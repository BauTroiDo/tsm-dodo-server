# DVT active Eve source parity — 2026-10-11

## Root-cause finding
The old checked-in `gamedata/Eve.emg` was NOT the data deployed on DEV and NOT the data loaded by the staging game client. This can make CI pass against obsolete Eve event branches and cause future deployments to restore them.

Validated SHA-256:
- Authoritative currently staged client `TSNPCVN_9PET_STAGING/AssetBundles/CompreseData/Eve.emg`: `03c84cb565d4bdd10b7506a4d04a918ae590737f80a3e7e4fd37782b9c40b910`
- Current server DEV `C:/Server-Game/tsm_dodo/gamedata/Eve.emg`: **same SHA**
- Obsolete checked-in blob before this patch: `3154357c5967d6573f21219309f479a8f1bf504e7c15582453f0c472c1841fb0`

## Example: novice quest 12280 at map 10821
The obsolete Eve file exposes a code30/code31 Surface and a resultClass15 effect. That is **not** the current DVT scene. The active binary has 10821 Eve3 condition3: talk26513 and +1 to 12280 (step12->13); condition5: resultType2/class7/parameter3, sceneInfo3 destination 10853. At 10853 Eve1 handles the next tutorial step. Do NOT add or silently no-op class15 to solve this scene, since this branch does not exist in the active data.

The new regression test `DvtActiveStarterEveParityTest` guards this chain and rejects restoration of obsolete choice30/31 and class15 in scene10821.

## Scope and limits
Only the repo's tracked Eve binary and targeted regression are changed. The client and DEV server already had the matching active Eve, so this commit does NOT need to patch or restart them. This is source/version parity, not proof that all missions, map unlocks or UI navigation work in an end-to-end playthrough.
