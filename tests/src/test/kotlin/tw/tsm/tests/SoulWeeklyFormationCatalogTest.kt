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
    fun onlyHighConfidenceLateCrazyWeeklyBlocksAreExposed() {
        assertNull(SoulWeeklyFormationCatalog.npcIdsForDungeon(77))
        assertEquals((40823..40828).toList(), SoulWeeklyFormationCatalog.npcIdsForDungeon(78))
        assertEquals((40829..40835).toList(), SoulWeeklyFormationCatalog.npcIdsForDungeon(79))
        assertEquals((40836..40843).toList(), SoulWeeklyFormationCatalog.npcIdsForDungeon(80))
        assertEquals((40844..40850).toList(), SoulWeeklyFormationCatalog.npcIdsForDungeon(81))
        assertEquals((40851..40859).toList(), SoulWeeklyFormationCatalog.npcIdsForDungeon(82))
    }

    @Test
    fun lateBlocksCarryTheExactDvtSkillsThatAnchorTheirIdentity() {
        val npcs = npcs()

        val plaguePig = npcs.getValue(40825)
        assertEquals("Ôn Dịch Trư Tướng", plaguePig.name)
        assertTrue(23019 in plaguePig.skills) // Vạn độc xuyên tâm

        val skeletonKing = npcs.getValue(40829)
        assertEquals("Hủ Hủ Vương", skeletonKing.name)
        assertTrue(23011 in skeletonKing.skills) // Huyền vũ

        val stage18Boss = npcs.getValue(40836)
        assertEquals("Bạo Ngược Vương", stage18Boss.name)
        assertEquals(21042, stage18Boss.specialSkill) // Gia Cát Cuồng Long Chú

        val stage19Caster = npcs.getValue(40849)
        assertEquals("Giảo Hoạt Tiểu Ma", stage19Caster.name)
        assertEquals(21042, stage19Caster.specialSkill)

        val stage20Dragon = npcs.getValue(40858)
        assertEquals("Dị Giới Bạo Ngược Vương", stage20Dragon.name)
        assertEquals(21042, stage20Dragon.specialSkill)
        assertEquals("Dị Giới Hủ Hủ Vương", npcs.getValue(40857).name)
        assertTrue(23011 in npcs.getValue(40857).skills)
        assertEquals(12052, npcs.getValue(40856).specialSkill) // Hấp Hồn Địa Ngục
    }
}
