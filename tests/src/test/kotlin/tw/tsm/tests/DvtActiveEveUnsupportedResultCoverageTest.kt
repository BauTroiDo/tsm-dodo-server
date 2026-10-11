package tw.tsm.tests

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tw.tsm.data.loaders.EveDataLoader

/**
 * Pin the unsupported-result audit to the EXACT active client/server Eve.emg,
 * not the older local reference archived under TSMobileOnLine/tsm_dodo.
 */
class DvtActiveEveUnsupportedResultCoverageTest {
    @Test fun activeEveHas35CityResourceAnd89ActivityRewardButNoClass15() {
        val file = listOf(
            Path.of("gamedata", "Eve.emg"),
            Path.of("..", "gamedata", "Eve.emg"),
        ).first { Files.isRegularFile(it) }
        val scenes = EveDataLoader.load(Files.readAllBytes(file))
        assertEquals(3823, scenes.size)

        val results = scenes.values.flatMap { scene ->
            scene.npcEvents.values.flatMap { event ->
                event.conditions.flatMap { it.results }
            }
        }

        val toServer = results.filter { it.resultType == 0 || it.resultType == 7 }
        assertEquals(35, toServer.count { it.resultClass == 11 },
            "Never forget 35 unresolved city-resource mutations")
        assertEquals(89, toServer.count { it.resultClass == 13 },
            "Never fabricate 89 missing activity-reward packages")
        assertEquals(0, results.count { it.resultClass == 15 },
            "Old class15/10821 note was stale; not present in active DVT Eve")
    }
}
