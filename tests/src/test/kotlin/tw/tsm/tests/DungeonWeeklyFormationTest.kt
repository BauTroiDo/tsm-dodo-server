package tw.tsm.tests

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tw.tsm.data.loaders.DungeonDatLoader
import tw.tsm.data.loaders.NpcDatLoader
import tw.tsm.gamelogic.systems.DvtWeeklyDungeonFormation

class DungeonWeeklyFormationTest {
    private fun bytes(name: String): ByteArray =
        Files.readAllBytes(Path.of("..", "File.dat", "Data-ios", name))

    @Test
    fun `all twenty DVT weekly stages resolve to ten real NPC rows`() {
        val dungeons = DungeonDatLoader.load(bytes("IOS_Dungeon_C.dat"))
        val npcs = NpcDatLoader.load(bytes("IOS_Npc_C.dat"))
        val weekly = dungeons.values.filter { it.kind == 3 }.sortedBy { it.id }

        assertEquals((18..37).toList(), weekly.map { it.id })
        for (def in weekly) {
            val formation = DvtWeeklyDungeonFormation.enemyIds(def.id)
            assertNotNull(formation, "missing formation for dungeon ${def.id}")
            assertEquals(10, formation!!.size, "dungeon ${def.id}")
            assertEquals(10, formation.distinct().size, "dungeon ${def.id}")
            assertTrue(formation.all(npcs::containsKey), "unknown NPC in dungeon ${def.id}: $formation")
        }
    }

    @Test
    fun `stage eighteen and nineteen match independently named DVT rosters`() {
        val npcs = NpcDatLoader.load(bytes("IOS_Npc_C.dat"))

        assertEquals(
            listOf(
                "Tả Từ", "Vu Cát", "Gia Cát Lượng", "Tư Mã Ý", "Lục Tốn",
                "Cận vệ quân", "Cận vệ quân", "Cận vệ quân", "Cận vệ quân", "Cận vệ quân",
            ),
            DvtWeeklyDungeonFormation.enemyIds(35)!!.map { npcs.getValue(it).name },
        )

        assertEquals(
            listOf(
                "Đổng Trác", "Điêu Thuyền", "Lý Nho", "Lý Biệt", "Lý Xiêm",
                "Quách Tị", "Lý Giác", "Đổng Mân", "Đổng Hoàng", "Ngưu phụ",
            ),
            DvtWeeklyDungeonFormation.enemyIds(36)!!.map { npcs.getValue(it).name },
        )
    }

    @Test
    fun `stage twenty is exactly two Lu Bu for each of five elements`() {
        val npcs = NpcDatLoader.load(bytes("IOS_Npc_C.dat"))
        val ids = DvtWeeklyDungeonFormation.enemyIds(37)!!
        val defs = ids.map(npcs::getValue)

        assertEquals(listOf(40448,40449,40450,40451,40452,40453,40454,40455,40480,40481), ids)
        assertTrue(defs.all { it.name == "Lữ Bố" && it.level == 200 && it.hpBase == 5_291_927 })
        assertEquals(mapOf(1 to 2, 2 to 2, 3 to 2, 4 to 2, 5 to 2), defs.groupingBy { it.element }.eachCount())
    }

    @Test
    fun `round objectives come directly from DVT needPar kinds six and seven`() {
        val dungeons = DungeonDatLoader.load(bytes("IOS_Dungeon_C.dat"))

        assertEquals(3, DvtWeeklyDungeonFormation.objective(dungeons.getValue(21)).winWithinRounds)
        assertEquals(0, DvtWeeklyDungeonFormation.objective(dungeons.getValue(21)).surviveRounds)

        assertEquals(10, DvtWeeklyDungeonFormation.objective(dungeons.getValue(22)).surviveRounds)
        assertEquals(0, DvtWeeklyDungeonFormation.objective(dungeons.getValue(22)).winWithinRounds)

        assertEquals(20, DvtWeeklyDungeonFormation.objective(dungeons.getValue(36)).winWithinRounds)
    }

    @Test
    fun `soul weekly remains deliberately unmapped until its backend grouping is proven`() {
        for (dungeonId in 63..82) {
            assertEquals(null, DvtWeeklyDungeonFormation.enemyIds(dungeonId))
        }
    }
}
