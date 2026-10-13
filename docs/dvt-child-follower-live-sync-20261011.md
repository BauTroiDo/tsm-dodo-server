# DVT child follower live S069-009 resync on roster transfers — 2026-10-11

## Root-cause proof

In original decrypted DVT Lua, `Common/protocal.lua protocolTable[69][9]` parses the **1-byte followIndex + UInt16 93-byte ChildData**, then calls `Role.SetChildNpc(followIndex)`. `Logic/Role.lua Role.SetChildNpc` selects only an **existing** `Role.followNpcs[Role.playerId][followIndex]`. When the child follower is removed, original `Role.FollowNpcDisappear` calls `SetChildNpc(0)` and clears the client pointer.

The previous server implementation restored **S069-009 only on login**; when an authentically linked adult child was taken from a cart, inn or warrior warehouse while already online, S015 announced the pet but S069-009 never reconnected `Role.childNpc`.

Also, `InnHandler`'s swap path overwrote the existing S015 follower slot without first sending a removal. This could leave a stale client object/pointer.

## Implemented

- `DvtChildFollowerLiveSync.afterCarriedPetAppeared` is invoked strictly **after** sending `S015` for a newly returned/swapped follower. It checks an existing `ChildRepository.loadState`, verifies a **pre-existing child profile** and the exact authenticated `petInstanceId`, and passes the complete current roster (follow+cart+inn+warehouse) to `ChildFollowerBindingPolicy.followIndex`.
- Only genuine adult `kind=3`, a unique owned UUID, consistent carried slot and valid capacity produce `S069-009`. Other pets and all unbound/missing/child/dead/ambiguous profiles produce **no extra packet**. No child state or UUID binding is created by the live-sync helper.
- Integrated into the **cart retrieve/swap** (`FollowNpcHandler`), **inn retrieve/swap** (`InnHandler`), and **warrior warehouse retrieve** (`WarriorRepositoryHandler`) paths.
- In **inn swap**, send old S015 follower removal BEFORE the new S015 follower appearance so DVT Lua clears stale child selection correctly.
- Tests `DvtChildFollowerLiveSyncTest` verify exact S069-009 framing, adult authorized link, same-template but different UUID rejection, no repository/no record, juvenile/dead/no-child rejection, duplicate storage UUID rejection, follow-capacity validation, and handler integration.

## Safety and remaining work

The active DEV server previously had **zero** genuine `character_child_states` rows, so this patch does not create or activate children. There is **no** automatic adoption, reward, item spending, class17 unlock, or mission11980/11981 advance. The Lua `Se_ChildForm.appear` invoked by S036-023 remains commented out; a working client raising/creation flow has not been sourced. The synchronization fix is a verified prerequisite for when real authenticated child profiles exist. E2E adoption cannot be claimed without an actual DVT child and validated creation semantics.

The client removes the existing child pointer via its own original S015 removal handler; the server doesn't invent a wire-level clearing command.
