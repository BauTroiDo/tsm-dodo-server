package tw.tsm.tests

import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.app.response.NpcEventResponseSender
import tw.tsm.data.loaders.MarkDatLoader
import tw.tsm.data.loaders.MarkStepDatLoader
import tw.tsm.domain.interfaces.SessionSender
import tw.tsm.domain.interfaces.repositories.MissionFlagRecord
import tw.tsm.domain.interfaces.repositories.MissionRecord
import tw.tsm.domain.interfaces.repositories.QuestRepository
import tw.tsm.gamelogic.systems.QuestSystem
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path

/**
 * UI icons are gated by the real DVT Mark_C bitId, not a synthetic level-one unlock.
 * A legacy marker step without its bit was previously repaired only when an Eve
 * event was evaluated, after the login S024-007 had already been emitted.
 */
class DvtLoginMarkerUiUnlockRegressionTest {
    private fun source(vararg parts: String): ByteArray {
        val path = Path.of(parts.first(), *parts.drop(1).toTypedArray())
        return Files.readAllBytes(if (Files.exists(path)) path else Path.of("..", *parts))
    }

    private val gateMissions = mapOf(11804 to 936, 12283 to 1375, 12285 to 1376, 12396 to 1434)

    private fun sourceDefs() = MarkDatLoader.load(
        source("File.dat", "Data-ios", "IOS_Mark_C.dat")
    ).first

    private fun sourceStepMissions() = MarkStepDatLoader.loadMissionIds(
        source("File.dat", "Data-ios", "IOS_MarkStep_C.dat")
    )

    @Test
    fun authenticUiGateMarkerIdsHaveRealBitsAndNoMarkStep() {
        val defs = sourceDefs()
        val steps = sourceStepMissions()
        for ((mission, bit) in gateMissions) {
            assertEquals(bit, defs[mission]?.bitId)
            assertFalse(mission in steps)
        }
        assertTrue(11120 in steps) // genuine multi-step quest
        assertTrue(defs[11120]?.bitId != null)
    }

    @Test
    fun legacySavedMarkerStepRepairsOnlyOwnedMissingBitsBeforeLoginSnapshot() = runTest {
        val cid = 93L
        val defs = sourceDefs()
        val steps = sourceStepMissions()
        val repo = mockk<QuestRepository>(relaxed = true)
        val flags = mutableMapOf(1375 to 1)
        coEvery { repo.getMissions(cid) } returns listOf(
            MissionRecord(11804, 1), MissionRecord(12283, 1),
            MissionRecord(12396, 1), MissionRecord(11120, 2)
        )
        coEvery { repo.getMissionFlags(cid) } coAnswers {
            flags.entries.map { MissionFlagRecord(it.key, it.value) }
        }
        coEvery { repo.upsertFlag(cid, any(), any()) } coAnswers {
            flags[secondArg()] = thirdArg()
        }

        val system = QuestSystem(repo, defs, markStepMissionIds = steps)
        assertEquals(setOf(936, 1434), system.reconcileStoredMarkerMissionFlags(cid)
            .map { it.bitIndex }.toSet())
        assertEquals(setOf(936, 1375, 1434), system.getFlags(cid)
            .map { it.bitIndex }.toSet())
        // Logged-in again: no repeated writes or duplicate claims.
        assertTrue(system.reconcileStoredMarkerMissionFlags(cid).isEmpty())
        coVerify(exactly = 1) { repo.upsertFlag(cid, 936, 1) }
        coVerify(exactly = 1) { repo.upsertFlag(cid, 1434, 1) }
        coVerify(exactly = 0) { repo.upsertFlag(cid, 1376, any()) }
        coVerify(exactly = 0) { repo.upsertFlag(cid, defs[11120]!!.bitId, any()) }
    }

    @Test
    fun absentMarkerNeverUnlocksAndUnknownDefinitionsRemainUntouched() = runTest {
        val cid = 93L
        val repo = mockk<QuestRepository>(relaxed = true)
        coEvery { repo.getMissions(cid) } returns listOf(
            MissionRecord(11120, 1), MissionRecord(12285, 0),
            MissionRecord(65534, 1)
        )
        coEvery { repo.getMissionFlags(cid) } returns emptyList()
        val sys = QuestSystem(repo, sourceDefs(), markStepMissionIds = sourceStepMissions())
        assertTrue(sys.reconcileStoredMarkerMissionFlags(cid).isEmpty())
        coVerify(exactly = 0) { repo.upsertFlag(any(), any(), any()) }
        // Without the authoritative original MarkStep membership, fail closed.
        val unknown = QuestSystem(repo, sourceDefs())
        assertTrue(unknown.reconcileStoredMarkerMissionFlags(cid).isEmpty())
        coVerify(exactly = 0) { repo.upsertFlag(any(), any(), any()) }
    }

    private class Capture : SessionSender {
        lateinit var packet: ByteArray
        override fun send(data: ByteArray) { packet = data }
        override fun close() {}
    }

    @Test
    fun initPacketPacksExactMarkBitsForOriginalClientCheckFlag() {
        val session = Capture()
        val flags = gateMissions.values.map { MissionFlagRecord(it, 1) }
        NpcEventResponseSender().sendInitMissionFlags(session, flags)
        val reader = ByteBuffer.wrap(session.packet).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(24, reader.get().toInt())
        assertEquals(7, reader.get().toInt())
        assertEquals(0, reader.get().toInt())
        assertEquals(3, reader.short.toInt())
        val indexed = mutableMapOf<Int, Int>()
        repeat(3) {
            indexed[reader.short.toInt() and 0xffff] = reader.get().toInt() and 255
        }
        // 936=>byte117 bit7, 1375/1376=>byte172 bits6/7,
        // 1434=>byte180 bit1.
        assertEquals(mapOf(117 to 0x80, 172 to 0xC0, 180 to 0x02), indexed)
        assertFalse(reader.hasRemaining())
    }

    @Test
    fun loginRepairsBeforeInitFlagsAndBeforePlayerAppear() {
        val local = Path.of("gamelogic", "src", "main", "kotlin", "tw", "tsm",
            "gamelogic", "systems", "LoginService.kt")
        val source = Files.readString(if (Files.exists(local)) local else Path.of("..").resolve(local))
        val start = source.indexOf("suspend fun executeCharacterEnterSequence(")
        val region = source.substring(start, source.indexOf("// 2. Tải vật phẩm", start))
        val reconcile = region.indexOf("questSystem.reconcileStoredMarkerMissionFlags(record.id)")
        val packet = region.indexOf("responseSender.sendInitMissionFlags(session, flags)")
        val appear = region.indexOf("responseSender.sendPlayerAppear(")
        assertTrue(reconcile >= 0 && reconcile < packet && packet < appear)
    }
}
