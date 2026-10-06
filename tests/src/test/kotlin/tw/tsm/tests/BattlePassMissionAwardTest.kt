package tw.tsm.tests

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import tw.tsm.app.handlers.ActivityHandler
import tw.tsm.data.loaders.MissionAwardDatLoader
import java.nio.file.Files
import java.nio.file.Path

class BattlePassMissionAwardTest {
    @Test
    fun `loads DVT battle pass missions exactly`() {
        val path = listOf(
            Path.of("File.dat", "Data-ios", "IOS_MissionAward_C.dat"),
            Path.of("..", "File.dat", "Data-ios", "IOS_MissionAward_C.dat"),
        ).first { Files.isRegularFile(it) }
        val defs = MissionAwardDatLoader.load(Files.readAllBytes(path))
        assertEquals(28, defs.size)
        val m17 = defs.getValue(17)
        assertEquals(117, m17.activityId)
        assertEquals(14, m17.conditionKind)
        assertEquals(7, m17.conditionValue)
        assertEquals(3, m17.operator)
        assertEquals(1, m17.targetValue)
        assertEquals(65447, m17.itemId)
        assertEquals(150, m17.itemCount)
        assertEquals(59, m17.getFlag)
    }

    @Test
    fun `mission award response matches C77-011 kind122 framing`() {
        assertEquals(
            listOf(77, 11, 0, 122, 1, 17, 0),
            ActivityHandler.missionAwardPacket(1, 17).map { it.toInt() and 255 },
        )
        assertEquals(
            listOf(77, 11, 0, 122, 255),
            ActivityHandler.missionAwardPacket(255, 17).map { it.toInt() and 255 },
        )
    }
}

