# DVT quest12509 step8 — restore real chef turn-in waypoint (2026-10-12)

## Verified bug

The client `MarkManager.Navigation()` selects the first pending kind3 item collection objective and switches to the mission stage's END target when all three counts are satisfied. DVT `12509/8` had all three vendor condition targets repaired previously, but **END=(0,0,0,0,0)**. Clicking the quest with all three ingredients therefore had no destination.

Existing exact acquisition objectives and verified vendors (unchanged):

- 1 × item26106, vendor 13242/placement1 at (510,400)
- 5 × item26013, vendor 13241/placement1 at (350,400)
- 5 × item26016, vendor 12002/placement4 at (470,600)

In the active `gamedata/Eve.emg`, scene **49944**, NPC type **15421** at **(330,360)**, placement **5**, event **10**:
- Condition #10 requires mission12509, stage8 and is AND-linked with item conditions #11/#12/#13 requiring above counts (`ops2, threshold 0/4/4`).
- Original result chain #10 consumes `26106×1,26013×5,26016×5`; advances quest12509 via original packed effects; grants `46484×1` and starts `12511/12549`. The client/server already own the event exchange; this patch only lets player navigate to the correct chef.
- Another group (#5/#6/#7/#8) also checks ingredients, but event10/cond10 explicitly contains **item deductions**, making NPC15421 the proven turn-in rather than a merchant.

## Strict patch contract

`tools/patch_dvt_12509_food_turnin_20261012.py` verifies Eve NPC, all three conditions and exact effects, the already-patched three vendor destinations, source file SHA, and patches only **8 END bytes** of `12509/8` to `(49944,330,360,1,5)`, with backup and idempotent recheck. No changes to quest steps, ingredient counts, RNG, Eve scripts, player state, Kotlin, gameplay rewards or seller data.

- Git/server file (892087 bytes) `c06c8f2179b78033faac137cd7803d4c054f5f151a8f54df1f013f321972739e` → `6272d35367a8fda2c389380ce4f3aec0c9d1d5c65ddffc542e3cb240da1ac2c3`. Byte offset **767233..767240**, **8 changed bytes**.
- Client staging both copies (928425 bytes) `a810858dc35924ef19e537a6e73f8a684f9433420cd80a7b4aa1b6fea89df80c` → `4d51a729a20694855adfcca266b41d56c5e403d0062be702b8d26f9fb63bde44`; offset **769053..769060**.
- Auditors should report located targets **5727→5728**, blank END targets **267→266**, no missing static NPC/door/scene reference.
- `tools/test_dvt_12509_food_turnin_20261012.py` checks all three vendor+END placements, Lua condition selection and idempotency; the existing vendor patch/test now accepts **only** source-verified chef END, not an arbitrary new target.

## Boundaries

No real character was walked to NPC15421 and no transaction was executed from the client. There may be other quest12509 blockers and older `resultClass13` remains unresolved, but this particular chef Eve group has no class13/11/15 effect. The mission advancement and exact claim must be verified in-game after deployment; do not rewrite packed mission results, add fake items or bypass the existing Eve exchange.
