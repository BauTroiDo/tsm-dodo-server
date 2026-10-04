package tw.tsm.gamelogic.systems

import tw.tsm.domain.data.DungeonDef
import tw.tsm.domain.data.NpcDef
import tw.tsm.domain.valueobjects.FollowNpcData

/**
 * Server-side enforcement for DVT kind=3 Bách Chiến entry restrictions.
 *
 * Lua Dungeon.CheckForQualification has an always-true guard bug, but its body and
 * S:047-019 define the intended backend contract: restrictions are evaluated against
 * the singular deployed fight NPC. Round limits (need kinds 6/7) are battle objectives,
 * not entry restrictions. Soul kinds 8/9/10 are intentionally unsupported here.
 */
object DvtWeeklyQualification {
    const val OK = 0
    const val RESTRICTION_FAILED = 1
    const val FIGHT_NPC_REQUIRED = 3
    const val UNSUPPORTED = 255

    fun checkKind3(
        def: DungeonDef,
        pet: FollowNpcData?,
        npc: NpcDef?,
    ): Int {
        if (def.kind != DungeonSystem.KIND_WEEKLY) return UNSUPPORTED

        val restrictions = def.needPar.filter { it.kind in 1..5 }
        if (restrictions.isEmpty()) return OK
        if (pet == null || npc == null) return FIGHT_NPC_REQUIRED

        for (need in restrictions) {
            val matches = when (need.kind) {
                1 -> npc.moral == need.parameter
                2 -> npc.element == need.parameter
                3 -> npcTurn(npc) == need.parameter
                4 -> hasSkill(pet, npc, need.parameter)
                5 -> !hasSkill(pet, npc, need.parameter)
                else -> true
            }
            if (!matches) return RESTRICTION_FAILED
        }
        return OK
    }

    private fun npcTurn(npc: NpcDef): Int =
        if (npc.turn == 2) 1 else 0 // exact NpcData:GetTurn()

    private fun hasSkill(pet: FollowNpcData, npc: NpcDef, skillId: Int): Boolean {
        val baseLevels = listOf(pet.skill1Lv, pet.skill2Lv, pet.skill3Lv)
        if (npc.skills.take(3).zip(baseLevels).any { (id, lv) -> id == skillId && lv > 0 }) {
            return true
        }
        return pet.canUseSpecialSkill && npc.specialSkill == skillId
    }
}
