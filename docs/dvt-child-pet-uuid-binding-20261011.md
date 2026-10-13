# DVT second-generation pet-instance binding — 2026-10-11

## Proven client/server contract

In the original DVT Lua `Common/protocal.lua`, `S069-009` carries a one-byte **followIndex**, UInt16 payload length **93**, and the exact `ChildData`. The handler does `ChildData.New(data)` and `Role.SetChildNpc(followIndex)`. The latter selects an already-existing `Role.followNpcs` entry; it never creates a child. `S069-007` restores a child profile without selecting a pet. `LoginService.executeCharacterEnterSequence` sends `S015-008` (actual carried pets) before `SelectCharHandler` restores the child profile.

The durable pet identity added by commit `d3f083c` uses a per-individual UUID. An NPC type ID is **not** an individual identity. A carried slot or MySQL autoincrement row ID can change when a pet is moved, traded, or resaved.

## Implementation

- Add **`character_child_states.pet_instance_id VARCHAR(36) NOT NULL DEFAULT ''`**. This is additive to the existing 93-byte payload. Empty means **no proven link**, not an implicit association to a matching NPC/slot.
- Add `ChildRepository.loadState(characterId)` returning the exact snapshot plus an optional validated UUID; corrupted IDs fail closed.
- Add server-only `bindAdultToOwnedPet(characterId, petInstanceId)`, which verifies: an existing valid adult `ChildData` profile, a canonical UUID, **exactly one** physical matching `character_pets` row globally and that row's character ownership. Both the child row and the matching pet row are selected `FOR UPDATE`. Existing bindings are write-once, except idempotent retry of the same UUID. No client protocol calls this method; no child is created.
- `ChildFollowerBindingPolicy` requires that the bound UUID match exactly one pet across all of that character's carried/cart/inn/warehouse storage, and match one actual **carried** follower with a consistent 1-based slot and the configured capacity. The profile must be adult (`kind=3`).
- Only after `LoginService` finishes the S015 roster sync does `SelectCharHandler` choose **S069-009** for a validated bound adult, otherwise retain original **S069-007** (or no packet when profile absent). This prevents accidental child pointer selection while a pet is in cart/inn or transferred elsewhere.
- Regression tests use H2 for authorization, missing/young/dead child, globally duplicated UUID, write-once behavior, preserving association through care/child item-use writes, and complete fail-closed slot-selection cases.

## Explicit limits

**This patch alone does not pair a child with a pet** and does not guess which existing NPC is the child. The DEV database had **zero ChildData records** before implementation, and this migration uses an empty default for every character. There is still no verified child creation/adoption or enabled raising UI in the provided DVT client. Do not call `bindAdultToOwnedPet` until a genuine creation/ownership operation is implemented and validated; do not grant child data, items, quest11980/11981 completion, or `conditionClass17` bypass. S069-009 restoring is only exercised on synthetic regression fixtures until an actual child relationship exists. During a live session, re-sync on storage transfers is a separate follow-up requirement; this step restores correct state during login.
