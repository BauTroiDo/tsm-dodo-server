package tw.tsm.tests

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tw.tsm.data.loaders.DungeonDatLoader
import tw.tsm.domain.data.DungeonDef
import tw.tsm.domain.data.DungeonNeedDef
import tw.tsm.gamelogic.systems.DvtWeeklyRoundObjective

class DungeonWeeklyRoundObjectiveTest {
    private fun defs() = DungeonDatLoader.load(
        Files.readAllBytes(Path.of("..", "File.dat", "Data-ios", "IOS_Dungeon_C.dat")),
    )

    @Test
    fun `real Bach Chien round requirements decode as DVT upper and lower bounds`() {
        val defs = defs()

        assertEquals(
            DvtWeeklyRoundObjective.Objective(maxRound = 3),
            DvtWeeklyRoundObjective.from(defs.getValue(21)),
        )
        assertEquals(
            DvtWeeklyRoundObjective.Objective(minRound = 10),
            DvtWeeklyRoundObjective.from(defs.getValue(22)),
        )
        assertEquals(
            DvtWeeklyRoundObjective.Objective(maxRound = 20),
            DvtWeeklyRoundObjective.from(defs.getValue(36)),
        )
        assertEquals(
            DvtWeeklyRoundObjective.Objective(),
            DvtWeeklyRoundObjective.from(defs.getValue(18)),
        )
    }

    @Test
    fun `real Soul weekly round requirement is decoded without enabling Soul entry gates`() {
        assertEquals(
            DvtWeeklyRoundObjective.Objective(maxRound = 20),
            DvtWeeklyRoundObjective.from(defs().getValue(81)),
        )
    }

    @Test
    fun `non weekly dungeons and nonpositive parameters do not create round objectives`() {
        val normal = DungeonDef(
            id = 1,
            nameId = 0,
            kind = 1,
            minLv = 0,
            maxLv = 0,
            maxPlayer = 1,
            missionFlag = 0,
            dailyFlag = 0,
            dailyCount = 0,
            skipFlag = 0,
            timeMinutes = 0,
            sceneId = 0,
            costPoint = 0,
            lootTable = 0,
            extraLoot = 0,
            infoTextId = 0,
            needPar = listOf(DungeonNeedDef(6, 3), DungeonNeedDef(7, 10)),
            allPassPrize = 0,
        )
        assertEquals(DvtWeeklyRoundObjective.Objective(), DvtWeeklyRoundObjective.from(normal))

        val weekly = normal.copy(
            id = 18,
            kind = 3,
            needPar = listOf(DungeonNeedDef(6, 0), DungeonNeedDef(7, -1)),
        )
        assertEquals(DvtWeeklyRoundObjective.Objective(), DvtWeeklyRoundObjective.from(weekly))
    }
}
