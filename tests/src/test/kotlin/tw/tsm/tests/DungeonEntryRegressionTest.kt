package tw.tsm.tests

import io.mockk.*
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.data.loaders.*
import tw.tsm.domain.data.*
import tw.tsm.domain.interfaces.*
import tw.tsm.domain.interfaces.repositories.*
import tw.tsm.gamelogic.systems.*

class DungeonEntryRegressionTest {
    private fun bytes(vararg parts: String) = Files.readAllBytes(Path.of("..", "File.dat", *parts))
    private fun scenes() = EveDataLoader.load(bytes("CompreseData", "Eve.emg"))
    private fun defs() = DungeonDatLoader.load(bytes("Data-ios", "IOS_Dungeon_C.dat"))
    private fun grids() = GroundWalkGridLoader.load(bytes("CompreseData", "Ground.mmg"))

    @Test fun `BaDauYeu old centroid is blocked and every normal dungeon has a walkable entrance`() {
        val grids = grids()
        assertFalse(grids.getValue(62001).isWalkablePixel(820, 1200))
        val teleport = SpecialSceneTeleportService(mockk(), mockk(), mockk(), scenes(), grids)
        for (d in defs().values.filter { it.kind in 1..2 }) {
            val point = teleport.deriveDungeonEntryPoint(d.sceneId)
            assertNotNull(point, "dungeon ${d.id}")
            assertTrue(grids.getValue(d.sceneId).isWalkablePixel(point!!.first, point.second), "dungeon ${d.id}")
        }
    }

    @Test fun `Ground grid preserves column order and both client obstacle bits`() {
        val grid = MapWalkGrid(2, 3, byteArrayOf(0, 1, 2, 4, 5, 0))
        assertTrue(grid.isWalkable(1, 1)); assertFalse(grid.isWalkable(1, 2))
        assertTrue(grid.isWalkable(1, 3)); assertFalse(grid.isWalkable(2, 1))
        assertFalse(grid.isWalkable(2, 2)); assertTrue(grid.isWalkable(2, 3))
        assertFalse(grid.isWalkable(0, 1))
        assertNull(MapWalkGrid(1, 1, byteArrayOf(1)).nearestWalkable(10, 10))
    }

    @Test fun `real Eve dungeon entry gates require initialized mission and retain hidden NPCs`() {
        val scenes = scenes()
        val evaluator = EveConditionEvaluator
        for ((id, eventNo) in listOf(2 to 2, 6 to 1, 1 to 1)) {
            val d = defs().getValue(id)
            val condition = scenes.getValue(d.sceneId).npcEvents.getValue(eventNo).conditions.first()
            assertFalse(evaluator.evaluate(condition, PlayerEventState()))
            assertTrue(evaluator.evaluate(condition, PlayerEventState(missionSteps = mapOf(d.missionFlag to 1), dungeonMissionIds = setOf(d.missionFlag))))
        }
        assertTrue(scenes.getValue(62001).npcs.values.all { it.isHide && !it.isVisible })
        assertFalse(scenes.getValue(62003).npcs.getValue(1).isHide)
        assertTrue(scenes.getValue(62003).npcs.getValue(2).isHide)
    }

    @Test fun `single dungeon mission is reset before scene entry without quota or completion flag changes`() = runBlocking {
        val d = defs().getValue(2)
        val repo = mockk<QuestRepository>(relaxed = true)
        val quest = QuestSystem(repo, mapOf(d.missionFlag to MarkDef("Dungeon", 4, 0, 0, "")))
        val sender = mockk<ResponseSender>(relaxed = true)
        val teleport = mockk<SpecialSceneTeleportService>()
        every { teleport.deriveDungeonEntryPoint(d.sceneId) } returns (510 to 1690)
        coEvery { teleport.teleport(any(), any(), any(), any(), any()) } just Runs
        val session = mockk<GameSession>(relaxed = true)
        every { session.roleId } returns 31
        val system = DungeonSystem(mapOf(d.id to d), quest, mockk(), teleport, sender)
        assertTrue(system.enterSingle(session, d.id))
        coVerifyOrder {
            repo.upsertMission(31, d.missionFlag, 1)
            sender.sendRemoveMission(session, d.missionFlag)
            sender.sendAddMissionStep(session, d.missionFlag, 1)
            teleport.teleport(session, d.sceneId, 510, 1690, 0)
        }
        coVerify(exactly = 0) { repo.upsertMission(any(), d.dailyFlag, any()) }
        coVerify(exactly = 0) { repo.upsertFlag(any(), any(), any()) }
    }

    @Test fun `missing dungeon ground data rejects entry before mutating mission`() = runBlocking {
        val d = defs().getValue(2)
        val quest = mockk<QuestSystem>()
        val teleport = mockk<SpecialSceneTeleportService>()
        every { teleport.deriveDungeonEntryPoint(d.sceneId) } returns null
        val system = DungeonSystem(mapOf(d.id to d), quest, mockk(), teleport)
        assertFalse(system.enterSingle(mockk(relaxed=true), d.id))
        coVerify(exactly=0) { quest.setMissionStep(any(), any(), any()) }
    }

    @Test fun `treasure step branches are exclusive and multiplayer staged assignments decode`() {
        val scenes = scenes()
        val d = defs().getValue(6)
        val branches = scenes.getValue(d.sceneId).npcEvents.getValue(5).conditions
        fun state(step: Int) = PlayerEventState(missionSteps = mapOf(d.missionFlag to step), dungeonMissionIds = setOf(d.missionFlag))
        assertTrue(EveConditionEvaluator.evaluate(branches[0], state(2)))
        assertFalse(EveConditionEvaluator.evaluate(branches[0], state(1)))
        assertTrue(EveConditionEvaluator.evaluate(branches[1], state(3)))
        assertFalse(EveConditionEvaluator.evaluate(branches[1], state(4)))
        assertTrue(EveConditionEvaluator.evaluate(branches[2], state(4)))
        assertFalse(EveConditionEvaluator.evaluate(branches[2], state(3)))
        val multi = scenes.getValue(62002)
        val assignment = multi.npcEvents.getValue(1).conditions[1].results.single()
        assertEquals(12332, assignment.parameter)
        assertEquals(4, assignment.parameterStyle)
        assertEquals(1002, assignment.resultValue)
        assertEquals(2, EveResultClassPolicy.dungeonStepAssignment(5, assignment.resultValue))
        assertEquals(3, EveResultClassPolicy.dungeonStepAssignment(5, 1003))
        assertNull(EveResultClassPolicy.dungeonStepAssignment(2, 1002))
        assertNull(EveResultClassPolicy.dungeonStepAssignment(5, 1256))
    }

    @Test fun `BaDauYeu show is gate-driven and terminal markers distinguish win from failure`() {
        val event = scenes().getValue(62001).npcEvents.getValue(2)
        assertTrue(event.conditions.first().results.any { it.resultClass == 3 && it.parameter == 1 && it.parameterStyle == 4 })
        val win = event.conditions.first { it.conditionClass == 8 && it.conditionParameterStyle == 1 }
        val lose = event.conditions.first { it.conditionClass == 8 && it.conditionParameterStyle == 2 }
        assertTrue(win.results.any { it.resultClass == 2 && it.parameter == 12323 && it.parameterStyle == 1 })
        assertFalse(lose.results.any { it.resultClass == 2 && it.parameter == 12323 })
        assertTrue(win.results.any { it.parameter == 12322 && it.parameterStyle == 3 })
        assertTrue(lose.results.any { it.parameter == 12322 && it.parameterStyle == 3 })
    }
}
