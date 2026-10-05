package tw.tsm.tests

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertArrayEquals
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
    fun arenaAndWorldBossEntriesAreDerivedFromAuthoritativeEvePlacements() {
        val eve = EveDataLoader.load(file("gamedata", "Eve.emg"))
        val entries = ActivityHandler.deriveActivityNavigationEntries(eve)

        assertEquals(
            ActivityHandler.ActivityNavigationEntry(10991, 15163, 1070, 120),
            entries[ActivityHandler.MIX_VS_ACTIVITY_ID],
        )
        assertEquals(
            ActivityHandler.ActivityNavigationEntry(10991, 15226, 990, 260),
            entries[ActivityHandler.CHAOS_VS_ACTIVITY_ID],
        )
        assertEquals(
            ActivityHandler.ActivityNavigationEntry(10991, 15244, 990, 260),
            entries[ActivityHandler.DARE_NPC_ACTIVITY_ID],
        )
        assertEquals(
            ActivityHandler.ActivityNavigationEntry(12001, 38177, 1870, 1180),
            entries[ActivityHandler.WORLD_BOSS_ACTIVITY_ID],
        )
        assertEquals(
            ActivityHandler.ActivityNavigationEntry(49909, 27141, 1010, 560),
            entries[ActivityHandler.IMMORTAL_ARENA_ACTIVITY_ID],
        )
        assertEquals(65, ActivityHandler.minimumLevel(ActivityHandler.IMMORTAL_ARENA_ACTIVITY_ID))
    }

    @Test
    fun activityNavigationPacketMatchesLuaS77Sub3Result1Contract() {
        val entry = ActivityHandler.ActivityNavigationEntry(
            sceneId = 10991,
            npcId = 15163,
            x = 1070,
            y = 120,
        )
        assertArrayEquals(
            byteArrayOf(
                77, 3, 0, 1,
                0xEF.toByte(), 0x2A,
                0x2E, 0x04,
                0x78, 0x00,
            ),
            ActivityHandler.navigationPacket(entry),
        )
    }

    @Test
    fun gmTrialDoorBypassKeepsBattleBranchAndDoesNotFakeBattleResult() {
        val eve = EveDataLoader.load(file("gamedata", "Eve.emg"))
        val resolver = EveConditionResolver(eve)
        val emptyState = PlayerEventState(level = 1)
        val override: (tw.tsm.domain.data.EveCondition) -> Boolean? = { cond ->
            when {
                cond.conditionClass == 2 -> true
                cond.conditionClass == 7 && cond.conditionParameter == 0 && cond.conditionParameterStyle == 1 -> true
                else -> null
            }
        }
        val battleOnly: (List<tw.tsm.domain.data.EveCondition>) -> Boolean = { chain ->
            chain.any { c -> c.results.any { it.resultType == 3 } }
        }

        val event12955 = eve.getValue(12955).npcEvents.getValue(2)
        assertEquals(null, resolver.resolveEvent(event12955, emptyState, 12955))
        val gm12955 = resolver.resolveEvent(
            event12955, emptyState, 12955,
            chainFilter = battleOnly,
            conditionOverride = override,
        )
        assertTrue(gm12955 != null)
        assertTrue(gm12955!!.results.any { it.resultType == 3 && it.resultMeanNo == 1 })
        assertTrue(gm12955.results.any { it.resultType == 1 && it.resultMeanNo == 53181 })

        // Scene 12953 door 3 has a non-battle choice branch first and the battle branch second.
        // GM must skip the choice-only branch and select FightData key=2.
        val event12953 = eve.getValue(12953).npcEvents.getValue(3)
        val unfiltered = resolver.resolveEvent(
            event12953, emptyState, 12953,
            conditionOverride = override,
        )
        assertTrue(unfiltered != null)
        assertTrue(unfiltered!!.results.none { it.resultType == 3 })

        val gm12953 = resolver.resolveEvent(
            event12953, emptyState, 12953,
            chainFilter = battleOnly,
            conditionOverride = override,
        )
        assertTrue(gm12953 != null)
        assertTrue(gm12953!!.results.any { it.resultType == 3 && it.resultMeanNo == 2 })
        assertTrue(gm12953.results.any { it.resultType == 1 && it.resultMeanNo == 53174 })
    }
}
