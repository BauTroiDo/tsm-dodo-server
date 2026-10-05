package tw.tsm.gamelogic.systems

import tw.tsm.domain.data.NpcDef
import tw.tsm.domain.data.NpcSoulDef
import tw.tsm.domain.data.SoulDef
import tw.tsm.domain.valueobjects.SoulSkillData
import tw.tsm.domain.valueobjects.SoulThingData
import kotlin.random.Random

/**
 * CUSTOM OFFLINE policy for the two Soul-generation details that the official
 * DVT client/site do not expose: initial six-stat distribution and skill-ID pool.
 *
 * Everything else remains authoritative DVT data:
 * itemId -> NpcSoul, quality, element, startPoint and skill-count probabilities.
 */
class OfflineSoulGenerationPolicy(
    private val soulDefs: Map<Int, SoulDef>,
    private val npcSoulDefs: Map<Int, NpcSoulDef>,
    private val npcDefs: Map<Int, NpcDef>,
) {
    fun generate(itemId: Int, random: Random = Random.Default): SoulThingData? {
        val npcSoul = npcSoulDefs[itemId] ?: return null
        val soulDef = soulDefs[npcSoul.quality] ?: return null
        val npc = npcDefs[npcSoul.npcId] ?: return null
        val element = npc.element
        if (element !in 0..0xFF || soulDef.startPoint < 0) return null

        val stats = IntArray(6)
        repeat(soulDef.startPoint) {
            stats[random.nextInt(stats.size)]++
        }

        val skillCount = soulDef.skillGetProb
            .take(4)
            .count { probability ->
                probability >= 10_000 || (probability > 0 && random.nextInt(10_000) < probability)
            }
            .coerceAtMost(4)

        val chosenSkills = OFFLINE_SOUL_SKILL_POOL
            .shuffled(random)
            .take(skillCount)
            .map { SoulSkillData(id = it, level = 1) }
            .toMutableList()

        while (chosenSkills.size < 4) chosenSkills += SoulSkillData()

        val initialStats = stats.toList()
        return SoulThingData(
            sn = 0L,
            itemId = itemId,
            element = element,
            equipId = 0,
            level = 1,
            starLevel = 0,
            startAtt = initialStats,
            nowAtt = initialStats,
            skills = chosenSkills,
            lockFlags = 0,
        )
    }

    companion object {
        /**
         * CUSTOM OFFLINE whitelist.
         *
         * These IDs are the Vietnamese IOS_Skill_C.dat equivalents of Soul skill
         * examples published by the official DVT Soul-system page:
         * Cây tinh / Kết giới / Hồi sinh / Băng phong / Ẩn mình.
         *
         * We intentionally do not map 暈擊 yet because the localized DAT contains
         * multiple stun-producing skills and there is no one-to-one authoritative ID.
         */
        val OFFLINE_SOUL_SKILL_POOL: List<Int> = listOf(
            10004, // Cây tinh
            10010, // Kết giới
            11013, // Thuật hồi sinh
            11014, // Băng phong
            13005, // Thuật ẩn mình
        )
    }
}
