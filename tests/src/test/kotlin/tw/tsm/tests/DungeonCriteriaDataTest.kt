package tw.tsm.tests

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.data.loaders.DungeonCriteriaDatLoader

class DungeonCriteriaDataTest {
    private fun root(): Path =
        generateSequence(Path.of("").toAbsolutePath()) { it.parent }
            .first { Files.exists(it.resolve("File.dat/Data-ios/IOS_DungeonCriteria_C.dat")) }

    private fun defs() = DungeonCriteriaDatLoader.load(
        Files.readAllBytes(root().resolve("File.dat/Data-ios/IOS_DungeonCriteria_C.dat"))
    )

    @Test
    fun exactDvtCriteriaTableLoadsWithoutTrailingBytes() {
        val defs = defs()
        assertEquals(34, defs.size)

        assertEquals(
            listOf(3, 1, 1, 1L, 0L),
            requireNotNull(defs[1]).let {
                listOf(it.playerOrNpc, it.kind, it.compare, it.value, it.value2)
            },
        )
        assertEquals(
            listOf(3, 2, 1, 1L, 3L),
            requireNotNull(defs[6]).let {
                listOf(it.playerOrNpc, it.kind, it.compare, it.value, it.value2)
            },
        )
        assertEquals(
            listOf(5, 4, 1, 10001L, 0L),
            requireNotNull(defs[15]).let {
                listOf(it.playerOrNpc, it.kind, it.compare, it.value, it.value2)
            },
        )
        assertEquals(
            listOf(3, 7, 4, 10L, 0L),
            requireNotNull(defs[25]).let {
                listOf(it.playerOrNpc, it.kind, it.compare, it.value, it.value2)
            },
        )
        assertEquals(
            listOf(3, 7, 2, 10L, 0L),
            requireNotNull(defs[26]).let {
                listOf(it.playerOrNpc, it.kind, it.compare, it.value, it.value2)
            },
        )
    }

    @Test
    fun currentClientCriteriaKindsStayWithinKnownDvtSet() {
        assertEquals(
            setOf(1, 2, 3, 4, 6, 7),
            defs().values.map { it.kind }.toSet(),
        )
    }
}
