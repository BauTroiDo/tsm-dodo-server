# Batch Steps 81–90 — Grouped status authority and effect audit

## Step 81 — Ice-bound grouped read
BattleDamageCalc no longer reads the scalar compatibility status for its IceBound damage modifier; it checks groupedStatuses directly.

## Step 82 — Stun grouped authority
BattleFighter.isStunned now derives only from groupedStatuses. Scalar statusEffect remains a compatibility view, not an authority source.

## Step 83 — Scalar audit
Remaining scalar fields are written only by syncLegacyStatusView; battle behavior no longer needs them except compatibility/debug representation.

## Step 84 — NPC target parity
NPC AI previously chose friendly side only for HP-heal skills. Player validation was already corrected to fightWay in Step61.
NPC now treats fightWay 6,7,8,14 as friendly utility, matching client target-side semantics.

## Step 85 — NPC dispel correctness
NPC dispel/utility skills therefore no longer target enemies merely because they are not classified as HP healing.

## Step 86 — Poison source audit
Poison animation/status scripts (BiteSpiritPoisonous, TurtlePoison, PoisonShield and FaintSkill identity) prove presentation and status identity, but not authoritative periodic-damage formula.

## Step 87 — Chaos source audit
Chaos.lua proves the status presentation/rotation and DisChaos animation. It does not contain authoritative confused-target selection/redirect rules.

## Step 88 — No invented poison tick
No poison periodic damage is added in this batch. A formula inferred from animation names would be unsafe and protocol-inaccurate.

## Step 89 — No invented chaos redirect
No random ally/enemy redirect is added without server/result evidence. Identity, duration, group coexistence and dispel remain implemented.

## Step 90 — Build gate
Run ./gradlew test build. Continue protocol/result audit for stateful battle effects after this gate.
