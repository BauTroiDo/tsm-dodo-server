package tw.tsm.tests

import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.data.loaders.EveDataLoader
import tw.tsm.data.loaders.LimitTimeDungeonDatLoader
import tw.tsm.data.loaders.SceneFightDatLoader
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.domain.interfaces.repositories.CharacterRepository
import tw.tsm.domain.interfaces.repositories.MiningRecord
import tw.tsm.domain.interfaces.repositories.MiningRepository
import tw.tsm.gamelogic.systems.InventorySystem
import tw.tsm.gamelogic.systems.MiningSystem
import tw.tsm.gamelogic.systems.SpecialSceneTeleportService
import tw.tsm.gamelogic.systems.WorldManager
import java.nio.file.Files
import java.nio.file.Path

class DvtTeleportRemainingTabsTest {
    private fun root(): Path =
        generateSequence(Path.of("").toAbsolutePath()) { it.parent }
            .first { Files.exists(it.resolve("File.dat/Data-ios/IOS_LimitTimeDungeon_C.dat")) }

    private fun data(name: String): ByteArray =
        Files.readAllBytes(root().resolve("File.dat/Data-ios/$name"))

    @Test
    fun `DVT limit time dungeon record is exact`() {
        val defs = LimitTimeDungeonDatLoader.load(data("IOS_LimitTimeDungeon_C.dat"))
        assertEquals(1, defs.size)
        val def = defs[1]!!
        assertEquals(21770L, def.messageId)
        assertEquals(49942, def.sceneId)
        assertEquals(27, def.limitIndex)
        assertEquals(120, def.limitTimeMinutes)
        assertEquals(0, def.limitLevel)
        assertEquals(13137, def.missionId)
    }

    @Test
    fun `DVT turn navigation has 18 rows in groups 101 to 103`() {
        val rows = SceneFightDatLoader.load(data("IOS_SceneFight_C.dat"))
        assertEquals(381, rows.size)
        val turns = rows.filter { it.group > 100 }
        assertEquals(18, turns.size)
        assertEquals(mapOf(101 to 9, 102 to 7, 103 to 2), turns.groupingBy { it.group }.eachCount())

        assertEquals(13243, turns.first().sceneId)
        assertEquals(190, turns.first().x)
        assertEquals(480, turns.first().y)
        assertEquals(59301, turns.last().sceneId)
        assertEquals(370, turns.last().x)
        assertEquals(280, turns.last().y)
    }

    @Test
    fun `special scene entry fallback is deterministic from Eve geometry`() {
        val eve = EveDataLoader.load(Files.readAllBytes(root().resolve("gamedata/Eve.emg")))
        val service = SpecialSceneTeleportService(
            mockk<ResponseSender>(relaxed = true),
            mockk<WorldManager>(relaxed = true),
            mockk<CharacterRepository>(relaxed = true),
            eve,
        )

        // 49942 has no NPC: first exit door (280,220) + 4 grids => (360,300).
        assertEquals(360 to 300, service.deriveEntryPoint(49942))
        // 49996 has six real Eve NPC placements: snapped centroid => (440,400).
        assertEquals(440 to 400, service.deriveEntryPoint(49996))
        assertNull(service.deriveEntryPoint(65535))
    }

    @Test
    fun `mining durability settles one point every 360 seconds and persists`() = runBlocking {
        val start = 1_000_000L
        val now = start + MiningSystem.TICK_MS * 3
        val repo = MemoryMiningRepository(
            MiningRecord(machineFull = listOf(2, 2, 0, 0), updatedAtMillis = start)
        )
        val system = MiningSystem(
            repo,
            mockk<InventorySystem>(relaxed = true),
            emptyMap(),
            nowMillis = { now },
        )

        val view = system.getView(1L)

        assertEquals(listOf(0, 1, 0, 0), view.record.machineFull)
        assertEquals(start + MiningSystem.TICK_MS * 3, view.record.updatedAtMillis)
        assertEquals(view.record, repo.value)
        assertTrue(view.isMining)
        assertEquals(MiningSystem.MAX_MINE, view.remainingOre)
    }

    private class MemoryMiningRepository(initial: MiningRecord?) : MiningRepository {
        var value: MiningRecord? = initial

        override suspend fun load(charId: Long): MiningRecord? = value

        override suspend fun save(charId: Long, record: MiningRecord) {
            value = record
        }
    }
}
