package tw.tsm.gamelogic.battle

import tw.tsm.domain.data.NpcDef
import tw.tsm.domain.data.SkillDef

/**
 * Snapshot chiến đấu cho NPC/quái từ Npc_C.dat.
 *
 * DVT NpcData.lua giữ các trường HP/SP/INT/ATK/DEF/AGI trực tiếp trong record.
 * PC fallback (BattleNpcAI) xác nhận npc hpmax/spmax được dùng nguyên xi khi vào battle
 * và reborn được lấy trực tiếp từ npc data. PC gọi field gốc [35] là skill4; DVT Lua
 * vẫn đánh số field này là [35] nhưng đặt tên specialSkill, sau ba skill [25-27].
 * DVT iOS đã lược các field drop [28-34], nên [35] nằm liền sau ba skill trong stream.
 *
 * Mức skill của enemy NPC theo live DVT là Lv1 (không dùng PC SkillData.max_lvl).
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
        // Preserve the four server-AI slots exactly. DVT field [35] specialSkill is
        // positionally the PC field named skill4; PC TSBattleNPC.seekTarget is the recovered
        // server-side AI implementation and applies the fourth-slot modulo gate first.
        // Role.maxNpcSkill=3 describes the ordinary client-visible base-skill array and does
        // not redefine the separate [35] field. Zero holes/duplicates must not be compressed.
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
