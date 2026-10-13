# DVT 12721 child lifecycle action preflight — 2026-10-11

## Verified behavior in the active client and Eve archive

The current DVT Lua contains `protocolTable[36][23]` for opening the second-generation general raising form, but **`Se_ChildForm.appear` is commented out**. The available Lua is not an active child-adoption interface. `Role.SetChildNpc(followIndex)` only binds an existing follower slot, and `S069-007/009` only restore a valid existing child snapshot. They cannot generate an authoritative parent-child record or follower.

Full scan of active `Eve.emg` at scene **12721** found exactly five server-only child-workflow actions:
- Eve9, condition16: `resultType=7, resultClass=7, parameter=0, pStyle=36, value=0` (one).
- Eve9/18: `resultType=10, resultClass=7, parameter=1, pStyle=22, value=0` (two).
- Eve10, condition15: `resultType=10, resultClass=7, parameter=1, pStyle=23, value=0` (one).
- Eve15: `resultType=10, resultClass=7, parameter=1, pStyle=24, value=0` (one).

The actual **Eve10 condition15** chain includes an earlier `resultType=0/resultClass=2/parameter=11980/pStyle=1/resultValue=30`, **followed by** the unavailable `type10/class7/pStyle23`. Previously `EveResultTypePolicy` silently skipped `resultType=10` because the client has no EventHandler[10], risking a character being marked progressed without the required child action.

## This checkpoint

- Add `DvtChildLifecycleGate` matching the **five exact shapes in scene12721 only**.
- Call it in `EveResultExecutor.canExecuteResults` on the **entire pending chain before item, money, quest or NPC side effects**. On an unsupported action, show a Vietnamese system message and cancel the event, preserving mission state. This does not affect other scenes or unrelated resultType10 uses.
- Regression tests assert all five shapes from current Eve, and specifically the mission11980-mutation-before-pStyle23 ordering. Check that `S036-023`'s UI handler is disabled in the actual checked-in Lua.

## What remains

This is a **data-integrity guard**, not functional adoption. To make mission11980/11981 fully playable requires a supported client UI or a deliberately authored replacement UI; authoritative server-side adoption/raising rules; a legitimate follower NPC link; persistent ChildData mutation and S069 state sync; then verified class17 predicates. Do not fill in missing semantics, silently skip scripts, award fabricated 650xx items, or advance 11980 before these are complete.

The prior ChildData snapshot table is present on DEV and empty (0 rows). No child is created by this patch.
