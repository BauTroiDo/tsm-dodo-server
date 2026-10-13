# Restore authentic DVT UI icon unlock progression — 2026-10-12

Status: **applied to both Windows staging client copies and DEV PC UiOpen_C.dat**. This **supersedes** `docs/dev-client-ui-icon-unlock-20261012.md` and revokes the temporary 50-icon bypass from commit `3b03792`.

## Why the previous unlock was wrong

The user confirmed icons must unlock at their original **player level and mission** milestones. The previous DEV-only patch cleared `showFlag/requireFlag` and `requireLv` for 50 menu records (group1/2/3), even if the source requires a quest flag and/or character level; it also layered over five older noncanonical tweaks from 2026-10-10. Making an icon visible is not proof its backend is usable, and disabling mission gates can expose unavailable functions before the actual tutorial flow.

## Source of truth

- **Authoritative DVT binary** `File.dat/Data-ios/IOS_UiOpen_C.dat`, 84 records, 2978 bytes, SHA256 `af759749cf6d33081df7de99f715cf682e324bb48fb0ff4c57edb932a78c8b4e`. This matches the archival pre-2026-10-10 copies in `D:\Games\Ts-Offline\_client_pc_uiopen_level_backup_20261010`.
- `lua/Data/FunctionData.lua` defines byte-exact layout of `showLv/showFlag/requireLv/requireFlag`.
- `lua/Logic/Function.lua`, `Function.CheckShow`, enforces visibility using `Role.CheckPlayerLv(showLv)` and `MarkManager.GetMissionFlag(showFlag)`. `Function.CheckUse` independently verifies `requireLv` and `requireFlag`, returning a message on failure. Server-switch restrictions, house/scene/battle/spectator modes and limited-time event checks still apply. `UIMainFunction` also has collapsed versus expanded menu independent of progression.

### Examples verified in the original source

| ID | Feature | Visible at | Usable at |
|---|---|---|---|
| 18 | Dungeon | Lv15 | Lv15 |
| 20 | Organization | Lv10 | Lv10 |
| 22 | Compound | Lv15 | Lv15 |
| 28 | Activity | Lv15 | Lv15 |
| 23 | Furnace | Quest flag11804 | Quest flag11804 |
| 62 | Server Lucky Bag | Quest flag11804 | Lv10 |
| 65 | Army Lucky Bag | Quest flag11804 | Lv10 |
| 63 | Unbounded Act | Quest flag11804 | Lv15 |
| 66 | House | Quest flag11804 | Lv60 |
| 10 | Skill Tree | Quest flag11804 | Quest flag11804 |
| 11 | Equipment/Bag | Quest flag12396 | Immediately once visible |
| 12 | Character Status | Quest flag12283 | Immediately once visible |
| 16 | Team | Quest flag12285 | Immediately once visible |
| 32 | Teleport | Quest flag11804 | Quest flag11804 |
| 73 | Elf | Quest flag11804 | Quest flag11804 |

These are **table values from the original DVT file**, not assertions that backend actions or a particular character's current quest flags already work. `showLv=0` means the field has no level restriction; an associated `showFlag` can still hide it.

## Restoration and safeguards

Restorer: `tools/restore_dvt_uiopen_original_gates_20261012.py`. Original client `UiOpen_C.dat` in both locations and DEV PC `File.dat/Data/UiOpen_C.dat` were previously SHA256 `d3a30b6ae1c6e67454df8218d7a2fea7ed705f29d8180fb3cb528db4b4fb7980`; now all three are byte-for-byte canonical SHA256 `af759749cf6d33081df7de99f715cf682e324bb48fb0ff4c57edb932a78c8b4e`. Exactly **149 byte differences** and **50 records with changed gates** relative to the temporary unlock; the original menu icon IDs, server-switch mask, names, icon images and non-gating fields are unmodified. This also undoes the *five older noncanonical edits* (IDs23/62/63/65/66) where they diverged from original DVT.

Client rollback backups created:
- `D:\Games\Ts-Offline\TSNPCVN_9PET_STAGING\AssetBundles\Data\IOS_UiOpen_C.dat.before-dvt-gate-restore-20261012_171351.bak`
- `D:\Games\Ts-Offline\TSNPCVN_9PET_STAGING\tsnpc_Data\StreamingAssets\Data\UiOpen_C.dat.before-dvt-gate-restore-20261012_171351.bak`

DEV PC backup: `C:\Server-Game\tsm_dodo\backups\dvt_original_ui_icons_20261012_171447\UiOpen_C.before-restore.dat`. The DEV `File.dat/Data-ios/IOS_UiOpen_C.dat` was already canonical and **unchanged**. The old DEV temporary bypass remains recoverable only in these safety backups and git history; it is no longer the active file.

**No character level, completed quest, mission flag, reward, Eve, MarkStep, Kotlin, client Lua or game service data was edited.** The server game continues running; reopen the staging client completely to make it read the correct UI table. Reconciliation of any residual icon-level behavior needs live in-game tests after the planned quest sweep. New regression `tools/test_dvt_uiopen_original_progression_20261012.py` locks the full SHA, DVT field decoder, 16 exemplar level/mission requirements, both Lua gate checks, exact former DEV bypass reconstruction, atomic restore/backup/idempotence/tamper refusal.
