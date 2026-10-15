# DVT ChildData care-state parity — 2026-10-11

## Original DVT behavior

In Data/HumanData.lua ChildData.New, the 93-byte child record stores:
- stodge at little-endian UInt16 offsets 42 and 43.
- satiation at little-endian UInt16 offsets 52 and 53.

In Common/protocal.lua protocolTable[69][3], the client reads S069-003,
sets child.stodge, and changes child.kind to EChild.Died if and only if
fullness reaches 0. protocolTable[69][4] reads S069-004 for satiation
without changing kind at zero. An increase in fullness alone does not
resurrect a child. S069-008 remains the separate 400 item-use maturity
transition.

## Implemented source-only care-state transition

- ChildDataSnapshot.withCare updates only selected UInt16 fields (and
  kind=2 on zero fullness); preserves all unmentioned record bytes.
- ChildRepository.updateCare executes a validated, locked transaction
  against an existing saved child; refuses a missing/None child record
  without creating a new child.
- ChildPackets.stodge and ChildPackets.satiation build S069-003 / S069-004
  outbound frames with exact five-byte envelopes.
- Expanded in-memory regression tests validate exact byte layout, zero
  fullness death, zero satisfaction non-death, non-resurrection, bounds,
  empty requests and no-child rejection.

## Boundary: still no verified adoption or raising UI

These additions are an explicit server-authored API, not a gameplay action
triggered by clients. The original child-raising UI S036-023 is commented
out; feeding item costs and timers are not yet established from original
DVT server-side evidence. The PetRepositoryImpl.saveAll path deletes and
reinserts character_pets rows; FollowNpcData lacks an immutable pet
instance ID, so neither database row ID nor roster slot/npc type alone
is a reliable permanent link to the child. No S069-009 auto-binding,
adoption, feeding, quest11980/11981 advance or class17 bypass is added.
Do not fabricate child state or accidentally designate an existing
ordinary warrior as a second-generation general.
