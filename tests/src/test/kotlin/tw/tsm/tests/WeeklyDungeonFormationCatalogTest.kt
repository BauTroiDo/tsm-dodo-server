package tw.tsm.tests

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.gamelogic.systems.WeeklyDungeonFormationCatalog

class WeeklyDungeonFormationCatalogTest {
    @Test
    fun catalogMapsTwentyBachChienDungeonIdsWithoutGuessingSoulStages() {
        assertEquals((40258..40267).toList(), WeeklyDungeonFormationCatalog.npcIdsForDungeon(18))
        assertEquals((40428..40437).toList(), WeeklyDungeonFormationCatalog.npcIdsForDungeon(35))
        assertEquals((40438..40447).toList(), WeeklyDungeonFormationCatalog.npcIdsForDungeon(36))
        assertEquals(
            (40448..40455).toList() + listOf(40480, 40481),
            WeeklyDungeonFormationCatalog.npcIdsForDungeon(37),
        )

        for (dungeonId in 18..37) {
            assertEquals(10, requireNotNull(WeeklyDungeonFormationCatalog.npcIdsForDungeon(dungeonId)).size)
        }

        // Soul/crazy-weekly (63..82) remains evidence-blocked.
        assertNull(WeeklyDungeonFormationCatalog.npcIdsForDungeon(63))
    }
}
