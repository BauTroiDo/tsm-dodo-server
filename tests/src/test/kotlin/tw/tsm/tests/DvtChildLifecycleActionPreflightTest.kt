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

    @Test fun careUiFeeIsNeverDebitedWithoutTheOriginalClientAction() {
        // Actual DVT Eve 12721, NPC event15 cond3:
        // type0/class5/pStyle2/value60000 reduces gold BEFORE
        // type10/class7/pStyle24. This would steal 60,000 gold if
        // unsupported type10 were silently skipped.
        val event = scenes.getValue(12721).npcEvents.getValue(15)
        val condition = event.conditions.single { it.conditionNo == 3 }
        val goldDebitIndex = condition.results.indexOfFirst {
            it.resultType == 0 && it.resultClass == 5 &&
                it.parameter == 0 && it.parameterStyle == 2 &&
                it.resultValue == 60000
        }
        val disabledCareIndex = condition.results.indexOfFirst {
            it.resultType == 10 && it.resultClass == 7 &&
                it.parameter == 1 && it.parameterStyle == 24
        }
        assertTrue(goldDebitIndex >= 0, "Must cover the authentic 60,000 gold charge")
        assertTrue(disabledCareIndex > goldDebitIndex,
            "The disabled care action occurs after the gold debit")
        assertNotNull(DvtChildLifecycleGate.firstUnavailable(12721, condition.results),
            "Preflight must refuse the entire chain BEFORE deducting any gold")
    }

    @Test fun dvtLuaHasNoRegisteredChildRaisingFormToOpen() {
        val source = listOf(
            Path.of("lua", "Common", "protocal.lua"),
            Path.of("..", "lua", "Common", "protocal.lua"),
        ).first { Files.isRegularFile(it) }.let(Files::readString)
        val callback = Regex(
            """protocolTable\[36\]\[23\] = function\(data\)(.*?)\nend""",
            RegexOption.DOT_MATCHES_ALL,
        ).find(source)?.groupValues?.get(1)
        assertNotNull(callback, "DVT S036-023 client handler must exist")
        assertTrue(callback!!.contains("--Se_ChildForm.appear"))
        assertFalse(
            Regex("""(?m)^\s*Se_ChildForm\.appear\s*\(""").containsMatchIn(callback),
            "Never treat a disabled, undefined UI callback as functional"
        )

        // The DVT client repository has no Se_ChildForm module. The
        // IL2CPP dump and other client copies are audited separately in docs.
        val uiRoot = listOf(Path.of("lua", "UI"), Path.of("..", "lua", "UI"))
            .first { Files.isDirectory(it) }
        Files.list(uiRoot).use { files ->
            assertFalse(files.anyMatch {
                it.fileName.toString().contains("ChildForm", ignoreCase = true)
            }, "Do not blindly uncomment S036-023 without implementing its UI")
        }
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
