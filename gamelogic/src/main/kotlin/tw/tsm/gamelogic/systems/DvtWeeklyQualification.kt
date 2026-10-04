package tw.tsm.gamelogic.systems

import tw.tsm.domain.data.DungeonDef
import tw.tsm.domain.data.NpcDef
import tw.tsm.domain.valueobjects.FollowNpcData

/**
 * Evidence-safe entry qualification for DVT weekly dungeons (kind 3/5).
 *
 * Dungeon.CheckForQualification uses DungeonData.needPar:
 * 1 moral, 2 element, 3 turn, 4 required skill, 5 forbidden skill,
 * 6/7 battle-round objectives, 8/9/10 Soul criteria.
 *
 * Only kinds 1..3 are resolved here today. Full skill checks (4/5) require the
 * complete RoleController:GetSkillLv contract, including special/upgrade/
 * exclusive-weapon/passive/Soul skills. Soul checks (8..10) require protocol
 * 120 state. Those are deliberately UNSUPPORTED instead of guessed.
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
    ): Result {
        if (dungeon.kind !in setOf(DungeonSystem.KIND_WEEKLY, DungeonSystem.KIND_SOUL_WEEKLY)) {
            return Result(Status.UNSUPPORTED)
        }

        val entryNeeds = dungeon.needPar.filter { it.kind !in setOf(0, 6, 7) }
        val unsupported = entryNeeds.firstOrNull { it.kind !in 1..3 }
        if (unsupported != null) {
            return Result(Status.UNSUPPORTED, unsupported.kind)
        }
        if (entryNeeds.isEmpty()) {
            return Result(Status.OK)
        }

        val pet = deployedPet ?: return Result(Status.FIGHT_NPC_REQUIRED)
        val npc = npcDefs[pet.npcId] ?: return Result(Status.UNSUPPORTED)

        for (need in entryNeeds) {
            val matches = when (need.kind) {
                1 -> npc.moral == need.parameter
                2 -> npc.element == need.parameter
                // DVT NpcData:GetTurn(): raw turn==2 maps to 1; all other values map to 0.
                3 -> (if (npc.turn == 2) 1 else 0) == need.parameter
                else -> return Result(Status.UNSUPPORTED, need.kind)
            }
            if (!matches) return Result(Status.RESTRICTION_FAILED)
        }
        return Result(Status.OK)
    }
}
