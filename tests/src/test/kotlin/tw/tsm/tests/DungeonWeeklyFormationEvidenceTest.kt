package tw.tsm.tests

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.data.loaders.NpcDatLoader

/**
 * Locks the DVT client-data evidence used by the Bách Chiến formation audit.
 *
 * This does NOT assert backend positions/order. It only prevents the recovered
 * special NPC blocks from being silently replaced or misread.
 */
class DungeonWeeklyFormationEvidenceTest {
    private val defs = NpcDatLoader.load(
        Files.readAllBytes(Path.of("..", "File.dat", "Data-ios", "IOS_Npc_C.dat"))
    )

    @Test fun `Bach Chien stages 1 through 19 have complete ten-NPC DVT blocks`() {
        for (stage in 1..19) {
            val start = 40258 + (stage - 1) * 10
            val missing = (start until start + 10).filterNot(defs::containsKey)
            assertTrue(missing.isEmpty(), "stage=$stage missing=$missing")
        }
    }

    @Test fun `stage 18 block matches the five named bosses plus five guards`() {
        val ids = (40428..40437).toList()
        assertEquals(
            listOf(
                "Tả Từ", "Vu Cát", "Gia Cát Lượng", "Tư Mã Ý", "Lục Tốn",
                "Cận vệ quân", "Cận vệ quân", "Cận vệ quân", "Cận vệ quân", "Cận vệ quân",
            ),
            ids.map { defs.getValue(it).name },
        )
        assertTrue(ids.all { defs.getValue(it).kind == 31 })
    }

    @Test fun `stage 19 block is the exact ten-enemy Dong Zhuo group`() {
        assertEquals(
            listOf(
                "Đổng Trác", "Điêu Thuyền", "Lý Nho", "Lý Biệt", "Lý Xiêm",
                "Quách Tị", "Lý Giác", "Đổng Mân", "Đổng Hoàng", "Ngưu phụ",
            ),
            (40438..40447).map { defs.getValue(it).name },
        )
    }

    @Test fun `stage 20 data contains five paired Lu Bu elements at identical boss HP`() {
        val ids = (40448..40455).toList() + listOf(40480, 40481)
        val bosses = ids.map(defs::getValue)

        assertTrue(bosses.all { it.name == "Lữ Bố" })
        assertTrue(bosses.all { it.kind == 31 && it.level == 200 })
        assertTrue(bosses.all { it.hpBase == 5_291_927 })
        assertEquals(
            mapOf(1 to 2, 2 to 2, 3 to 2, 4 to 2, 5 to 2),
            bosses.groupingBy { it.element }.eachCount(),
        )

        // IDs 40456/40457 are unrelated low-level NPCs, proving the element-5
        // pair is intentionally separated in the DVT table rather than a blind
        // contiguous 20th block assumption.
        assertNotEquals("Lữ Bố", defs.getValue(40456).name)
        assertNotEquals("Lữ Bố", defs.getValue(40457).name)
    }
}
