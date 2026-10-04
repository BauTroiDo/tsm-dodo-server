package tw.tsm.tests

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tw.tsm.data.loaders.EveDataLoader
import tw.tsm.gamelogic.systems.EveConditionResolver
import tw.tsm.gamelogic.systems.PlayerEventState

class DungeonMiniGameFlowRegressionTest {
    private fun scenes() = EveDataLoader.load(
        Files.readAllBytes(Path.of("..", "File.dat", "CompreseData", "Eve.emg"))
    )

    @Test
    fun `BaDauYeu event2 follows battle then win then step talk then relocation`() {
        val all = scenes()
        val event = all.getValue(62007).npcEvents.getValue(2)
        val resolver = EveConditionResolver(all)

        val click = resolver.resolveEventInDataOrder(
            event,
            PlayerEventState(
                missionSteps = mapOf(12358 to 2),
                dungeonMissionIds = setOf(12358),
            ),
            62007,
        )
        assertEquals(1, click?.matchedConditionNo)
        assertTrue(click!!.results.any { it.resultType == 3 && it.resultMeanNo == 1 })

        val win = resolver.resolveEvent(
            event,
            PlayerEventState(
                missionSteps = mapOf(12358 to 2),
                dungeonMissionIds = setOf(12358),
                battleResult = 1,
                fightDataKey = 1,
            ),
            62007,
            chainFilter = { chain -> chain.any { it.conditionClass == 8 } },
        )
        assertEquals(2, win?.matchedConditionNo)
        assertTrue(win!!.results.any {
            it.resultClass == 2 && it.parameter == 12358 &&
                it.parameterStyle == 1 && it.resultValue == 1
        })

        val afterWinTalk = resolver.resolveEventInDataOrder(
            event,
            PlayerEventState(
                missionSteps = mapOf(12358 to 3),
                dungeonMissionIds = setOf(12358),
            ),
            62007,
            afterConditionNo = 2,
        )
        assertEquals(5, afterWinTalk?.matchedConditionNo)
        assertTrue(afterWinTalk!!.results.any { it.resultType == 1 && it.resultMeanNo == 23922 })

        val relocate = resolver.resolveEventInDataOrder(
            event,
            PlayerEventState(
                missionSteps = mapOf(12358 to 3),
                dungeonMissionIds = setOf(12358),
            ),
            62007,
            afterConditionNo = 5,
        )
        assertEquals(8, relocate?.matchedConditionNo)
        assertTrue(relocate!!.results.any {
            it.resultClass == 3 && it.parameter == 2 && it.parameterStyle == 3
        })
        val next = relocate.results.filter {
            it.resultClass == 3 && it.parameterStyle == 4
        }
        assertEquals(1, next.size)
        assertTrue(next.single().parameter in 3..6)
    }

    @Test
    fun `treasure entry event resolves four visible chests and advances mission`() {
        val all = scenes()
        val event = all.getValue(62003).npcEvents.getValue(1)
        val resolver = EveConditionResolver(all)
        val entry = resolver.resolveEvent(
            event,
            PlayerEventState(
                missionSteps = mapOf(12330 to 1),
                dungeonMissionIds = setOf(12330),
            ),
            62003,
        )

        assertEquals(1, entry?.matchedConditionNo)
        val shown = entry!!.results.filter {
            it.resultClass == 3 && it.parameterStyle == 4
        }
        assertEquals(4, shown.size)
        assertTrue(shown.all { it.parameter in 2..7 })
        assertTrue(entry.results.any {
            it.resultClass == 2 && it.parameter == 12330 &&
                it.parameterStyle == 1 && it.resultValue == 1
        })
    }
}
