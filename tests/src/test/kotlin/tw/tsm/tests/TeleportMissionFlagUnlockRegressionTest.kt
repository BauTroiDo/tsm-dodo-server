package tw.tsm.tests

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.app.handlers.WarpMarkUnlockPolicy
import tw.tsm.domain.data.MarkDef
import tw.tsm.domain.data.WarpDef
import tw.tsm.gamelogic.systems.QuestSystem

/**
 * DVT UITeleport checks MarkManager.flags, persisted in CharacterMissionFlags.
 * A permanent BitFlag is a separate user-profile feature, NOT a quest unlock.
 */
class TeleportMissionFlagUnlockRegressionTest {
    private val markDefs = mapOf(10075 to MarkDef("Khổng Phương", 1, 47, 0, ""))
    private val gatedWarp = WarpDef(0, 11012, 10075, 550, 450)
    private val quest = mockk<QuestSystem>()

    @Test fun missionFlagUnlocksWarp() = runTest {
        coEvery { quest.hasMissionFlag(31L, 10075) } returns true
        assertTrue(WarpMarkUnlockPolicy.isUnlocked(31L, gatedWarp, markDefs, quest))
        coVerify(exactly = 1) { quest.hasMissionFlag(31L, 10075) }
    }

    @Test fun absentMissionCompletionFlagKeepsWarpLocked() = runTest {
        coEvery { quest.hasMissionFlag(31L, 10075) } returns false
        assertFalse(WarpMarkUnlockPolicy.isUnlocked(31L, gatedWarp, markDefs, quest))
        coVerify(exactly = 1) { quest.hasMissionFlag(31L, 10075) }
    }

    @Test fun ungatedWarpDoesNotQueryQuestStore() = runTest {
        assertTrue(WarpMarkUnlockPolicy.isUnlocked(
            31L, gatedWarp.copy(mark = 0), markDefs, quest
        ))
        coVerify(exactly = 0) { quest.hasMissionFlag(any(), any()) }
    }

    @Test fun unknownMarkFailsClosed() = runTest {
        assertFalse(WarpMarkUnlockPolicy.isUnlocked(
            31L, gatedWarp.copy(mark = 99999), markDefs, quest
        ))
        coVerify(exactly = 0) { quest.hasMissionFlag(any(), any()) }
    }
}
