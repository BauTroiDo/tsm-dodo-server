package tw.tsm.tests

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tw.tsm.app.handlers.ActivityHandler
import tw.tsm.data.loaders.EveDataLoader
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.gamelogic.systems.AdminBypassPolicy
import tw.tsm.gamelogic.systems.EveConditionResolver
import tw.tsm.gamelogic.systems.PlayerEventState
import java.nio.file.Files
import java.nio.file.Path

class AdminBypassAndActivityEntryTest {
    private fun file(vararg parts: String): ByteArray {
        val direct = Path.of(parts.first(), *parts.drop(1).toTypedArray())
        val path = if (Files.exists(direct)) direct else Path.of("..", *parts)
        return Files.readAllBytes(path)
    }

    @Test
    fun gmGetsAllRequestedTestingBypassFlagsWhileNormalGetsNone() {
        val policy = AdminBypassPolicy()
        val normal = mockk<GameSession>()
        every { normal.gmLevel } returns 0
        val gm = mockk<GameSession>()
        every { gm.gmLevel } returns 99

        val normalFlags = policy.flags(normal)
        assertFalse(normalFlags.bypassSchedule)
        assertFalse(normalFlags.bypassLevel)
        assertFalse(normalFlags.bypassEntryLimit)
        assertFalse(normalFlags.bypassQuestGate)
        assertFalse(normalFlags.freeCost)
        assertFalse(normalFlags.rewardRepeatTest)

        val gmFlags = policy.flags(gm)
        assertTrue(gmFlags.bypassSchedule)
        assertTrue(gmFlags.bypassLevel)
        assertTrue(gmFlags.bypassEntryLimit)
        assertTrue(gmFlags.bypassQuestGate)
        assertTrue(gmFlags.freeCost)
        assertTrue(gmFlags.rewardRepeatTest)
    }

    @Test
    fun judgmentTowerEntryIsDerivedFromAuthoritativeEveReverseLink() {
        val eve = EveDataLoader.load(file("gamedata", "Eve.emg"))
        assertEquals(12921, ActivityHandler.JUDGMENT_TOWER_SCENE_ID)
        assertEquals(620 to 1200, ActivityHandler.deriveJudgmentTowerEntry(eve))
    }

    @Test
    fun gmTrialDoorBypassKeepsBattleBranchAndDoesNotFakeBattleResult() {
        val eve = EveDataLoader.load(file("gamedata", "Eve.emg"))
        val event = eve.getValue(12955).npcEvents.getValue(2)
        val resolver = EveConditionResolver(eve)
        val emptyState = PlayerEventState(level = 1)

        assertEquals(null, resolver.resolveEvent(event, emptyState, 12955))

        val gmResolved = resolver.resolveEvent(
            event, emptyState, 12955,
            conditionOverride = { cond ->
                when {
                    cond.conditionClass == 2 -> true
                    cond.conditionClass == 7 && cond.conditionParameter == 0 && cond.conditionParameterStyle == 1 -> true
                    else -> null
                }
            },
        )
        assertTrue(gmResolved != null)
        assertTrue(gmResolved!!.results.any { it.resultType == 3 && it.resultMeanNo == 1 })
        assertTrue(gmResolved.results.any { it.resultType == 1 && it.resultMeanNo == 53181 })
    }
}
