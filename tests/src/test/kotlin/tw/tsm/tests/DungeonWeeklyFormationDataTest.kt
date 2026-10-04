package tw.tsm.tests

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tw.tsm.data.loaders.NpcDatLoader

/**
 * Data-only lock for the reconstructed Bách Chiến Thiên Quan NPC membership.
 *
 * This intentionally does NOT define battle positions/order or enable kind=3 combat.
 * Stages 1..19 are the structured ten-NPC DVT block 40258..40447; stage 20 uses
 * the four contiguous elemental pairs plus the non-contiguous fifth-element pair.
 */
class DungeonWeeklyFormationDataTest {
    private fun npcs() = NpcDatLoader.load(
        Files.readAllBytes(Path.of("..", "File.dat", "Data-ios", "IOS_Npc_C.dat")),
    )

    private fun membership(stage: Int): List<Int> = when (stage) {
        in 1..19 -> {
            val first = 40258 + (stage - 1) * 10
            (first until first + 10).toList()
        }
        20 -> listOf(40448, 40449, 40450, 40451, 40452, 40453, 40454, 40455, 40480, 40481)
        else -> emptyList()
    }

    @Test
    fun `all reconstructed Bach Chien stages reference ten real DVT NPC rows`() {
        val defs = npcs()
        for (stage in 1..20) {
            val ids = membership(stage)
            assertEquals(10, ids.size, "stage $stage")
            assertEquals(10, ids.distinct().size, "stage $stage duplicate membership")
            assertTrue(ids.all(defs::containsKey), "stage $stage has unknown NPC: $ids")
        }
    }

    @Test
    fun `stage eighteen and nineteen lock independently corroborated named rosters`() {
        val defs = npcs()

        assertEquals(
            listOf(
                "Tả Từ", "Vu Cát", "Gia Cát Lượng", "Tư Mã Ý", "Lục Tốn",
                "Cận vệ quân", "Cận vệ quân", "Cận vệ quân", "Cận vệ quân", "Cận vệ quân",
            ),
            membership(18).map { defs.getValue(it).name },
        )

        assertEquals(
            listOf(
                "Đổng Trác", "Điêu Thuyền", "Lý Nho", "Lý Biệt", "Lý Xiêm",
                "Quách Tị", "Lý Giác", "Đổng Mân", "Đổng Hoàng", "Ngưu phụ",
            ),
            membership(19).map { defs.getValue(it).name },
        )
    }

    @Test
    fun `stage twenty is five elemental Lu Bu pairs without asserting positions`() {
        val npcDefs = npcs()
        val defs = membership(20).map(npcDefs::getValue)

        assertTrue(defs.all { it.name == "Lữ Bố" && it.level == 200 && it.hpBase == 5_291_927 })
        assertEquals(
            mapOf(1 to 2, 2 to 2, 3 to 2, 4 to 2, 5 to 2),
            defs.groupingBy { it.element }.eachCount(),
        )
    }
}
