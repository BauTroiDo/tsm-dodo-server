package tw.tsm.tests

import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.app.handlers.MiningHandler
import tw.tsm.app.handlers.RelocateHandler
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.domain.interfaces.repositories.MiningRecord
import tw.tsm.gamelogic.systems.MiningSystem
import tw.tsm.gamelogic.systems.MiningView
import tw.tsm.gamelogic.systems.WorldManager
import tw.tsm.gamelogic.systems.PartySystem
import tw.tsm.gamelogic.systems.SpecialSceneTeleportService
import java.nio.ByteBuffer
import java.nio.ByteOrder

class DvtMiningSceneReadyHudWireTest {
    @Test fun sceneReadyAckTriggersHudReplayOnlyInMine() = runBlocking {
        val world = mockk<WorldManager>(relaxed = true)
        val response = mockk<ResponseSender>(relaxed = true)
        val miner = mockk<MiningHandler>(relaxed = true)
        val session = mockk<GameSession>(relaxed = true)
        every { session.state } returns SessionState.IN_GAME
        every { session.mapId } returns MiningSystem.MINE_SCENE_ID
        val handler = RelocateHandler(
            world, response, miningHandler = miner
        )
        handler.handle(session, 1, byteArrayOf())
        coVerify(exactly = 1) { miner.sendHudSnapshotAfterSceneReady(session) }

        every { session.mapId } returns 12001
        handler.handle(session, 1, byteArrayOf())
        coVerify(exactly = 1) { miner.sendHudSnapshotAfterSceneReady(any()) }
    }

    @Test fun replaySendsAuthoritativeOreAndDoesNotDepleteMine() = runBlocking {
        val system = mockk<MiningSystem>()
        val world = mockk<WorldManager>(relaxed = true)
        val s = mockk<GameSession>(relaxed = true)
        every { s.state } returns SessionState.IN_GAME
        every { s.mapId } returns MiningSystem.MINE_SCENE_ID
        every { s.roleId } returns 31
        val frames = mutableListOf<ByteArray>()
        every { s.send(capture(frames)) } just Runs
        val state = MiningView(MiningRecord(), 600_000_000L)
        coEvery { system.getView(31L) } returns state
        val handler = MiningHandler(
            system, mockk<SpecialSceneTeleportService>(relaxed = true),
            mockk<PartySystem>(relaxed = true), world,
        )
        handler.sendHudSnapshotAfterSceneReady(s)
        assertEquals(1, frames.size)
        val frame = frames.single()
        assertEquals(18, frame.size)
        assertArrayEquals(byteArrayOf(170.toByte(), 2, 0, 1, 1),frame.copyOfRange(0, 5))
        assertEquals(600_000_000L, ByteBuffer.wrap(frame, 5, 8)
            .order(ByteOrder.LITTLE_ENDIAN).long)
        coVerify(exactly = 1) { system.getView(31L) }

        every { s.mapId } returns 49995
        handler.sendHudSnapshotAfterSceneReady(s)
        assertEquals(1, frames.size)
        coVerify(exactly = 1) { system.getView(any()) }
    }
}
