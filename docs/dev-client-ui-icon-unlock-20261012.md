# DEV icon menu early unlock, 2026-10-12

## User request and scope

For the current Windows staging client **TSNPCVN_9PET_STAGING**, remove *only table-level icon show/use level and quest-flag requirements* to let the developer inspect menu UI before the full gameplay and quest walkthrough. This is deliberately **DEV-only** and does not reward quests, mark tutorial missions complete, spawn items, raise actual character levels, or change Kotlin protocol enforcement. We retain the earlier requirement that the **world map remains quest-locked**, rather than granting unrestricted teleportation or map progress.

## Source-verified mechanism

Original DVT `lua/Data/FunctionData.lua` reads 84 `UiOpen_C.dat` records with `kind`, `group`, `name`, `icon`, `aniBtn`, `showLv`, `showFlag`, `requireLv`, `requireFlag`, `requireMsg`, `serverSwitch`. `lua/Logic/Function.lua` checks `showLv/showFlag` in `Function.CheckShow`, `requireLv/requireFlag` in `Function.CheckUse`, and enforces extra conditions for active events, battle, visiting houses, city watch mode, and special menus. `UIMainFunction` controls collapsed/expanded menu visibility independently; the small right-bottom starter icons are expected even when the other icons require clicking the menu expansion control.

The current staging client already had five historical targeted table edits dated 2026-10-10 (IDs 62,65,63,23,66). We took that *exact existing* source as baseline; did not restore or undo those earlier changes.

## Changed versus intentionally protected records

Script: `tools/patch_dev_ui_icon_menu_unlock_20261012.py`.

- **50 menu icon entries** in menu groups 1,2,3 have their show-level capped at 1, show mission flag cleared, required level cleared, and required mission flag cleared (only when a field was nonzero). 146 individual bytes differ. Includes standard gameplay/character menus and optional event icon records. The runtime server-switch mask and actual event/timer checks in Lua are **untouched**; opening an icon is not proof its server functionality is implemented or an event is active.
- **Record ID32, Teleport** (group3, kind32) keeps original showFlag11804 and requireFlag11804, preserving the user's earlier request to keep world-map/teleport locked until its genuine mainline mission unlock.
- All **group0** avatar/ghost flag records (IDs228–242), **group5/6 combat controls**, and original icon graphics, labels, animation names, icons' server-switch masks, active-activity time checks, house/battle permissions and Quest MarkManager are unchanged.
- No source Lua runtime edits. No edits to tracked original `File.dat/Data-ios/IOS_UiOpen_C.dat` (SHA256 af759749...), avoiding shipping a DEV cheat as source data for a live environment.

## Exact binaries & integrity

- Staging `AssetBundles/Data/IOS_UiOpen_C.dat`, `tsnpc_Data/StreamingAssets/Data/UiOpen_C.dat`: each length2978, baseline SHA256 **e5d123bb74520866f2002643ac90606432ea5dbd7240c8064c8e4e23f366eb48** → DEV unlock SHA256 **d3a30b6ae1c6e67454df8218d7a2fea7ed705f29d8180fb3cb528db4b4fb7980**.
- Each file gets its own timestamped backup suffix `.before-dev-icon-unlock-...bak` and is atomically replaced only if the full baseline SHA matches. Refuse unknown input/output SHA; patch only the four UI gate columns; independently reparse records and verify other columns and protected IDs unchanged.
- Existing DEV Windows PC `C:/Server-Game/tsm_dodo/File.dat/Data/UiOpen_C.dat` was same pre-patch SHA e5d123..., and must be backed up before syncing the same DEV table. DEV `File.dat/Data-ios/IOS_UiOpen_C.dat` is intentionally left original.
- CI/standalone `tools/test_dvt_dev_ui_icon_unlock_20261012.py` **recreates old five historical edits from the tracked unmodified original** (asserts exact pre-patch SHA), checks exactly 50 affected records, 146 byte differences, no map/teleport, group0, combat changes, all non-gate bytes untouched, fail-closed unknown file, backup and idempotence, and runtime checks still intact.

## What to expect when manually testing

Exit the client fully and launch the **staging** executable again so `UiOpen_C.dat` reloads. Open the lower-right menu using its expansion arrow/icon; many group3 menu functions are hidden while the menu is collapsed by normal `UIMainFunction.showAll` behavior, not by a lock. Menu icons that still do not appear may be gated by runtime server-switch masks, special event schedules, map/house/battle mode or actual asset/server support — this data-only patch does not claim every event is active or every service fully implemented.

Check ordinary icon visibility and clickability at level1 without mission flags; confirm Teleport remains quest-locked, no unauthorized map unlocks, no mission stage skips, no free resources, and no crash on unsupported event screens. Only a future manual E2E pass can establish the full UI and server functionality for each menu.
DEV deployment 2026-10-12 16:55: PC file C:\Server-Game\tsm_dodo\File.dat\Data\UiOpen_C.dat was patched to d3a30b6ae1c6e67454df8218d7a2fea7ed705f29d8180fb3cb528db4b4fb7980; backup C:\Server-Game\tsm_dodo\backups\dev_ui_icons_20261012_20261012_165522\UiOpen_C.dat. Restart the staging client to reload icons, not the JVM.
