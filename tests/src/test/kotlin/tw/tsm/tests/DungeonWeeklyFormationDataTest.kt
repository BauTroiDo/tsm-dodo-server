package tw.tsm.tests

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.data.loaders.NpcDatLoader

class DungeonWeeklyFormationDataTest {
    private fun root(): Path =
        generateSequence(Path.of("").toAbsolutePath()) { it.parent }
            .first { Files.exists(it.resolve("File.dat/Data/Npc_C.dat")) }

    private fun npcs() =
        NpcDatLoader.load(Files.readAllBytes(root().resolve("File.dat/Data/Npc_C.dat")))

    @Test
    fun reconstructedStagesOneThroughNineteenAreTenNpcBlocks() {
        val npcs = npcs()
        for (stage in 1..19) {
            val firstId = 40258 + (stage - 1) * 10
            val block = (firstId until firstId + 10).map { id -> requireNotNull(npcs[id]) }
            assertEquals(10, block.size, "stage=$stage")
            assertTrue(block.all { it.kind == 31 }, "stage=$stage must stay in the DVT activity-NPC block")
        }
    }

    @Test
    fun stageEighteenAndNineteenNamesMatchLiveDvtData() {
        val npcs = npcs()
        assertEquals(
            listOf(
                "Tả Từ", "Vu Cát", "Gia Cát Lượng", "Tư Mã Ý", "Lục Tốn",
                "Cận vệ quân", "Cận vệ quân", "Cận vệ quân", "Cận vệ quân", "Cận vệ quân",
            ),
            (40428..40437).map { requireNotNull(npcs[it]).name },
        )
        assertEquals(
            listOf(
                "Đổng Trác", "Điêu Thuyền", "Lý Nho", "Lý Biệt", "Lý Xiêm",
                "Quách Tị", "Lý Giác", "Đổng Mân", "Đổng Hoàng", "Ngưu phụ",
            ),
            (40438..40447).map { requireNotNull(npcs[it]).name },
        )
    }

    @Test
    fun stageTwentyIsFiveElementPairsOfLevelTwoHundredLuBu() {
        val npcs = npcs()
        val ids = (40448..40455).toList() + listOf(40480, 40481)
        val block = ids.map { requireNotNull(npcs[it]) }
        assertEquals(List(10) { "Lữ Bố" }, block.map { it.name })
        assertTrue(block.all { it.level == 200 })
        assertEquals(
            mapOf(1 to 2, 2 to 2, 3 to 2, 4 to 2, 5 to 2),
            block.groupingBy { it.element }.eachCount(),
        )
    }
}
