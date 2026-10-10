package tw.tsm.tests

import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tw.tsm.data.loaders.LimitTimeDungeonDatLoader
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.gamelogic.systems.*
import java.nio.file.Files
import java.nio.file.Path

@OptIn(ExperimentalCoroutinesApi::class)
class LimitTimeDungeonExpiryRegressionTest {
    private val def by lazy {
        LimitTimeDungeonDatLoader.load(
            Files.readAllBytes(Path.of("..", "File.dat", "Data-ios", "IOS_LimitTimeDungeon_C.dat"))
        ).getValue(1)
    }

    @Test
    fun `otherworld reloading the scene must not restart the final minute`() = runTest {
        var used = 119
        val counts = mockk<RoleCountSystem>()
        every { counts.getValue(31L, 27) } answers { used }
        every { counts.setValue(31L, 27, any(), 120) } answers {
            used = invocation.args[2] as Int
        }
        coEvery { counts.saveCounts(31L) } just Runs

        val sender = mockk<ResponseSender>(relaxed = true)
        val special = mockk<SpecialSceneTeleportService>()
        coEvery { special.exitSpecialScene(any(), 49942) } returns true
        val quests = mockk<QuestSystem>()
        coEvery { quests.ensureMissionStarted(31L, 13137) } returns false
        val session = mockk<GameSession>(relaxed = true)
        every { session.roleId } returns 31
        every { session.mapId } returns 49942
        every { session.state } returns SessionState.IN_GAME
        every { session.battleId } returns 0L
        every { session.gmLevel } returns 0

        val system = LimitTimeDungeonSystem(
            mapOf(1 to def), counts, sender, special, this,
            AdminBypassPolicy(), quests,
            clockMillis = { testScheduler.currentTime },
            minuteMillis = 10_000L,
            tickMillis = 1_000L,
        )
        system.onSceneReady(session)
        runCurrent()
        advanceTimeBy(5_000)
        runCurrent()
        system.onSceneReady(session) // repeated C012-001 from same session
        advanceTimeBy(5_000)
        runCurrent()

        assertEquals(120, used)
        verify(exactly = 1) {
            sender.sendRoleCountUpdate(session, mapOf(27 to (120 to 120)))
        }
        coVerify(exactly = 1) { special.exitSpecialScene(session, 49942) }
    }

    @Test
    fun `expired training time on login immediately kicks without reinitializing mission`() = runTest {
        val counts = mockk<RoleCountSystem>()
        every { counts.getValue(31L, 27) } returns 120
        val special = mockk<SpecialSceneTeleportService>()
        coEvery { special.exitSpecialScene(any(), 49942) } returns true
        val quests = mockk<QuestSystem>()
        val session = mockk<GameSession>(relaxed = true)
        every { session.roleId } returns 31
        every { session.mapId } returns 49942
        every { session.state } returns SessionState.IN_GAME
        every { session.battleId } returns 0L
        every { session.gmLevel } returns 0

        LimitTimeDungeonSystem(mapOf(1 to def), counts, mockk(), special, this,
            AdminBypassPolicy(), quests).onSceneReady(session)
        runCurrent()

        coVerify(exactly = 1) { special.exitSpecialScene(session, 49942) }
        coVerify(exactly = 0) { quests.ensureMissionStarted(any(), any()) }
    }

    @Test
    fun `if teleport fails retry, but never warp mid battle`() = runTest {
        var battling = 901L
        var used = 120
        val counts = mockk<RoleCountSystem>()
        every { counts.getValue(31L, 27) } answers { used }
        val special = mockk<SpecialSceneTeleportService>()
        coEvery { special.exitSpecialScene(any(), 49942) } returnsMany listOf(false, true)
        val session = mockk<GameSession>(relaxed = true)
        every { session.roleId } returns 31
        every { session.mapId } returns 49942
        every { session.state } returns SessionState.IN_GAME
        every { session.battleId } answers { battling }
        every { session.gmLevel } returns 0

        LimitTimeDungeonSystem(mapOf(1 to def), counts, mockk(), special, this,
            AdminBypassPolicy(), mockk(), tickMillis = 250L)
            .onSceneReady(session)
        runCurrent()
        coVerify(exactly = 0) { special.exitSpecialScene(any(), any()) }
        battling = 0L
        advanceTimeBy(250)
        runCurrent()
        coVerify(exactly = 1) { special.exitSpecialScene(session, 49942) }
        advanceTimeBy(250)
        runCurrent()
        coVerify(exactly = 2) { special.exitSpecialScene(session, 49942) }
        assertTrue(used >= 120)
    }
}
