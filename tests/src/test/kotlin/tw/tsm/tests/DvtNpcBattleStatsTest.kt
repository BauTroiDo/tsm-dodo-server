package tw.tsm.tests

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.data.loaders.NpcDatLoader
import tw.tsm.data.loaders.SkillDatLoader
import tw.tsm.gamelogic.battle.NpcAiSkillRules
import tw.tsm.gamelogic.battle.NpcBattleProfileCalc
import java.nio.file.Files
import java.nio.file.Path

class DvtNpcBattleStatsTest {
    private fun root(): Path =
        generateSequence(Path.of("").toAbsolutePath()) { it.parent }
            .first { Files.exists(it.resolve("File.dat/Data/Npc_C.dat")) }

    private fun serverData(name: String): ByteArray =
        Files.readAllBytes(root().resolve("File.dat/Data/$name"))

    @Test
    fun `wild npc uses direct DVT battle stats without pet growth formula`() {
        val npcs = NpcDatLoader.load(serverData("Npc_C.dat"))
        val skills = SkillDatLoader.load(serverData("Skill_C.dat"))

        val npc = requireNotNull(npcs[10001])
        val profile = NpcBattleProfileCalc.from(npc, skills)

        assertEquals(npc.level, profile.level)
        assertEquals(npc.hpBase.toLong(), profile.maxHp)
        assertEquals(npc.spBase.toLong(), profile.maxSp)
        assertEquals(npc.atk, profile.atk)
        assertEquals(npc.def, profile.def)
        assertEquals(npc.agi, profile.agi)
        assertEquals(npc.intelligence, profile.intelligence)
        assertEquals(npc.element, profile.element)

        if (npc.level > 0 && npc.hpx > 0) {
            assertNotEquals(
                npc.hpBase.toLong() + npc.level.toLong() * npc.hpx,
                profile.maxHp,
            )
        }
        if (npc.level > 0 && npc.spx > 0) {
            assertNotEquals(
                npc.spBase.toLong() + npc.level.toLong() * npc.spx,
                profile.maxSp,
            )
        }
    }

    @Test
    fun `enemy npc keeps reborn and DVT skill level one semantics`() {
        val npcs = NpcDatLoader.load(serverData("Npc_C.dat"))
        val skills = SkillDatLoader.load(serverData("Skill_C.dat"))

        val npc = npcs.values.first {
            it.specialSkill > 0 &&
                it.turn > 0 &&
                skills.containsKey(it.specialSkill)
        }
        val profile = NpcBattleProfileCalc.from(npc, skills)

        assertEquals(npc.turn, profile.turn)
        assertTrue(npc.specialSkill in profile.skills)

        val expectedSkills = listOf(
            npc.skills.getOrElse(0) { 0 },
            npc.skills.getOrElse(1) { 0 },
            npc.skills.getOrElse(2) { 0 },
            npc.specialSkill,
        )

        assertEquals(expectedSkills, profile.skills.toList())
        for (rawSkillId in expectedSkills.filter { it > 0 }.distinct()) {
            val effectiveSkillId = NpcAiSkillRules.normalizeSelectedSkill(rawSkillId)
            if (skills.containsKey(rawSkillId)) {
                assertEquals(1, profile.skillLevels[rawSkillId])
            }
            if (skills.containsKey(effectiveSkillId)) {
                assertEquals(
                    1,
                    profile.skillLevels[effectiveSkillId],
                    "NPC ${npc.name} effective skill=$effectiveSkillId must use live DVT Lv1",
                )
            }
        }
    }

    @Test
    fun `enemy npc AI slots preserve holes and duplicates from DVT Npc_C`() {
        val npcs = NpcDatLoader.load(serverData("Npc_C.dat"))
        val skills = SkillDatLoader.load(serverData("Skill_C.dat"))

        val gapProfile = NpcBattleProfileCalc.from(requireNotNull(npcs[45367]), skills)
        assertEquals(listOf(14080, 0, 0, 21001), gapProfile.skills.toList())

        val duplicateProfile = NpcBattleProfileCalc.from(requireNotNull(npcs[35061]), skills)
        assertEquals(listOf(20052, 20052, 20052, 0), duplicateProfile.skills.toList())
        assertEquals(setOf(20052), duplicateProfile.skillLevels.keys)
    }

    @Test
    fun `live DVT npc data includes AI skills whose SP cost exceeds starting SP`() {
        val npcs = NpcDatLoader.load(serverData("Npc_C.dat"))
        val skills = SkillDatLoader.load(serverData("Skill_C.dat"))
        val npc = requireNotNull(npcs[40456])
        val profile = NpcBattleProfileCalc.from(npc, skills)

        assertEquals(50L, profile.maxSp)
        assertEquals(13025, profile.skills[1])
        assertTrue(requireNotNull(skills[13025]).requireSp > profile.maxSp)

        assertEquals(13015, profile.skills[2])
        val normalizedSummon = NpcAiSkillRules.normalizeSelectedSkill(profile.skills[2])
        assertEquals(13018, normalizedSummon)
        assertTrue(requireNotNull(skills[normalizedSummon]).requireSp > profile.maxSp)
    }

    @Test
    fun `live DVT npc AI normal skills use level one and special non Skill_C ids stay explicit`() {
        val npcs = NpcDatLoader.load(serverData("Npc_C.dat"))
        val skills = SkillDatLoader.load(serverData("Skill_C.dat"))
        val missing = mutableSetOf<Int>()

        npcs.forEach { (npcId, npc) ->
            val profile = NpcBattleProfileCalc.from(npc, skills)
            profile.skills.filter { it > 0 }.forEach { rawSkillId ->
                val effectiveSkillId = NpcAiSkillRules.normalizeSelectedSkill(rawSkillId)
                if (skills[effectiveSkillId] == null) {
                    missing += effectiveSkillId
                } else {
                    assertEquals(
                        1,
                        profile.skillLevels[effectiveSkillId],
                        "NPC $npcId effective skill=$effectiveSkillId must use live DVT Lv1",
                    )
                }
            }
        }

        assertEquals(
            setOf(
                1306, 12000, 45005,
                31001, 31002, 31003, 31004, 31005, 31007, 31008, 31009,
                31010, 31011, 31015, 31021, 31022, 31024,
            ),
            missing,
            "Lock the live DVT Npc_C non-Skill_C inventory; Lua Attack falls these back to HandFight",
        )
    }

    @Test
    fun `npc passive metadata is preserved for battle runtime without inventing behavior`() {
        val npcs = NpcDatLoader.load(serverData("Npc_C.dat"))
        val skills = SkillDatLoader.load(serverData("Skill_C.dat"))

        val npc = npcs.values.first { it.passiveSkill > 0 && it.passiveSkillLv > 0 }
        val profile = NpcBattleProfileCalc.from(npc, skills)

        assertEquals(npc.passiveSkill, profile.passiveSkillId)
        assertEquals(npc.passiveSkillLv, profile.passiveSkillLevel)
    }

    @Test
    fun `all profile fields preserve Npc_C direct combat attributes`() {
        val npcs = NpcDatLoader.load(serverData("Npc_C.dat"))
        val skills = SkillDatLoader.load(serverData("Skill_C.dat"))

        val sample = npcs.entries
            .filter { (_, npc) -> npc.hpBase > 0 && npc.level > 0 }
            .filterIndexed { index, _ -> index % 97 == 0 }
            .take(64)

        assertTrue(sample.isNotEmpty())
        sample.forEach { (_, npc) ->
            val profile = NpcBattleProfileCalc.from(npc, skills)
            assertEquals(npc.hpBase.toLong(), profile.maxHp)
            assertEquals(npc.spBase.toLong(), profile.maxSp)
            assertEquals(npc.atk, profile.atk)
            assertEquals(npc.def, profile.def)
            assertEquals(npc.agi, profile.agi)
            assertEquals(npc.intelligence, profile.intelligence)
            assertEquals(npc.turn, profile.turn)
            assertEquals(npc.passiveSkill, profile.passiveSkillId)
            assertEquals(npc.passiveSkillLv, profile.passiveSkillLevel)
        }
    }
}