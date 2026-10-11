# DVT durable pet instance identity — 2026-10-11

## Why this is required before S069-009 child follower binding

The DVT client uses `Role.SetChildNpc(followIndex)` to point to an existing, actual following general. The server's `PetRepositoryImpl.saveAll` deletes and reinserts the `character_pets` rows on ordinary saves, so the auto-increment database row ID is unstable. Likewise, the NPC ID names a template rather than one owned individual, while `storageType` and `slotIndex` change when generals are transferred among the carried roster, cart, inn and warehouse.

**Never bind ChildData by the row ID, NPC template ID, or a current roster slot.**

## Implemented

- A server-only `FollowNpcData.instanceId` UUID is assigned on creation, preserved by Kotlin `copy()`, and persisted as `character_pets.instance_id VARCHAR(36)`. The original binary client S015 layout is NOT changed.
- Existing records created before this migration receive the column with default empty string. In `PetRepositoryImpl.findByCharacterId`, the row lock and immediate transactional update backfill any missing/invalid/duplicate IDs. Subsequent login reads the same UUID without waiting for autosave.
- `saveAll` stores the UUID despite deleting and reinserting physical DB rows, and rejects missing/noncanonical/duplicate UUIDs **before the destructive delete**.
- `PetRebornSystem` preserves the original instanceId even when the NPC template changes during reincarnation. Roster, cart, inn, storage, exchange and other regular transformations use the existing `copy()` identity preservation.
- H2-backed `DvtPetInstanceIdentityPersistenceTest` covers two owned NPCs of the same template with unique identities, blank-UUID migration and reconnect, move/save/reload with changed physical row IDs, rejected duplicate/missing UUID without deleting the existing roster, and reincarnation identity preservation.

## Deployment precautions

The new column is additive. Back up the entire `character_pets` table or the database before booting an auto-migrating server. No SQL that deletes/rebuilds existing pet records should run as part of the migration. Existing pet data, stats, items and equips must remain intact.

This step **does not create a child**, associate any existing pet with `ChildData`, or send S069-009. The server still lacks an authoritative adoption/raising workflow and a verified mapping from a genuine child to its associated pet-instance UUID. Child-only `conditionClass17` quests remain fail-closed until such an identity binding is proven and stored.