package tw.tsm.tests

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tw.tsm.data.loaders.DungeonDatLoader

/** Locks DVT weekly DungeonData.needPar without enabling weekly battle semantics. */
class DungeonWeeklyNeedParDataTest {
    private fun weekly() = DungeonDatLoader.load(
        Files.readAllBytes(Path.of("..", "File.dat", "Data-ios", "IOS_Dungeon_C.dat")),
    )

    @Test
    fun `Bach Chien ids eighteen through thirty seven use the recovered needPar matrix`() {
        val defs = weekly()
        assertEquals((18..37).toList(), defs.values.filter { it.kind == 3 }.sortedBy { it.id }.map { it.id })

        fun need(id: Int) = defs.getValue(id).needPar.filter { it.kind != 0 }
            .map { it.kind to it.parameter }

        assertEquals(emptyList<Pair<Int, Int>>(), need(18))
        assertEquals(listOf(6 to 3), need(21))
        assertEquals(listOf(7 to 10), need(22))
        assertEquals(listOf(1 to 5), need(23))
        assertEquals(listOf(1 to 4), need(24))
        assertEquals(listOf(1 to 1), need(25))
        assertEquals(listOf(1 to 2), need(26))
        assertEquals(listOf(1 to 3), need(27))
        assertEquals(listOf(4 to 12003), need(28))
        assertEquals(listOf(3 to 0), need(29))
        assertEquals(listOf(2 to 2), need(30))
        assertEquals(listOf(2 to 4), need(31))
        assertEquals(listOf(2 to 1), need(32))
        assertEquals(listOf(2 to 3), need(33))
        assertEquals(listOf(3 to 1), need(34))
        assertEquals(listOf(5 to 10009), need(35))
        assertEquals(listOf(6 to 20), need(36))
        assertEquals(emptyList<Pair<Int, Int>>(), need(37))
    }

    @Test
    fun `Crazy weekly Soul gates remain explicit instead of being downgraded to normal pet checks`() {
        val defs = weekly()
        val crazy = defs.values.filter { it.kind == 5 }.sortedBy { it.id }
        assertEquals((63..82).toList(), crazy.map { it.id })

        val soulKinds = setOf(8, 9, 10)
        assertTrue((63..77).all { id ->
            defs.getValue(id).needPar.any { it.kind in soulKinds }
        })

        assertEquals(listOf(2 to 3), defs.getValue(78).needPar.filter { it.kind != 0 }.map { it.kind to it.parameter })
        assertEquals(listOf(3 to 1), defs.getValue(79).needPar.filter { it.kind != 0 }.map { it.kind to it.parameter })
        assertEquals(listOf(5 to 10009), defs.getValue(80).needPar.filter { it.kind != 0 }.map { it.kind to it.parameter })
        assertEquals(listOf(6 to 20), defs.getValue(81).needPar.filter { it.kind != 0 }.map { it.kind to it.parameter })
        assertTrue(defs.getValue(82).needPar.all { it.kind == 0 })
    }
}
