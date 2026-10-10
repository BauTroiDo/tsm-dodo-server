# DVT mainline navigation repair and active PC parity — 2026-10-10

## Problem and resulting behavior

Mainline guides11566/2 and11934/4 referenced Door IDs7 and5, although their scenes have only doors1..3. Correct both `endEventId` values to3, the actual DoorData.id owning the matching quest event. Preserve descriptions, guide strings, coordinates, conditions, teleport, team checks and Eve events. This is a navigation metadata repair, not a quest-step/reward mutation or a fabricated door.

## Proving the actual PC source

Running PC executable: `D:/Games/Ts-Offline/TSNPCVN_9PET_STAGING/tsnpc.exe`.

Decompiled its current `tsnpc_Data/Managed/Assembly-CSharp.dll` with ilspycmd:
- `RMSGlobal.ASSETBUNDLES_PATH` = `Path.Combine(Directory.GetCurrentDirectory(), "AssetBundles")`.
- `LuaFramework.LuaManager` adds that path + `/Lua`; `AppConst.LuaBundleMode=false`.
- `LuaFileUtils.ReadFile` decrypts the selected Lua file; `CGResourceManager.GetResourcePackedLocalPath` resolves below the same AssetBundles root.
- Decrypted active `AssetBundles/Lua/Logic/DataManager.lua` reads **`Data/IOS_MarkStep_C.dat`**, not the old StreamingAssets `Data/MarkStep_C.dat`.
- Player.log identifies asset loads from this staging client's AssetBundles folder.

Active Lua source SHA256:
- DataManager: `f330d8896c58f53baa44463a1f3b3c7f66396aca87a067c15311ca63e8a92827`
- MarkData: `2c5be04d6daf271d320f09c7852e8cfbb1520dfc48fe06fb277f58f2a1221b7c`
- MarkManager: `98d7d1d286fdb943359f287940cc5c68577db1c3e8f537ee10f277e43cc3dc4f`
- DoorManager: `cecb6d287aa87c821fe7539c80ad38dcc5ab7aa0cba385716f0c3cf10997cf5a`

The byte layout matches original DVT115 `_native_dump_dvt115/_lua_client_decrypted/Data/MarkData.lua`, `MarkData.ReadStep`. Both original DVT and active PC MarkManager compare `DoorManager.doors[navigationEventId]`; DoorManager indexes `this.doors[doorData.id]`. Eve event number is a different identifier.

## Before-patch complete parity

| Source | Bytes | Records | SHA256 |
|---|---:|---:|---|
| Active PC `AssetBundles/Data/IOS_MarkStep_C.dat` | 928425 | 4505 | `6e7c1c4c4b3d80e2256395b5713997a8d75535497320e59284cd4c65fe4980f1` |
| Live server `File.dat/Data-ios/IOS_MarkStep_C.dat` | 892087 | 4505 | `7b43b993ee4b765870faf8d38ea2ce3b9d2bfdb810221030d213197f7ac8f720` |

Parsed both to exact EOF. All4505 mission/step keys and structural fields match: zero missing records and zero structural differences. Hash differences come from text/record lengths; do not use raw hash inequality as a version-mismatch verdict. Full original comparison is `docs/dvt-client-markstep-parity-20261010.json`.

Active PC `IOS_Mark_C.dat` hash `6101d852713ce7a7445b4f1e25ce29d139756c9d33bd61853bcadcb529f0c07c` matches the server. Active PC and server Eve.emg share SHA256 `03c84cb565d4bdd10b7506a4d04a918ae590737f80a3e7e4fd37782b9c40b910`.

The embedded `resources.assets` TextAsset is a different baseline (4424 records, hash `4e82bcf4...edad7e0`), and StreamingAssets also contains old Lua/data. They are not the effective source selected by this client's current loader. The earlier14 differences/81 extra records from the embedded baseline must not be attributed to the active client.

## Exact Door evidence and repair

| Mission/step | Scene and guide coordinate | Old ID | Correct DoorData.id | Door center | Owned Eve event |
|---|---|---:|---:|---|---:|
| 11566/2 | 25021, (430,560) | 7 | 3 | (440,580) | 7 |
| 11934/4 | 12431, (990,720) | 5 | 3 | (1020,720) | 6 |

Scene25021/event7 contains an equality gate for mission11566 step2 and starts the original battle. Original battle outcome branches2/3 advance11566 by1; do not convert its intended outcome semantics into a generic victory reward. Scene12431/event6 has mission11934 step4 equality gates and the follow-up branch advances11934 by1. The repair changes only door reference IDs; it does not modify these chains.

## Reproducible repair, tests and operational limits

`python tools/dvt_markstep_parity.py --client-dat <client>/AssetBundles/Data/IOS_MarkStep_C.dat --server-dat <snapshot>/IOS_MarkStep_C.dat --report parity.json --fixed-client-dat client-fixed.dat --fixed-server-dat server-fixed.dat`

The tool writes separate candidates and rejects overlapping input/output paths. Strict parsing rejects truncation, duplicates, trailing bytes and odd UTF-16 lengths. Semantic anchors must match the proven scene/position/kind/old-ID/teleport/team values. Applying the repair twice is idempotent.

Only **two bytes** change in each complete data file. Candidate SHA256:
- PC: `e17ac359391fdaf0d4c57a51876acfb106749768a2e21dfbb1326e732c85f33f`
- Server: `fa1729647758eda7c3565e148c9498fcc4775cc8d0d87a2be842bcf1fbe1f9a3`

Original byte slices of the two affected records from the active PC and server are retained in `tools/dvt_markstep_door_records.json`. Eight tests cover actual fixtures, localization, semantic differences, malformed input, two-byte repair, unchanged unrelated fields, idempotency, changed-anchor refusal, and real repository Eve/mission-gate integration. CI runs them alongside Gradle test/build.

PC data is loaded into Lua tables during initialization: a saved file repair needs a client restart to be observed. Do not terminate a running player session automatically. Gameplay navigation/battle/quest advancement still requires an E2E check; passing static parity and regression tests does not prove gameplay completion. Server MarkStep loader currently uses mission IDs only; these IDs do not change, so this data-only correction needs no game JVM restart.
