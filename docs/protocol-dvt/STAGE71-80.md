# Batch Steps 71–80 — Independent battle status groups

## Step 71 — Status container
BattleFighter now owns groupedStatuses keyed by EFightStatusKind-compatible group id while retaining the old scalar fields as a compatibility view.

## Step 72 — Seal/Faint coexistence
A fighter can now retain independent modeled groups instead of every new status overwriting the previous status.

## Step 73 — Stun evaluation
isStunned checks every grouped status. A stun in Seal remains effective even if another Faint/Vary status is also present.

## Step 74 — Apply by group
BattleSkillResolver maps the proven concrete status to its modeled group and replaces only that group's entry.

## Step 75 — Dispel by group
Dispel clears every client-proven group for that skill and leaves unrelated groups intact.

## Step 76 — Duration per group
Round-end duration decrement iterates every active group independently. A status applied this round still starts consuming duration from the next round.

## Step 77 — Restore packet per group
Each expired group emits its own StatusRestore(col,row,statusKind,sourceSkillId), matching RevRestoreStatus's group + skillId payload.

## Step 78 — Legacy compatibility view
statusEffect/statusRounds/statusSkillId/statusAppliedRound remain synchronized to a deterministic primary group so unmigrated code compiles while grouped logic becomes authoritative.

## Step 79 — Revive cleanup
Revive clears all modeled status groups before restoring HP, matching client death/revive lifecycle more closely than clearing one scalar status.

## Step 80 — Build gate
Run ./gradlew test build. Next batch can migrate remaining scalar readers and audit poison/chaos execution against packet/effect evidence.
