package tw.tsm.tests

import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.data.loaders.EveDataLoader
import tw.tsm.domain.data.EveNpcPlacement
import tw.tsm.gamelogic.systems.NpcClientPath
import tw.tsm.gamelogic.systems.NpcPatrolSystem

class BaDauNpcMovementRegressionTest {
    private val scenes = EveDataLoader.load(
        Files.readAllBytes(Path.of("..", "File.dat", "CompreseData", "Eve.emg"))
    )

    @Test
    fun `Ba Dau paths remain client owned in every level bracket`() {
        val patrol = NpcPatrolSystem(mockk(relaxed = true), mockk(relaxed = true),
            scenes, CoroutineScope(SupervisorJob()), mockk(relaxed = true))
        for (sceneId in 62007..62010) {
            patrol.activateMap(sceneId)
            val roaming = scenes.getValue(sceneId).npcs.values.filter { it.motionType == 4 }
            assertTrue(roaming.isNotEmpty())
            roaming.forEach { npc ->
                assertTrue(npc.isHide)
                assertTrue(NpcClientPath.ownsMovement(npc))
                assertNull(patrol.getNpcPosition(sceneId, npc.id), "server must not reveal/move hidden path NPC")
                val points = npc.motionNodes.drop(1).map { (gx, gy) -> (gx - 1) * 20 to (gy - 1) * 20 }
                points.forEach { (x, y) -> assertTrue(NpcClientPath.isWithinRoute(npc, x, y)) }
                points.zipWithNext().forEach { (a, b) ->
                    assertTrue(NpcClientPath.isWithinRoute(npc, (a.first+b.first)/2, (a.second+b.second)/2))
                }
                assertFalse(NpcClientPath.isWithinRoute(npc, 5000, 5000))
            }
        }
    }

    @Test
    fun `click remains valid midway along route far from spawn and server target`() {
        val npc = scenes.getValue(62007).npcs.getValue(6)
        assertTrue(NpcClientPath.isWithinRoute(npc, 720, 850))
        assertFalse(NpcClientPath.isWithinRoute(npc, 720, 200))
    }

    @Test
    fun `standing and encounter NPCs retain server patrol and distance checks`() {
        val path = scenes.getValue(62007).npcs.getValue(6)
        assertFalse(NpcClientPath.ownsMovement(path.copy(traceRadius = 100)))
        assertFalse(NpcClientPath.ownsMovement(path.copy(isHide = false)))
        assertFalse(NpcClientPath.isWithinRoute(path.copy(traceRadius = 100), 720, 850))
        assertFalse(NpcClientPath.ownsMovement(path.copy(motionNodes = emptyList())))
        assertFalse(NpcClientPath.isWithinRoute(EveNpcPlacement(2, 22087, emptyList(), 210, 260), 720, 850))
    }
}
