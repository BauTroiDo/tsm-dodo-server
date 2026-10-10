package tw.tsm.tests

import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.data.loaders.LimitTimeDungeonDatLoader
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.gamelogic.systems.*
import tw.tsm.persistence.repositories.LimitedAreaDailyRepositoryImpl
import tw.tsm.persistence.tables.CharacterLimitedAreaDays
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

class OtherworldDailyEntryRegressionTest {
    private fun repository(): LimitedAreaDailyRepositoryImpl {
        Database.connect(
            "jdbc:h2:mem:limited_${UUID.randomUUID()};MODE=MySQL;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
        )
        transaction { SchemaUtils.create(CharacterLimitedAreaDays) }
        return LimitedAreaDailyRepositoryImpl()
    }

    @Test
    fun `one character gets one entry per day even if fewer than 120 minutes were used`() = runTest {
        val repo = repository()
        val start = 1_790_000_000_000L
        assertTrue(repo.claim(91, 49942, 20261010, start, 0, 60_000))
        assertFalse(repo.claim(91, 49942, 20261010, start + 10_000, 0, 60_000))
        repo.close(91, 49942)
        assertTrue(repo.find(91, 49942)!!.closed)
        assertFalse(repo.claim(91, 49942, 20261010, start + 20_000, 0, 60_000))
        assertTrue(repo.claim(91, 49942, 20261011, start + 86_400_000, 120, 60_000))
        val tomorrow = repo.find(91, 49942)!!
        assertEquals(20261011, tomorrow.dayKey)
        assertFalse(tomorrow.closed)
        assertEquals(start + 86_400_000, tomorrow.startedAtMillis)
    }

    @Test
    fun `old RoleCount 27 minutes do not grant another session during migration`() = runTest {
        val repo = repository()
        val start = 1_790_000_000_000L
        assertFalse(repo.claim(92, 49942, 20261010, start, 9, 60_000))
        assertTrue(repo.find(92, 49942)!!.closed)
        assertFalse(repo.claim(92, 49942, 20261010, start + 2_000, 9, 60_000))
        assertTrue(repo.claim(92, 49942, 20261011, start + 86_400_000, 9, 60_000))
    }

    @Test
    fun `reconnect in the same map recovers the original persisted deadline`() = runTest {
        val repo = repository()
        val start = 1_790_000_000_000L
        assertTrue(repo.claim(93, 49942, 20261010, start, 0, 60_000))
        assertEquals(start, repo.recover(93, 49942, 20261010, start + 15 * 60_000, 0, 60_000).startedAtMillis)
        assertFalse(repo.claim(93, 49942, 20261010, start + 16 * 60_000, 15, 60_000))
    }

    @Test
    fun `legacy in-map recovery preserves minutes already consumed`() = runTest {
        val repo = repository()
        val now = 1_790_000_000_000L
        val run = repo.recover(94, 49942, 20261010, now, 119, 60_000)
        assertEquals(now - 119 * 60_000L, run.startedAtMillis)
        assertFalse(run.closed)
        assertFalse(repo.claim(94, 49942, 20261010, now + 1_000, 119, 60_000))
    }

    @Test
    fun `server admission resets UI minutes only when a new daily entry is actually granted`() = runTest {
        val repo = repository()
        var now = 1_790_000_000_000L
        var used = 0
        val counts = mockk<RoleCountSystem>()
        every { counts.getValue(95L, 27) } answers { used }
        every { counts.setValue(95L, 27, any(), 120) } answers { used = thirdArg() }
        coEvery { counts.saveCounts(95L) } just Runs
        val sender = mockk<ResponseSender>(relaxed = true)
        val session = mockk<GameSession>(relaxed = true)
        every { session.roleId } returns 95
        val def = LimitTimeDungeonDatLoader.load(
            Files.readAllBytes(Path.of("..", "File.dat", "Data-ios", "IOS_LimitTimeDungeon_C.dat"))
        ).getValue(1)
        val system = LimitTimeDungeonSystem(
            mapOf(1 to def), counts, sender, mockk(), this, AdminBypassPolicy(), mockk(),
            clockMillis = { now }, dailyRepository = repo,
        )
        assertTrue(system.tryStartDailyVisit(session, def))
        verify(exactly = 1) { sender.sendRoleCountUpdate(session, mapOf(27 to (0 to 120))) }
        assertFalse(system.tryStartDailyVisit(session, def))
        now += 86_400_000L
        assertTrue(system.tryStartDailyVisit(session, def))
        coVerify(exactly = 2) { counts.saveCounts(95L) }
    }
}
