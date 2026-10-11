package tw.tsm.tests

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.data.loaders.EveDataLoader
import tw.tsm.gamelogic.systems.DvtChildLifecycleGate

/**
 * Active DVT Eve map12721. Do not credit mission11980 when creation/care UI
 * operations are not implemented by the server or current client.
 */
class DvtChildLifecycleActionPreflightTest {
    private val scenes by lazy {
        val eve = listOf(Path.of("gamedata", "Eve.emg"), Path.of("..", "gamedata", "Eve.emg"))
            .first { Files.isRegularFile(it) }
        EveDataLoader.load(Files.readAllBytes(eve))
    }

    @Test fun exactlyFiveKnownUnavailableActionsInTheChildQuestScene() {
        val scene = scenes.getValue(12721)
        val actions = scene.npcEvents.values.flatMap { event ->
            event.conditions.flatMap { it.results }
        }.filter { DvtChildLifecycleGate.requiresUnavailableChildAction(12721, it) }

        assertEquals(5, actions.size)
        assertEquals(1, actions.count {
            it.resultType == 7 && it.resultClass == 7 && it.parameter == 0 &&
                it.parameterStyle == 36 && it.resultValue == 0
        })
        assertEquals(mapOf(22 to 2, 23 to 1, 24 to 1),
            actions.filter { it.resultType == 10 }
                .groupingBy { it.parameterStyle }.eachCount())
        assertTrue(actions.all {
            !DvtChildLifecycleGate.requiresUnavailableChildAction(12722, it)
        }, "The policy must not accidentally rewrite other maps")
    }

    @Test fun preserveQuest11980IfChildLifecycleWouldBeSkippedAfterMissionMutation() {
        val event = scenes.getValue(12721).npcEvents.getValue(10)
        val cond = event.conditions.single { it.conditionNo == 15 }
        val missionMutation = cond.results.indexOfFirst {
            it.resultType == 0 && it.resultClass == 2 &&
                it.parameter == 11980 && it.parameterStyle == 1 && it.resultValue == 30
        }
        val childActionIndex = cond.results.indexOfFirst {
            DvtChildLifecycleGate.requiresUnavailableChildAction(12721, it)
        }
        assertTrue(missionMutation >= 0, "Expected real Eve mission11980 step change")
        assertTrue(childActionIndex > missionMutation,
            "The child action must be preflighted before earlier mission mutation")
        assertNotNull(DvtChildLifecycleGate.firstUnavailable(12721, cond.results))
        assertNull(DvtChildLifecycleGate.firstUnavailable(12722, cond.results))
        assertNull(DvtChildLifecycleGate.firstUnavailable(12721,
            cond.results.subList(0, childActionIndex)))
    }

    @Test fun neverSilentlyPassTheSecondGenerationNpcMenuAction() {
        val eve = scenes.getValue(12721).npcEvents.getValue(9)
        val cond = eve.conditions.single { it.conditionNo == 16 }
        val blocked = DvtChildLifecycleGate.firstUnavailable(12721, cond.results)
        assertNotNull(blocked)
        assertEquals(7, blocked!!.resultType)
        assertEquals(36, blocked.parameterStyle)
    }

    @Test fun originalLuaConfirmsTheChildFormIsNotOpenable() {
        val lua = listOf(Path.of("lua", "Common", "protocal.lua"),
            Path.of("..", "lua", "Common", "protocal.lua"))
            .first { Files.isRegularFile(it) }
        val source = Files.readString(lua)
        assertTrue(source.contains("protocolTable[36][23] = function(data)"))
        assertTrue(source.contains("--Se_ChildForm.appear"))
    }
}
