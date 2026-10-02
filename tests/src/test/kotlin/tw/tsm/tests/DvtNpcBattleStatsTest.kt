package tw.tsm.tests

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.data.loaders.NpcDatLoader
import tw.tsm.data.loaders.SkillDatLoader
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
    fun `enemy npc keeps reborn and fourth special skill at max level`() {
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

        val expectedSkills = buildList {
            npc.skills.filterTo(this) { it > 0 }
            add(npc.specialSkill)
        }.distinct()

        assertEquals(expectedSkills, profile.skills.toList())
        for (skillId in expectedSkills) {
            assertEquals(
                skills[skillId]?.maxLv?.coerceAtLeast(1) ?: 1,
                profile.skillLevels[skillId],
                "NPC ${npc.name} skill=$skillId must use SkillData.maxLv",
            )
        }
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
        }
    }
}