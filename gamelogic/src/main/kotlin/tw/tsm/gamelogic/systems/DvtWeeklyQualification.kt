package tw.tsm.gamelogic.systems

import tw.tsm.domain.data.DungeonDef
import tw.tsm.domain.data.NpcDef
import tw.tsm.domain.valueobjects.FollowNpcData
import tw.tsm.domain.valueobjects.SoulThingData

/**
 * Evidence-safe entry qualification for DVT weekly dungeons (kind 3/5).
 *
 * Dungeon.CheckForQualification uses DungeonData.needPar:
 * 1 moral, 2 element, 3 turn, 4 required skill, 5 forbidden skill,
 * 6/7 battle-round objectives, 8/9/10 Soul criteria.
 *
 * Kinds 1..3 and Soul gates 8..10 are evidence-complete. Full skill checks
 * (4/5) still require the complete RoleController:GetSkillLv contract including
 * special/upgrade/exclusive-weapon/passive skills, so those remain UNSUPPORTED.
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
    ): Result {
        if (dungeon.kind !in setOf(DungeonSystem.KIND_WEEKLY, DungeonSystem.KIND_SOUL_WEEKLY)) {
            return Result(Status.UNSUPPORTED)
        }

        val entryNeeds = dungeon.needPar.filter { it.kind !in setOf(0, 6, 7) }
        val supportedKinds = setOf(1, 2, 3, 8, 9, 10)
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
