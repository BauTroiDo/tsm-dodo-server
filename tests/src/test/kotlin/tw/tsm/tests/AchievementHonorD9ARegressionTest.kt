package tw.tsm.tests

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tw.tsm.domain.data.AchievementDef
import tw.tsm.gamelogic.systems.AchievementSystem
import tw.tsm.gamelogic.systems.BitFlagSystem
import tw.tsm.gamelogic.systems.InventorySystem
import tw.tsm.gamelogic.systems.MoneySystem
import tw.tsm.gamelogic.systems.PlayerStateManager
import tw.tsm.gamelogic.systems.QuestSystem
import tw.tsm.gamelogic.systems.RoleCountSystem

class AchievementHonorD9ARegressionTest {
    private fun def(target: Long) = AchievementDef(
        id = 9001, mainTag = 0, subTag = 0, sortId = 0, showKind = 0, score = 1,
        conditionKind = 13, conditionKindValue = 0, conditionOpr = 3, conditionValue = target,
        itemId = 0, itemCount = 0, completeFlag = 7001, getFlag = 7002,
        channel = 0, channelContent = 0,
    )

    private fun states(killNum: Int): PlayerStateManager = PlayerStateManager().also {
        it.initPlayer(
            characterId = 1L, hp = 100, sp = 50, maxHp = 100, maxSp = 50,
            level = 1, atk = 10, def = 10, agi = 10, intelligence = 10,
            hpx = 0, spx = 0, element = 1, killNum = killNum,
        )
    }

    @Test
    fun `Honor condition uses KillNum because DVT Calc GetHonor is identity`() = runBlocking {
        val flags = mockk<BitFlagSystem>(relaxed = true)
        coEvery { flags.getSetFlagIds(1L) } returns emptySet()
        val achievement = def(120)
        val system = AchievementSystem(
            bitFlagSystem = flags,
            inventorySystem = mockk<InventorySystem>(relaxed = true),
            moneySystem = mockk<MoneySystem>(relaxed = true),
            playerStateManager = states(120),
            questSystem = mockk<QuestSystem>(relaxed = true),
            roleCountSystem = mockk<RoleCountSystem>(relaxed = true),
            achievementDefs = mapOf(achievement.id to achievement),
        )

        assertEquals(AchievementSystem.RESULT_SUCCESS, system.completeAchievement(1L, achievement.id))
        coVerify(exactly = 1) { flags.setFlag(1L, achievement.completeFlag) }
    }

    @Test
    fun `Honor condition fails below KillNum threshold and does not set flag`() = runBlocking {
        val flags = mockk<BitFlagSystem>(relaxed = true)
        coEvery { flags.getSetFlagIds(1L) } returns emptySet()
        val achievement = def(120)
        val system = AchievementSystem(
            bitFlagSystem = flags,
            inventorySystem = mockk<InventorySystem>(relaxed = true),
            moneySystem = mockk<MoneySystem>(relaxed = true),
            playerStateManager = states(119),
            questSystem = mockk<QuestSystem>(relaxed = true),
            roleCountSystem = mockk<RoleCountSystem>(relaxed = true),
            achievementDefs = mapOf(achievement.id to achievement),
        )

        assertEquals(AchievementSystem.RESULT_CONDITION_FAILED, system.completeAchievement(1L, achievement.id))
        coVerify(exactly = 0) { flags.setFlag(any(), any()) }
    }
}
