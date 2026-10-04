package tw.tsm.tests

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tw.tsm.data.loaders.EveDataLoader
import tw.tsm.gamelogic.systems.EveConditionResolver

class EveGroupDataRegressionTest {
    private fun scenes() = EveDataLoader.load(
        Files.readAllBytes(Path.of("..", "File.dat", "CompreseData", "Eve.emg"))
    )

    @Test
    fun `treasure dungeon entry keeps one weighted show result per DVT group`() {
        val allScenes = scenes()
        val scene = allScenes.getValue(62003)
        val results = scene.npcEvents.getValue(1).conditions.first().results
        val filtered = EveConditionResolver(allScenes).applyGroupData(results, scene.groupDatas)

        assertTrue(filtered.any { it.resultGroupNo == 0 && it.resultNo == 1 })
        assertTrue(filtered.any { it.resultGroupNo == 0 && it.resultNo == 14 })

        val expectedMembers = mapOf(
            20 to setOf(2, 3),
            21 to setOf(4, 5),
            22 to setOf(6, 7),
            11 to setOf(8, 9, 10, 11, 12, 13),
        )
        for ((groupNo, members) in expectedMembers) {
            val selected = filtered.filter { it.resultGroupNo == groupNo }
            assertEquals(1, selected.size, "group $groupNo must pick exactly one DVT member")
            assertTrue(selected.single().resultNo in members)
            assertEquals(3, selected.single().resultClass)
            assertEquals(4, selected.single().parameterStyle)
        }
        assertEquals(6, filtered.size)
    }

    @Test
    fun `BaDauYeu relocation keeps one valid next-location result`() {
        val allScenes = scenes()
        val scene = allScenes.getValue(62007)
        val condition = scene.npcEvents.getValue(2).conditions.first { it.conditionNo == 8 }
        val filtered = EveConditionResolver(allScenes).applyGroupData(condition.results, scene.groupDatas)

        assertTrue(filtered.any { it.resultGroupNo == 0 && it.resultNo == 1 })
        val next = filtered.filter { it.resultGroupNo == 3 }
        assertEquals(1, next.size)
        assertTrue(next.single().resultNo in 2..5)
        assertEquals(3, next.single().resultClass)
        assertEquals(4, next.single().parameterStyle)
        assertEquals(2, filtered.size)
    }

    @Test
    fun `DVT PickMember is a pick-count flag rather than a result number`() {
        val groups = scenes().values.flatMap { it.groupDatas.values }.filter { it.pickMember > 0 }
        assertTrue(groups.isNotEmpty())
        assertEquals(setOf(1), groups.map { it.pickMember }.toSet())
        assertTrue(groups.any { it.pickMember !in it.memberNoAy })
    }
}
