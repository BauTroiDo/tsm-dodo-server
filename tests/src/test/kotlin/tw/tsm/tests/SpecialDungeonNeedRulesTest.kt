package tw.tsm.tests

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.data.loaders.DungeonDatLoader
import tw.tsm.domain.data.DungeonNeedDef
import tw.tsm.gamelogic.systems.SpecialDungeonNeedRules

class SpecialDungeonNeedRulesTest {
    private fun root(): Path =
        generateSequence(Path.of("").toAbsolutePath()) { it.parent }
            .first { Files.exists(it.resolve("File.dat/Data-ios/IOS_Dungeon_C.dat")) }

    private fun defs() = DungeonDatLoader.load(
        Files.readAllBytes(root().resolve("File.dat/Data-ios/IOS_Dungeon_C.dat"))
    )

    @Test
    fun needKindsAreSeparatedByLifecyclePhase() {
        assertEquals(SpecialDungeonNeedRules.Phase.NONE, SpecialDungeonNeedRules.phase(DungeonNeedDef(0, 0)))
        for (kind in 1..5) {
            assertEquals(SpecialDungeonNeedRules.Phase.ENTRY, SpecialDungeonNeedRules.phase(DungeonNeedDef(kind, 123)))
        }
        for (kind in 6..7) {
            assertEquals(SpecialDungeonNeedRules.Phase.BATTLE_ROUND, SpecialDungeonNeedRules.phase(DungeonNeedDef(kind, 10)))
        }
        for (kind in 8..10) {
            assertEquals(SpecialDungeonNeedRules.Phase.SOUL_ENTRY, SpecialDungeonNeedRules.phase(DungeonNeedDef(kind, 1)))
        }
        assertEquals(SpecialDungeonNeedRules.Phase.UNKNOWN, SpecialDungeonNeedRules.phase(DungeonNeedDef(99, 1)))
    }

    @Test
    fun bachChienCurrentNeedParametersMatchDvtTable() {
        val defs = defs()
        val expected = listOf(
            18 to listOf(DungeonNeedDef(0, 0), DungeonNeedDef(0, 0)),
            19 to listOf(DungeonNeedDef(0, 0), DungeonNeedDef(0, 0)),
            20 to listOf(DungeonNeedDef(0, 0), DungeonNeedDef(0, 0)),
            21 to listOf(DungeonNeedDef(6, 3), DungeonNeedDef(0, 0)),
            22 to listOf(DungeonNeedDef(7, 10), DungeonNeedDef(0, 0)),
            23 to listOf(DungeonNeedDef(1, 5), DungeonNeedDef(0, 0)),
            24 to listOf(DungeonNeedDef(1, 4), DungeonNeedDef(0, 0)),
            25 to listOf(DungeonNeedDef(1, 1), DungeonNeedDef(0, 0)),
            26 to listOf(DungeonNeedDef(1, 2), DungeonNeedDef(0, 0)),
            27 to listOf(DungeonNeedDef(1, 3), DungeonNeedDef(0, 0)),
            28 to listOf(DungeonNeedDef(4, 12003), DungeonNeedDef(0, 0)),
            29 to listOf(DungeonNeedDef(3, 0), DungeonNeedDef(0, 0)),
            30 to listOf(DungeonNeedDef(2, 2), DungeonNeedDef(0, 0)),
            31 to listOf(DungeonNeedDef(2, 4), DungeonNeedDef(0, 0)),
            32 to listOf(DungeonNeedDef(2, 1), DungeonNeedDef(0, 0)),
            33 to listOf(DungeonNeedDef(2, 3), DungeonNeedDef(0, 0)),
            34 to listOf(DungeonNeedDef(3, 1), DungeonNeedDef(0, 0)),
            35 to listOf(DungeonNeedDef(5, 10009), DungeonNeedDef(0, 0)),
            36 to listOf(DungeonNeedDef(6, 20), DungeonNeedDef(0, 0)),
            37 to listOf(DungeonNeedDef(0, 0), DungeonNeedDef(0, 0)),
        )

        for ((id, needs) in expected) {
            val def = defs.getValue(id)
            assertEquals(3, def.kind)
            assertEquals(needs, def.needPar, "dungeonId=$id")
        }
    }

    @Test
    fun crazyWeeklyCurrentNeedParametersAreVersionLocked() {
        val defs = defs()
        val expected = listOf(
            63 to listOf(DungeonNeedDef(8, 1), DungeonNeedDef(5, 10004)),
            64 to listOf(DungeonNeedDef(8, 2), DungeonNeedDef(5, 11014)),
            65 to listOf(DungeonNeedDef(8, 3), DungeonNeedDef(5, 12012)),
            66 to listOf(DungeonNeedDef(8, 4), DungeonNeedDef(5, 13002)),
            67 to listOf(DungeonNeedDef(8, 2), DungeonNeedDef(5, 20026)),
            68 to listOf(DungeonNeedDef(8, 3), DungeonNeedDef(5, 12034)),
            69 to listOf(DungeonNeedDef(8, 4), DungeonNeedDef(5, 13015)),
            70 to listOf(DungeonNeedDef(8, 1), DungeonNeedDef(5, 10016)),
            71 to listOf(DungeonNeedDef(9, 10), DungeonNeedDef(8, 8)),
            72 to listOf(DungeonNeedDef(10, 50), DungeonNeedDef(5, 20026)),
            73 to listOf(DungeonNeedDef(9, 10), DungeonNeedDef(5, 20026)),
            74 to listOf(DungeonNeedDef(9, 20), DungeonNeedDef(8, 7)),
            75 to listOf(DungeonNeedDef(9, 30), DungeonNeedDef(5, 23027)),
            76 to listOf(DungeonNeedDef(9, 40), DungeonNeedDef(5, 20026)),
            77 to listOf(DungeonNeedDef(9, 50), DungeonNeedDef(5, 23027)),
            78 to listOf(DungeonNeedDef(2, 3), DungeonNeedDef(0, 0)),
            79 to listOf(DungeonNeedDef(3, 1), DungeonNeedDef(0, 0)),
            80 to listOf(DungeonNeedDef(5, 10009), DungeonNeedDef(0, 0)),
            81 to listOf(DungeonNeedDef(6, 20), DungeonNeedDef(0, 0)),
            82 to listOf(DungeonNeedDef(0, 0), DungeonNeedDef(0, 0)),
        )

        for ((id, needs) in expected) {
            val def = defs.getValue(id)
            assertEquals(5, def.kind)
            assertEquals(needs, def.needPar, "dungeonId=$id")
        }
    }
}
