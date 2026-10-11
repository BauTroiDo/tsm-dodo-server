# DVT ChildData item-use adulthood: exact 399 → 400 transition (2026-10-11)

## Authoritative source

The staged DVT client’s `Common/protocal.lua`, `protocolTable[69][8]`, receives **S:069-008** (UInt16 item-use total), sets `Role.playerData.child.childItemCount` to that value and changes `kind` to `EChild.Adults` once the count is **≥400**. `protocolTable[69][11]` reads **S:069-011** (UInt8 kind). `Data/HumanData.lua ChildData.New` confirms `kind` values **0=None, 1=Foster, 2=Died, 3=Adults**. Note `ChildData` does NOT store the child’s combat level.

## Implemented in this checkpoint

- `ChildDataSnapshot.withItemCount(newTotal)` makes a fresh 93-byte snapshot, writes the UInt16 value to **byte offsets 72–73**, makes kind **3** at ≥400, preserves every other byte including 18 reserved bytes, and refuses no-child/overflow/decreasing input. Tighten the binary decoder to reject undefined kind 4.
- `ChildRepository.setItemUseTotal(characterId,newTotal)` and MySQL implementation use `SELECT ... FOR UPDATE` to protect one child’s monotonic total; no row or a None child returns null, no implicit child creation. Persistence retains the exact updated bytes.
- `ChildPackets.itemUseTotal` prepares the verified **S069-008** envelope `[69,8,0,totalLE(2)]` and `ChildPackets.lifecycleKind` prepares **S069-011** `[69,11,0,kind(1)]`.
- Extend `DvtChildDataWirePersistenceContractTest` to cover **399 vs 400**, 65535 upper boundary, the exact 93-byte result, reserved byte preservation, immutable source, outbound packet layouts, rejection of no-child updates, and rejection of invalid kind 4.

## Critical non-goals / production safety

The original current DVT client’s `S:036-023` raising UI has been commented out; it has **no** supported replacement UI in the included Lua. The real player input/item economy rules for feeding are not reconstructed. Therefore there is **no new user-facing item-use command**, no NPC loot/item spending shortcut and **no automatic call** to `setItemUseTotal` during gameplay. This checkpoint is a verified state transition and persistence API for future **server-authorized** child-care actions, not a claim that users can adopt, raise, or summon a child today. The real follower slot, S069-009 pairing, 16 class17 conditions, and quests 11980/11981 remain blocked until the authentic authorizing flow exists. Existing player records are not created/modified by this source change.

Reference: `docs/dvt-child-adoption-unimplemented-actions-20261011.md` and the source-grounded `docs/dvt-childdata-s069-persistence-20261011.md`.
