# DVT `resultClass=11` — confirmed original organization resources (2026-10-11)

## Proven source semantics

Original client `lua/Logic/Organization.lua` defines:

```lua
EOrgResource = {
  Gold = 1,   -- gold in the organization treasury
  Grain = 2,  -- grain
  Mineral = 3,-- mineral
  Wood = 4,   -- lumber
  Cloth = 5,  -- cloth
  Army = 6,
  Weapon = 7,
}
```

`lua/Logic/City.lua` implements `City.GetResourceRate(orgId, resourceKind)`,
cross-referencing `EOrgResource.Gold/Grain/Mineral/Wood/Cloth` to five
city production stations: Business/Barn/Pit/Lumbering/Drapery.

The active `gamedata/Eve.emg` (SHA
`03c84cb565d4bdd10b7506a4d04a918ae590737f80a3e7e4fd37782b9c40b910`)
contains **35** `resultType=0/resultClass=11` effects. All
have `parameter=0`, `resultMeanNo=0`, `parameterStyle` from 1–5
and positive `resultValue`. Exactly **7 selected event/condition
chains**, with **5 resources per chain**:

| Scene | Event/condition | Resource amounts in style order 1..5 | Other consequences in SAME chain |
| --- | --- | --- | --- |
| 11572 | 2/1 | 50, 100, 100, 100, 100 | Complete 10338, start 10339 |
| 26522 | 5/10 | 100, 100, 100, 100, 100 | Complete 11574, start 11577 |
| 26525 | 10/6 | 500, 1000, 1000, 300, 100 | Item 57009; complete 11574, start 11575 |
| 26525 | 10/8 | 500, 1000, 1000, 300, 100 | Item 57010; complete 11574, start 11575 |
| 26525 | 10/10 | 500, 1000, 1000, 300, 100 | Item 57011; complete 11574, start 11575 |
| 26525 | 10/12 | 500, 1000, 1000, 300, 100 | Item 57012; complete 11574, start 11575 |
| 57523 | 31/7 | 200, 300, 300, 300, 300 | Items 19762 and 20617; complete 11014, start 11015 |

This confirms five guild/organization treasury resources, **not**
individual player gold, player inventory items, or five independent
character achievements. Because each bundle also changes mission
progress or inventory, an engine must apply these operations
transactionally and idempotently, or reject the WHOLE effect chain
before any partial gifts or quest step change.

## Server limitation

The current Kotlin server does not yet have the authoritative
organization membership/treasury store and organization update packets
required to identify which organization receives the resource.
Granting `EOrgResource.Gold` to `MoneySystem` would be a material
economic bug. Merely suppressing the five missing effects and completing
the mission would steal the guild's intended reward.

The existing `EveResultExecutor.canExecuteResults` correctly refuses
`resultClass=11` before mutation. This checkpoint **does NOT make
organization grants executable**.

## Code changes

- `DvtOrgResourceResultPolicy`: source-backed, *non-mutating* decoder
  for `resultType0/class11/param0`, with exact five enum mappings;
  rejects unsupported styles/shapes; `isExecutable=false` until
  organization ownership, storage and packets exist.
- `EveResultExecutor` now gives the player a specific explanation when
  this unsupported chain is reached: resources are organization-owned
  and neither inventory nor quest progress is partially granted.
- `DvtOrganizationQuestResourceContractTest`: verifies exact client Lua
  values, all 35 original effects, seven bundled event conditions and
  rejection of malformed shapes. Prevents future edits from mapping
  class11 Gold into the character wallet.

## Prerequisite for truly fixing the 7 blocked chains

Implement and test authoritative organization membership, treasury
table (Gold/Grain/Mineral/Wood/Cloth), organization ownership/privilege
checks, per-Eve-chain idempotent transaction including item and mission
effects, and original client `S` protocol notifications. Use live
DEV test character joined to a real organization, verify balances
before/after, relog and repeat against Eve replay. Do NOT authorize
user inventory credits as a substitute.

Remaining gaps: 89 `resultClass13` effects lack a reliable package
mapping, 279 `conditionClass14` checks and known MarkStep
non-navigable objectives. User requested quest system first; do not
switch to Gia Viên/Định hôn/second-generation children.
