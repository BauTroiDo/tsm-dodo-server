package tw.tsm.gamelogic.systems

import tw.tsm.domain.data.CollectWarriorSortDef
import tw.tsm.domain.data.DungeonDef
import tw.tsm.domain.data.ExclusiveWeaponDef
import tw.tsm.domain.data.ItemDef
import tw.tsm.domain.data.NpcDef
import tw.tsm.domain.data.NpcUpgradeDef
import tw.tsm.domain.valueobjects.FollowNpcData
import tw.tsm.domain.valueobjects.SoulThingData

/**
 * Evidence-safe entry qualification for DVT weekly dungeons (kind 3/5).
 *
 * Dungeon.CheckForQualification uses DungeonData.needPar:
 * 1 moral, 2 element, 3 turn, 4 required skill, 5 forbidden skill,
 * 6/7 battle-round objectives, 8/9/10 Soul criteria.
 *
 * Kinds 4/5 deliberately mirror the exact DVT Lua, including its unusual rule:
 * when a Soul is equipped, kind4 also requires that Soul itself to contain the
 * required skill; kind5 rejects either GetSkillLv > 0 or a matching Soul skill.
 */
object DvtWeeklyQualification {
    enum class Status {
        OK,
        FIGHT_NPC_REQUIRED,
        RESTRICTION_FAILED,
        UNSUPPORTED,
    }

    data class Result(
        val status: Status,
        val unsupportedKind: Int? = null,
    )

    fun checkEntry(
        dungeon: DungeonDef,
        deployedPet: FollowNpcData?,
        npcDefs: Map<Int, NpcDef>,
        equippedSoul: SoulThingData? = null,
        npcUpgradeDefs: Map<Int, NpcUpgradeDef> = emptyMap(),
        itemDefs: Map<Int, ItemDef> = emptyMap(),
        exclusiveWeaponDefs: Map<Int, Map<Int, ExclusiveWeaponDef>> = emptyMap(),
        collectWarriorSortDefs: List<CollectWarriorSortDef> = emptyList(),
    ): Result {
        if (dungeon.kind !in setOf(DungeonSystem.KIND_WEEKLY, DungeonSystem.KIND_SOUL_WEEKLY)) {
            return Result(Status.UNSUPPORTED)
        }

        val entryNeeds = dungeon.needPar.filter { it.kind !in setOf(0, 6, 7) }
        val supportedKinds = setOf(1, 2, 3, 4, 5, 8, 9, 10)
        val unsupported = entryNeeds.firstOrNull { it.kind !in supportedKinds }
        if (unsupported != null) {
            return Result(Status.UNSUPPORTED, unsupported.kind)
        }
        if (entryNeeds.isEmpty()) {
            return Result(Status.OK)
        }

        val pet = deployedPet ?: return Result(Status.FIGHT_NPC_REQUIRED)
        val npc = npcDefs[pet.npcId] ?: return Result(Status.UNSUPPORTED)
        val soul = equippedSoul?.takeIf { it.equipId == pet.npcId }

        for (need in entryNeeds) {
            val matches = when (need.kind) {
                1 -> npc.moral == need.parameter
                2 -> npc.element == need.parameter
                // DVT NpcData:GetTurn(): raw turn==2 maps to 1; all other values map to 0.
                3 -> (if (npc.turn == 2) 1 else 0) == need.parameter
                4 -> {
                    val skillLv = DvtFollowNpcSkillLookup.getSkillLevel(
                        pet = pet,
                        npc = npc,
                        skillId = need.parameter,
                        npcUpgradeDefs = npcUpgradeDefs,
                        itemDefs = itemDefs,
                        exclusiveWeaponDefs = exclusiveWeaponDefs,
                        collectWarriorSortDefs = collectWarriorSortDefs,
                        equippedSoul = soul,
                    )
                    val soulHasSkill = DvtFollowNpcSkillLookup.hasSoulSkill(soul, need.parameter)
                    skillLv > 0 && (soul == null || soulHasSkill)
                }
                5 -> {
                    val skillLv = DvtFollowNpcSkillLookup.getSkillLevel(
                        pet = pet,
                        npc = npc,
                        skillId = need.parameter,
                        npcUpgradeDefs = npcUpgradeDefs,
                        itemDefs = itemDefs,
                        exclusiveWeaponDefs = exclusiveWeaponDefs,
                        collectWarriorSortDefs = collectWarriorSortDefs,
                        equippedSoul = soul,
                    )
                    val soulHasSkill = DvtFollowNpcSkillLookup.hasSoulSkill(soul, need.parameter)
                    skillLv <= 0 && !soulHasSkill
                }
                8 -> soul?.element == need.parameter
                9 -> soul != null && soul.level >= need.parameter
                10 -> soul != null && soul.level <= need.parameter
                else -> return Result(Status.UNSUPPORTED, need.kind)
            }
            if (!matches) return Result(Status.RESTRICTION_FAILED)
        }
        return Result(Status.OK)
    }
}
