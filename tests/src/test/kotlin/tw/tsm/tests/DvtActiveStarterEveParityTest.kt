package tw.tsm.tests

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.data.loaders.EveDataLoader
import tw.tsm.gamelogic.systems.EveConditionResolver
import tw.tsm.gamelogic.systems.PlayerEventState

/** Guard against reintroducing the obsolete class15/choice30-31 10821 starter scene. */
class DvtActiveStarterEveParityTest {
    @Test fun activeDvtHomeUsesQuestStep13AndScene10853() {
        val scenes = EveDataLoader.load(Files.readAllBytes(Path.of("..", "gamedata", "Eve.emg")))
        val home = scenes.getValue(10821)
        val event = home.npcEvents.getValue(3)
        assertTrue(home.sceneInfos.containsKey(3), "Active DVT door must support sceneInfo3 -> 10853")
        assertFalse(event.conditions.any { it.conditionClass == 10 }, "Obsolete choice30/31 restored")
        assertFalse(event.conditions.flatMap { it.results }.any { it.resultClass == 15 }, "Obsolete skip effect restored")

        val resolver = EveConditionResolver(scenes)
        val state = PlayerEventState(missionSteps = mapOf(12280 to 12), completedEveCounts = mapOf(3 to 2))
        val progress = requireNotNull(resolver.resolveEvent(event, state, 10821))
        assertEquals(3, progress.matchedConditionNo)
        assertTrue(progress.results.any {
            it.resultType == 0 && it.resultClass == 2 &&
                it.parameter == 12280 && it.parameterStyle == 1 && it.resultValue == 1
        })
        val sceneChange = requireNotNull(resolver.resolveEventInDataOrder(
            event, state.copy(missionSteps = mapOf(12280 to 13)), 10821, afterConditionNo = 3
        ))
        assertEquals(5, sceneChange.matchedConditionNo)
        assertTrue(sceneChange.results.any {
            it.resultType == 2 && it.resultClass == 7 && it.parameter == 3
        })
        assertTrue(scenes.containsKey(10853), "DVT destination scene missing")
        assertTrue(scenes.getValue(10853).sceneInfos.containsKey(1), "DVT tutorial continuation exit missing")
    }
}
