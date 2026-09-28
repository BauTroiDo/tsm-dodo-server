package tw.tsm.tests

import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.gamelogic.systems.BitFlagSystem
import tw.tsm.gamelogic.systems.DailyProgressSystem
import tw.tsm.gamelogic.systems.RoleCountSystem

class DvtDailyProgressTest {
    @Test fun `gameplay role count update is persisted in runtime cache and pushed through S85`() {
        val counts = mockk<RoleCountSystem>()
        val flags = mockk<BitFlagSystem>()
        val sender = mockk<ResponseSender>(relaxed = true)
        val session = mockk<GameSession> {
            every { roleId } returns 42
        }

        every { counts.increment(42, DailyProgressSystem.CATCH_SUCCESS, 1) } returns 2
        every { counts.getMaxValue(42, DailyProgressSystem.CATCH_SUCCESS) } returns 0

        val system = DailyProgressSystem(counts, flags, sender)
        val result = system.incrementRoleCount(
            session,
            DailyProgressSystem.CATCH_SUCCESS,
        )

        assertEquals(2, result)
        verify(exactly = 1) {
            sender.sendRoleCountUpdate(
                session,
                mapOf(DailyProgressSystem.CATCH_SUCCESS to (2 to 0)),
            )
        }
    }

    @Test fun `DVT 149x condition flag is stored once and pushed through S81`() = runTest {
        val counts = mockk<RoleCountSystem>()
        val flags = mockk<BitFlagSystem>()
        val sender = mockk<ResponseSender>(relaxed = true)
        val session = mockk<GameSession> {
            every { roleId } returns 42
        }

        coEvery { flags.getSetFlagIds(42) } returns emptySet()
        coJustRun { flags.setFlag(42, DailyProgressSystem.FLAG_DEPLOY_FOLLOW_NPC) }

        val system = DailyProgressSystem(counts, flags, sender)
        system.setConditionFlag(session, DailyProgressSystem.FLAG_DEPLOY_FOLLOW_NPC)

        coVerify(exactly = 1) {
            flags.setFlag(42, DailyProgressSystem.FLAG_DEPLOY_FOLLOW_NPC)
        }
        verify(exactly = 1) {
            sender.sendBitFlagChanges(
                session,
                listOf(DailyProgressSystem.FLAG_DEPLOY_FOLLOW_NPC to true),
            )
        }
    }
}
