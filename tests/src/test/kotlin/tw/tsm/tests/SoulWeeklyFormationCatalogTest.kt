package tw.tsm.tests

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.data.loaders.NpcDatLoader
import tw.tsm.gamelogic.systems.SoulWeeklyFormationCatalog

class SoulWeeklyFormationCatalogTest {
    private fun root(): Path =
        generateSequence(Path.of("").toAbsolutePath()) { it.parent }
            .first { Files.exists(it.resolve("File.dat/Data/Npc_C.dat")) }

    private fun npcs() = NpcDatLoader.load(
        Files.readAllBytes(root().resolve("File.dat/Data/Npc_C.dat"))
    )

    @Test
    fun catalogPartitionsTheDedicatedSoulBlockIntoExactlyTwentyStages() {
        val npcs = npcs()
        val physicalOrder = npcs.keys.filter { it in 40687..40859 }
        val catalogOrder = SoulWeeklyFormationCatalog.allNpcIdsInStageOrder()

        assertEquals(173, physicalOrder.size)
        assertEquals(173, catalogOrder.size)
        assertEquals(physicalOrder, catalogOrder)
        assertEquals((40687..40859).toSet(), catalogOrder.toSet())
        assertEquals(20, (63..82).count { SoulWeeklyFormationCatalog.npcIdsForDungeon(it) != null })
    }

    @Test
    fun stageGroupSizesMatchThePhysicalDvtRecordThemes() {
        assertEquals(
            listOf(7, 7, 7, 10, 10, 10, 10, 10, 10, 10, 4, 10, 11, 10, 10, 6, 7, 8, 7, 9),
            (63..82).map { SoulWeeklyFormationCatalog.npcIdsForDungeon(it)!!.size },
        )
    }

    @Test
    fun publicGuideAnchorsMatchLiveDvtNpcAndSkillData() {
        val npcs = npcs()

        // Stage8: the guide warns that Gongsun enemies use Qinglong.
        val stage8 = SoulWeeklyFormationCatalog.npcIdsForDungeon(70)!!.map(npcs::getValue)
        assertTrue(stage8.count { it.name.contains("Công Tôn") } >= 6)
        assertTrue(stage8.any { 13015 in it.skills })

        // Stage16: pig/plague enemies and poison.
        val stage16 = SoulWeeklyFormationCatalog.npcIdsForDungeon(78)!!.map(npcs::getValue)
        assertTrue(stage16.any { it.name.contains("Trư") })
        assertTrue(stage16.any { 23019 in it.skills })

        // Stage17: skeleton/decay king uses Xuanwu.
        val stage17 = SoulWeeklyFormationCatalog.npcIdsForDungeon(79)!!.map(npcs::getValue)
        assertTrue(stage17.any { it.name.contains("Khô") })
        assertTrue(stage17.any { 23011 in it.skills })

        // Stages18-19: Crazy Dragon spell exists in each group.
        val stage18 = SoulWeeklyFormationCatalog.npcIdsForDungeon(80)!!.map(npcs::getValue)
        val stage19 = SoulWeeklyFormationCatalog.npcIdsForDungeon(81)!!.map(npcs::getValue)
        assertTrue(stage18.any { it.specialSkill == 21042 })
        assertTrue(stage19.any { it.specialSkill == 21042 })

        // Stage20 reuses the late bosses and exposes soul-drain + Xuanwu + Crazy Dragon.
        val stage20 = SoulWeeklyFormationCatalog.npcIdsForDungeon(82)!!.map(npcs::getValue)
        assertTrue(stage20.any { it.specialSkill == 12052 })
        assertTrue(stage20.any { 23011 in it.skills })
        assertTrue(stage20.any { it.specialSkill == 21042 })
    }
}
