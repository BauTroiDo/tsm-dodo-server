package tw.tsm.tests

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.data.loaders.MarkStepDatLoader
import tw.tsm.domain.data.MarkDef
import tw.tsm.domain.data.MarkNavigationTarget
import tw.tsm.domain.data.MarkStepDef
import tw.tsm.domain.data.MarkStepRequirement
import tw.tsm.gamelogic.systems.QuestNavigationPolicy
import java.io.ByteArrayOutputStream

class DvtQuestNavigationParityTest {
    private fun fixtureData(): ByteArray {
        val out = ByteArrayOutputStream()
        fun u1(v: Int) { out.write(v and 255) }
        fun u2(v: Int) { u1(v); u1(v ushr 8) }
        fun u4(v: Int) { u2(v); u2(v ushr 16) }
        fun str(v: String) {
            val bytes = v.toByteArray(Charsets.UTF_16LE)
            u2(bytes.size)
            out.write(bytes)
        }
        fun target(scene: Int, x: Int, y: Int, kind: Int, id: Int) {
            u2(scene); u2(x); u2(y); u1(kind); u1(id)
        }
        u4(2) // two rows for mission 10074, as in DVT source
        u2(10074); u1(1); str("Giao lễ cho Khổng Dung")
        u1(3); u2(30002); u1(1); target(11101,310,400,1,1)
        repeat(4) { u1(0);u2(0);u1(0);target(0,0,0,0,0) }
        str("Đưa lễ"); target(11012,550,450,1,1);u1(1);u1(0)
        u2(10074);u1(2);str("Báo lại với Khổng Phương")
        repeat(5) { u1(0);u2(0);u1(0);target(0,0,0,0,0) }
        str("Quay về");target(11101,310,400,1,1);u1(1);u1(0)
        return out.toByteArray()
    }
    private val marks = mapOf(
        10074 to MarkDef("Khổng Phương",1,0,0,""),
        10075 to MarkDef("Khổng Phương đưa Lê",1,47,0,""),
    )

    @Test fun parseAllDvtMarkStepFieldsNotJustMissionIds() {
        val data = fixtureData()
        val parsed = MarkStepDatLoader.loadSteps(data)
        assertEquals(setOf(10074), MarkStepDatLoader.loadMissionIds(data))
        assertEquals(2, parsed[10074]?.size)
        val first = parsed[10074]!![1]!!
        assertEquals(3, first.conditions[0].kind)
        assertEquals(30002, first.conditions[0].id)
        assertEquals(1, first.conditions[0].requiredCount)
        assertEquals(11101, first.conditions[0].target.sceneId)
        assertEquals(11012, first.endTarget.sceneId)
        assertEquals(550, first.endTarget.x)
        assertEquals(450, first.endTarget.y)
        assertEquals(1, first.endTarget.eventKind)
        assertEquals(1, first.endTarget.eventId)
        assertEquals(1, first.teleport)
        assertFalse(first.checkTeam)
    }

    @Test fun visibleMission10075UsesActive10074AndSelectsReturnNpcWhenItemReady() {
        val parsed = MarkStepDatLoader.loadSteps(fixtureData())
        val before = QuestNavigationPolicy.resolve(10075, mapOf(10074 to 1), marks, parsed, emptyMap())!!
        assertEquals(10074, before.activeMissionId)
        assertFalse(before.readyToTurnIn)
        assertEquals(11101, before.target.sceneId)
        val after = QuestNavigationPolicy.resolve(
            10075, mapOf(10074 to 1), marks, parsed, mapOf(30002 to 1)
        )!!
        assertTrue(after.readyToTurnIn)
        assertEquals(11012, after.target.sceneId)
        assertEquals(550, after.target.x)
        assertEquals(450, after.target.y)
        assertEquals(1, after.target.eventId)
    }

    @Test fun nextStepReturnsToQuestGiverAndInactiveAliasDoesNotFabricateMission() {
        val parsed = MarkStepDatLoader.loadSteps(fixtureData())
        val target = QuestNavigationPolicy.resolve(
            10075, mapOf(10074 to 2), marks, parsed, mapOf(30002 to 1)
        )!!
        assertEquals(11101, target.target.sceneId)
        assertEquals(310, target.target.x)
        assertNull(QuestNavigationPolicy.resolve(10075, emptyMap(), marks, parsed, emptyMap()))
    }

    @Test fun killObjectiveNavigatesToEncounterUntilEveAdvancesTheMissionStep() {
        // The only DVT MarkStep kill objectives are 13221/2 and 13306/1.
        // Their successful battle Eve chains advance the active mission step.
        // Simulate 13306/1: a collect target comes first, then a kill target,
        // then a DIFFERENT return NPC in scene 54975.
        val itemTarget = MarkNavigationTarget(54974, 200, 300, 1, 11)
        val killTarget = MarkNavigationTarget(54974, 1200, 320, 1, 11)
        val turnInTarget = MarkNavigationTarget(54975, 280, 380, 1, 2)
        val stepOne = MarkStepDef(
            missionId = 13306, step = 1, description = "Collect and kill",
            conditions = listOf(
                MarkStepRequirement(3, 29826, 1, itemTarget),
                MarkStepRequirement(2, 38136, 1, killTarget),
            ),
            endGuide = "Return", endTarget = turnInTarget, teleport = 0, checkTeam = false,
        )
        val stepTwo = stepOne.copy(step = 2, conditions = emptyList())
        val sampleMarks = mapOf(13306 to MarkDef("DVT encounter", 1, 0, 0, ""))
        val sampleSteps = mapOf(13306 to mapOf(1 to stepOne, 2 to stepTwo))

        val needItem = QuestNavigationPolicy.resolve(
            13306, mapOf(13306 to 1), sampleMarks, sampleSteps, emptyMap()
        )!!
        assertEquals(itemTarget, needItem.target)
        assertFalse(needItem.readyToTurnIn)

        val itemReadyButKillPending = QuestNavigationPolicy.resolve(
            13306, mapOf(13306 to 1), sampleMarks, sampleSteps, mapOf(29826 to 1)
        )!!
        assertEquals(killTarget, itemReadyButKillPending.target)
        assertFalse(itemReadyButKillPending.readyToTurnIn)

        val afterEveStepAdvance = QuestNavigationPolicy.resolve(
            13306, mapOf(13306 to 2), sampleMarks, sampleSteps, mapOf(29826 to 1)
        )!!
        assertEquals(turnInTarget, afterEveStepAdvance.target)
        assertTrue(afterEveStepAdvance.readyToTurnIn)
    }
}
