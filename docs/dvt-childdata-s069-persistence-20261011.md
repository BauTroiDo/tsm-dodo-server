# DVT S069 ChildData persistence foundation — 2026-10-11

## Implemented scope

Lossless, defensive Kotlin codec ChildDataSnapshot follows Data/HumanData.lua ChildData.New, **93 bytes** exactly. The child level is not included in ChildData and must come from an actual deployed follower; the 18 reserved bytes and both time doubles are retained byte-for-byte.

Wire layout (all numbers little-endian): kind UInt8, sex UInt8, turn UInt8, element UInt8, 10 skills of (ID UInt16 and level UInt8), stodgeOldTime Double, stodge UInt16, satiationOldTime Double, satiation UInt16, six attribute UInt16 values, six attribute experience UInt8 values, childItemCount UInt16, career UInt8 and 18 reserved bytes.

Database: one optional character_child_states record per character, with the original BINARY(93) payload. ChildRepositoryImpl can load/save a validated record transactionally; no default child row is created and no client opcode is accepted as a profile upload. No existing game database rows are modified by this source commit.

Outbound S069 packet encoders:
- S:069-007: main=69, sub=7, reserved=0, UInt16(93), raw 93-byte snapshot. SelectCharHandler sends this only when a valid, non-None character child record exists after completing the normal character login sequence.
- S:069-009: main=69, sub=9, reserved=0, deployed followIndex, UInt16(93), raw 93-byte snapshot. Encoder exists for a future genuine child follower; it is not called automatically before follower parity is understood.

According to Common/protocal.lua, C069-001/002 are child SKILL DELETION requests, not profile creation/upload. They remain unhandled, avoiding client-supplied status forgery.

## Tests and limits

DvtChildDataWirePersistenceContractTest validates the complete synthetic in-memory record, 18 reserved bytes, both encoded S069 packets, defensive copy behavior, and invalid payload rejection. A constructed TEST snapshot is not a real character record.

This patch does NOT create a child, implement feeding/education, bind Role.childNpc, handle deployed child battles, or evaluate conditionClass17's 16 unverified conditions. Mission 11980/11981 remain fail-closed until authoritative parent/child and NPC follower state can be linked without fabricated fields.

Deployment requires Server CI success, controlled DB migration with backup and a DEV restart. The migration only creates an empty table. Client Lua was already independently fixed for class12 speaker events in the previous checkpoint.
