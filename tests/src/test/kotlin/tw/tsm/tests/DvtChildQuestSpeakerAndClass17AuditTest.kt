package tw.tsm.tests

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.data.loaders.EveDataLoader
import tw.tsm.gamelogic.systems.EveConditionEvaluator
import tw.tsm.gamelogic.systems.PlayerEventState

/** DVT's 11980 child quest: guard the real Lua speaker bug and unsupported class17 gates. */
class DvtChildQuestSpeakerAndClass17AuditTest {
    private val scenes by lazy {
        val eve = listOf(Path.of("gamedata", "Eve.emg"), Path.of("..", "gamedata", "Eve.emg"))
            .first { Files.isRegularFile(it) }
        EveDataLoader.load(Files.readAllBytes(eve))
    }

    @Test fun `secondary generation dialogue must resolve eventInfo resultClass`() {
        val lua = listOf(
            Path.of("lua", "Logic", "Event", "EventHandler.lua"),
            Path.of("..", "lua", "Logic", "Event", "EventHandler.lua")
        ).first { Files.isRegularFile(it) }
        val source = Files.readString(lua)
        assertTrue(source.contains("elseif eventInfo.resultClass == 12 then"))
        assertFalse(Regex("elseif\\s+resultClass\\s*==\\s*12").containsMatchIn(source),
            "Bare resultClass silently loses the child speaker in Talk result class12")
        assertTrue(source.contains("showRole = Role.childNpc"))
    }

    @Test fun `child quest events contain class12 speakers and all sixteen class17 gates`() {
        val children = scenes.filterKeys { it == 12721 || it == 12722 }
        val conditions = children.values.flatMap { s ->
            s.npcEvents.values.flatMap { it.conditions }
        }
        val class17 = conditions.filter { it.conditionClass == 17 }
        assertEquals(16, class17.size)
        val shapes = class17.groupingBy {
            listOf(it.conditionParameter, it.conditionParameterStyle, it.conditionOps, it.conditionValue)
        }.eachCount()
        assertEquals(
            mapOf(
                listOf(0, 1, 5, 0) to 4,
                listOf(0, 2, 5, 10) to 1,
                listOf(0, 2, 6, 10) to 1,
                listOf(3, 3, 5, 0) to 2,
                listOf(2, 1, 0, 0) to 4,
                listOf(2, 2, 0, 0) to 4
            ), shapes
        )
        val childSpeech = conditions.flatMap { it.results }.count {
            it.resultType == 1 && it.resultClass == 12
        }
        assertTrue(childSpeech >= 4, "Real Eve must contain the affected child speaker events")
        // No persistent 93-byte ChildData/S069 subsystem is implemented. Fail closed;
        // do not fabricate child eligibility or advance mission 11980 from empty data.
        assertTrue(class17.all { EveConditionEvaluator.evaluate(it, PlayerEventState()) == false })
    }
}
