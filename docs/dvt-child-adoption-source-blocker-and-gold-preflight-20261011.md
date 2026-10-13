# DVT child-adoption source blocker and Eve side-effect audit (2026-10-11)

## Exact inputs and version parity

Audited the following user-supplied DVT reference assets directly:

- Active client: `D:\Games\Ts-Offline\TSNPCVN_9PET_STAGING\AssetBundles\CompreseData\Eve.emg`; **SHA-256 `03c84cb565d4bdd10b7506a4d04a918ae590737f80a3e7e4fd37782b9c40b910`**.
- PR #41 checkout `gamedata/Eve.emg`: **same 10,511,898 bytes and same SHA-256**.
- DEV server `C:\Server-Game\tsm_dodo\gamedata\Eve.emg`: **same SHA-256** verified using SSH `tsm-prod`.
- WARNING: `D:\Games\Ts-Offline\TSMobileOnLine\tsm_dodo\gamedata\Eve.emg` is a **different older local copy**, 10,508,784 bytes, SHA-256 `3154357c5967d6573f21219309f479a8f1bf504e7c15582453f0c472c1841fb0`. **Do not deploy or diagnose a version mismatch from that outdated path**. The 12721/12722 child-event shapes checked below happen to agree across both archives, but binary parity is NOT general.

## Actual DVT child UI source is missing

The original extracted `_native_dump_dvt115\_lua_client_decrypted\Common\protocal.lua` contains:

```lua
protocolTable[36][23] = function(data)
  --Se_ChildForm.appear;
end
```

The same disabled callback appears in `luaDVT/Common/protocal.lua` and `TSMlua/lua_decrypted/Common/protocal.lua`. Searching all Lua in these three trees did not reveal **any** definition of `Se_ChildForm` or any concrete child-raising UI implementation. The IL2CPP `dump.cs` search for child UI classes / `Se_ChildForm` also yielded no matching class. `EChild` and `ChildData.New` do exist, and client S069 can **consume** child state; they are not a client-side constructor or an adoption UI.

**Do not merely uncomment the callback**. It will refer to an undefined global and is not a working adoption interface. Reconstructing adoption costs, sex/element selection, child skill mutation and conversion into a genuine general requires real provenance or a separately specified new game feature. This DVT client source alone does not supply the old form or the server-side creation rules.

## Exact Eve child scene chain with hazards

The authoritative active Eve scene 12721 defines the following nonfunctional actions:

| NPC event / condition | Result instruction | Expected role / risk |
|---|---|---|
| 9 / 8 | `type10/class7/param1/pStyle22/value0` | Disabled child-related UI action |
| 9 / 16 | `type7/class7/param0/pStyle36/value0` | Child adoption interaction, S036-023 callback disabled |
| 10 / 15 | `type10/class7/param1/pStyle23/value0` | Comes **AFTER** `type0/class2/param11980/pStyle1/value30`, so skipping silently advances mission11980 |
| 15 / 3 | `type10/class7/param1/pStyle24/value0` | Comes **AFTER** `type0/class5/param0/pStyle2/value60000`, so skipping silently **debits 60,000 gold** without performing care |
| 18 / 15 | `type10/class7/param1/pStyle22/value0` | Second UI path with same blocked operation |

All five are restricted by `DvtChildLifecycleGate` to scene 12721, and `EveResultExecutor.canExecuteResults` runs the **whole-chain** preflight before money/item/quest changes, so they must continue to fail closed until there is a working UI and authorized backend handler. The added `DvtChildLifecycleActionPreflightTest` checks both the mission-advance ordering **and the 60,000 gold debit**. No fake gold cost, hidden wallet debit, child insertion, pet association or quest override is acceptable.

Scene 12721 and scene 12722 also contain **16** `conditionClass=17` gates: class17 is still unevaluated and returns false when the server lacks authoritative child lifecycle state. The client has no implementation of the missing UI to create that state in the first place. Their six precise parameter/operator shapes are already pinned by `DvtChildQuestSpeakerAndClass17AuditTest`.

## Engineering decision

The supported, completed layers are the 93-byte ChildData persistence and S069 wire encoders, authenticated pet-instance UUID binding, and S069-009 resynchronization after follower transfers. Those are prerequisites, **not a working adoption feature**.

The next feasible path requires one of:
1. Recover a **different client release** with an actual `Se_ChildForm` / second-generation raising UI implementation and original server rules, then implement exact protocol and item economy.
2. Explicitly design a **new** child UI and server-side gameplay mechanics as a new feature, clearly distinguished from DVT original, with full client assets and legitimate transactional ownership/item consumption.

Until either exists, do not unlock missions11980/11981 or class17 merely by replacing the missing effects with no-ops. Continue addressing other source-verifiable quest bugs independently of this removed UI.
