# Batch Steps 61–70 — Target legality and dispel semantics

## Step 61 — Target legality uses fightWay
Lua FightRoleController:CheckSkillTargetLegal proves target side rules use SkillData.fightWay.
Friendly target rejects fightWay 1,2,3,11,15,21.
Enemy target rejects fightWay 6,7,8,14.
Server Step35 incorrectly reduced this to heal-vs-attack. Fixed command validation.

## Step 62 — Friendly utility target resolution
BattleSkillResolver now resolves fightWay 6,7,8,14 against friendly side even when the skill is not HP healing. This is required for dispel/buff utility skills.

## Step 63 — Dispel group evidence
FightRoleController SealSkill/VarySkill/FaintSkill/SpecSkill/StrongSkill explicitly maps clear skills to EStatus.None in each status group.
Server now carries these client-proven group lists.

## Step 64 — Modeled dispel execution
With current single-status BattleFighter model, a dispel clears the active status only if its modeled group is one the skill clears.
State reset includes statusEffect/statusRounds/statusSkillId/statusAppliedRound.

## Step 65 — Specific clears
Evidence-backed examples now work server-side:
11015 melt ice; 14007 clear trance; 14014 antidote; 14022 clear chaos; 20007 clear Spec; broad status-clear skills clear all groups they appear in.

## Step 66 — Light summon clear
Client VarySkill explicitly includes 22011..22014 in Vary clear list. Server now honors this for the modeled Vary statuses.

## Step 67 — Dispel does not deal fake damage
Recognized dispel skills emit a successful target result with no attr changes and skip ordinary attack calculation.

## Step 68 — Status application identity gate
After Step51, concrete status is skillId-derived. Status application no longer requires hitStatus > 0, because that old field is not the complete concrete-status namespace. It requires a proven mapped skillId and positive duration.

## Step 69 — Multi-group boundary
Client stores independent Seal/Vary/Faint/Spec/Strong/Passive/Diversity. Server still stores one modeled status. This batch prepares exact group semantics but intentionally avoids a broad data-model migration without full stacking/packet audit.

## Step 70 — Build gate
Run ./gradlew test build before migrating the status container.
