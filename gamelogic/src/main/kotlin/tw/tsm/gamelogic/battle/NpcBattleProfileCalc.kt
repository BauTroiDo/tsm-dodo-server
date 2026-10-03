package tw.tsm.gamelogic.battle

import tw.tsm.domain.data.NpcDef
import tw.tsm.domain.data.SkillDef

/**
 * Snapshot chiến đấu cho NPC/quái từ Npc_C.dat.
 *
 * DVT NpcData.lua giữ các trường HP/SP/INT/ATK/DEF/AGI trực tiếp trong record.
 * PC fallback (BattleNpcAI) xác nhận npc hpmax/spmax được dùng nguyên xi khi vào battle,
 * skill1..skill4 của NPC dùng SkillData.max_lvl, và reborn được lấy trực tiếp từ npc data.
 *
 * Không áp công thức pet/player growth vào wild/event NPC ở đây.
 */
data class NpcBattleProfile(
    val level: Int,
    val maxHp: Long,
    val maxSp: Long,
    val element: Int,
    val atk: Int,
    val def: Int,
    val agi: Int,
    val intelligence: Int,
    val turn: Int,
    val passiveSkillId: Int,
    val passiveSkillLevel: Int,
    val skills: IntArray,
    val skillLevels: Map<Int, Int>,
)

object NpcBattleProfileCalc {
    fun from(
        npc: NpcDef,
        skillDefs: Map<Int, SkillDef>,
    ): NpcBattleProfile {
        // Preserve the four DVT/PC AI slots exactly. Zero holes and duplicate skill IDs
        // are meaningful because TSBattleNPC.seekTarget applies a different modulo gate
        // to skill4 -> skill3 -> skill2 -> skill1. Compressing/deduplicating changes odds.
        val skills = intArrayOf(
            npc.skills.getOrElse(0) { 0 },
            npc.skills.getOrElse(1) { 0 },
            npc.skills.getOrElse(2) { 0 },
            npc.specialSkill,
        )

        // Live DVT RoleController:GetElementSkill/GetSkillLv gives ordinary
        // battlefield NPC skills level 1. PC uses SkillData.max_lvl, but DVT is authoritative.
        // Keep both raw and normalized summon IDs at Lv1 so the resolver sees the same level.
        val levelIds = buildSet {
            skills.asSequence()
                .filter { it > 0 }
                .forEach { rawSkillId ->
                    add(rawSkillId)
                    add(NpcAiSkillRules.normalizeSelectedSkill(rawSkillId))
                }
        }
        val levels = levelIds.mapNotNull { skillId ->
            skillDefs[skillId]?.let { skillId to 1 }
        }.toMap()

        return NpcBattleProfile(
            level = npc.level,
            maxHp = npc.hpBase.toLong(),
            maxSp = npc.spBase.toLong(),
            element = npc.element,
            atk = npc.atk,
            def = npc.def,
            agi = npc.agi,
            intelligence = npc.intelligence,
            turn = npc.turn,
            passiveSkillId = npc.passiveSkill,
            passiveSkillLevel = npc.passiveSkillLv,
            skills = skills,
            skillLevels = levels,
        )
    }
}
