package tw.tsm.tests

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tw.tsm.domain.data.EveMinePlacement
import tw.tsm.domain.data.SceneEveData
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.gamelogic.systems.EventSession
import tw.tsm.gamelogic.systems.EveEventStateMachine
import tw.tsm.gamelogic.systems.EveTriggerRouter
import tw.tsm.gamelogic.systems.MineEncounterSystem
import tw.tsm.gamelogic.systems.PlayerStateManager

class MineEncounterPacingTest {
    private fun playerStates(): PlayerStateManager =
        PlayerStateManager().also {
            it.initPlayer(
                characterId = 42,
                hp = 100,
                sp = 100,
                maxHp = 100,
                maxSp = 100,
                level = 1,
                atk = 1,
                def = 1,
                agi = 1,
                intelligence = 1,
                hpx = 1,
                spx = 1,
                element = 1,
            )
        }

    private fun session(mapId: Int): GameSession = mockk(relaxed = true) {
        every { roleId } returns 42
        every { battleId } returns 0L
        every { loginFinished } returns true
        every { this@mockk.mapId } returns mapId
        every { characterName } returns "Tester"
    }

    private fun scene(vararg mines: EveMinePlacement): SceneEveData =
        SceneEveData(
            npcs = emptyMap(),
            doors = emptyMap(),
            npcEvents = emptyMap(),
            sceneInfos = emptyMap(),
            mines = mines.associateBy { it.id },
        )

    @Test
    fun `mine cooldown survives mine region transitions and does not prefill distance`() = runTest {
        val mapId = 63000
        val explicit = EveMinePlacement(
            id = 1,
            events = listOf(1),
            gridX = 0,
            gridY = 0,
            gridWidth = 20,
            gridHeight = 20,
            sizeKind = 0,
        )
        val fallback = EveMinePlacement(
            id = 2,
            events = listOf(2),
            gridX = 1,
            gridY = 1,
            gridWidth = 1,
            gridHeight = 1,
            sizeKind = 1,
        )
        val stateMachine = mockk<EveEventStateMachine>()
        every { stateMachine.getSession(42) } returns null
        val router = mockk<EveTriggerRouter>()
        coEvery { router.triggerMine(any(), any()) } returns true
        val clock = longArrayOf(0L)

        val system = MineEncounterSystem(
            eveData = mapOf(mapId to scene(explicit, fallback)),
            stateMachine = stateMachine,
            triggerRouter = router,
            playerStateManager = playerStates(),
            nowMs = { clock[0] },
        )
        val session = session(mapId)

        system.onPlayerMove(session, 0, 0, 80, 0)
        system.onPlayerMove(session, 80, 0, 160, 0)
        system.onPlayerMove(session, 160, 0, 200, 0)
        coVerify(exactly = 1) { router.triggerMine(session, 1) }

        // Crossing into another MineData region used to replace MineWalkState and
        // erase the 27s cooldown, allowing another encounter 10-12 seconds later.
        clock[0] = 10_000L
        system.onPlayerMove(session, 200, 0, 500, 0)
        system.onPlayerMove(session, 500, 0, 580, 0)
        system.onPlayerMove(session, 580, 0, 660, 0)
        coVerify(exactly = 1) { router.triggerMine(any(), any()) }

        // Movement during cooldown must not pre-fill the 200px budget.
        clock[0] = 28_000L
        system.onPlayerMove(session, 660, 0, 740, 0)
        system.onPlayerMove(session, 740, 0, 820, 0)
        coVerify(exactly = 1) { router.triggerMine(any(), any()) }
        system.onPlayerMove(session, 820, 0, 860, 0)

        coVerify(exactly = 1) { router.triggerMine(session, 2) }
    }

    @Test
    fun `mine battle starts a fresh grace period when battle actually ends`() = runTest {
        val mapId = 12811
        val mine = EveMinePlacement(
            id = 1,
            events = listOf(1),
            gridX = 1,
            gridY = 1,
            gridWidth = 1,
            gridHeight = 1,
            sizeKind = 1,
        )
        var activeEvent: EventSession? = null
        val stateMachine = mockk<EveEventStateMachine>()
        every { stateMachine.getSession(42) } answers { activeEvent }
        val router = mockk<EveTriggerRouter>()
        coEvery { router.triggerMine(any(), 1) } returns true
        val clock = longArrayOf(0L)

        val system = MineEncounterSystem(
            eveData = mapOf(mapId to scene(mine)),
            stateMachine = stateMachine,
            triggerRouter = router,
            playerStateManager = playerStates(),
            nowMs = { clock[0] },
        )
        val session = session(mapId)

        system.onPlayerMove(session, 0, 0, 80, 0)
        system.onPlayerMove(session, 80, 0, 160, 0)
        system.onPlayerMove(session, 160, 0, 200, 0)
        coVerify(exactly = 1) { router.triggerMine(session, 1) }

        activeEvent = EventSession(
            mapId = mapId,
            eveNo = 1,
            npcClickId = 1,
            triggerKind = MineEncounterSystem.MINE_TRIGGER_KIND,
            results = emptyList(),
        )
        clock[0] = 20_000L
        system.onBattleEnd(42)
        activeEvent = null

        // Old behavior would expire at t=27s from battle start. New behavior is
        // t=47s because the 27s grace starts at the real battle end (t=20s).
        clock[0] = 30_000L
        system.onPlayerMove(session, 200, 0, 280, 0)
        system.onPlayerMove(session, 280, 0, 360, 0)
        system.onPlayerMove(session, 360, 0, 400, 0)
        coVerify(exactly = 1) { router.triggerMine(any(), any()) }

        clock[0] = 48_000L
        system.onPlayerMove(session, 400, 0, 480, 0)
        system.onPlayerMove(session, 480, 0, 560, 0)
        system.onPlayerMove(session, 560, 0, 600, 0)
        coVerify(exactly = 2) { router.triggerMine(session, 1) }
    }

    @Test
    fun `DVT no encounter item suppresses MineData without banking movement`() = runTest {
        val mapId = 12811
        val mine = EveMinePlacement(
            id = 1,
            events = listOf(1),
            gridX = 1,
            gridY = 1,
            gridWidth = 1,
            gridHeight = 1,
            sizeKind = 1,
        )
        val stateMachine = mockk<EveEventStateMachine>()
        every { stateMachine.getSession(42) } returns null
        val router = mockk<EveTriggerRouter>()
        coEvery { router.triggerMine(any(), 1) } returns true
        val playerStates = playerStates()
        playerStates.getState(42)!!.noEncounterUntil = 20_000L
        val clock = longArrayOf(10_000L)

        val system = MineEncounterSystem(
            eveData = mapOf(mapId to scene(mine)),
            stateMachine = stateMachine,
            triggerRouter = router,
            playerStateManager = playerStates,
            nowMs = { clock[0] },
        )
        val session = session(mapId)

        system.onPlayerMove(session, 0, 0, 80, 0)
        system.onPlayerMove(session, 80, 0, 160, 0)
        system.onPlayerMove(session, 160, 0, 240, 0)
        coVerify(exactly = 0) { router.triggerMine(any(), any()) }

        clock[0] = 21_000L
        system.onPlayerMove(session, 240, 0, 320, 0)
        system.onPlayerMove(session, 320, 0, 400, 0)
        coVerify(exactly = 0) { router.triggerMine(any(), any()) }
        system.onPlayerMove(session, 400, 0, 440, 0)

        coVerify(exactly = 1) { router.triggerMine(session, 1) }
    }

    @Test
    fun defeatGraceUsesDatabaseCharacterIdRatherThanWorldPlayerId() = runTest {
        // Live CaoYenDao has database charId=31 but world playerId=13.
        // Eve state and MineWalkState are keyed by the DB character id.
        val mapId = 12861
        val mine = EveMinePlacement(
            id = 1,
            events = listOf(15),
            gridX = 1,
            gridY = 1,
            gridWidth = 1,
            gridHeight = 1,
            sizeKind = 1,
        )
        var activeEvent: EventSession? = EventSession(
            mapId = 12861,
            eveNo = 6,
            npcClickId = 5,
            triggerKind = 8,
            results = emptyList(),
            fightDataKey = 28,
        )
        val stateMachine = mockk<EveEventStateMachine>()
        every { stateMachine.getSession(42) } answers { activeEvent }
        every { stateMachine.getSession(13) } returns null
        val router = mockk<EveTriggerRouter>()
        coEvery { router.triggerMine(any(), 1) } returns true
        val now = longArrayOf(100_000L)
        val system = MineEncounterSystem(
            eveData = mapOf(mapId to scene(mine)),
            stateMachine = stateMachine,
            triggerRouter = router,
            playerStateManager = playerStates(),
            nowMs = { now[0] },
        )
        val session = session(mapId) // roleId=42 is the DB character id.

        // Wrong world player id silently misses the source event.
        system.onBattleEnd(13, playerWin = false)
        io.mockk.verify(exactly = 1) { stateMachine.getSession(13) }

        // Production callback MUST supply session.roleId.toLong().
        system.onBattleEnd(session.roleId.toLong(), playerWin = false)
        io.mockk.verify(exactly = 1) { stateMachine.getSession(42) }
        activeEvent = null

        // 21 seconds after defeat: previous code erroneously allowed Mine.
        now[0] = 121_000L
        system.onPlayerMove(session, 0, 0, 80, 0)
        system.onPlayerMove(session, 80, 0, 160, 0)
        system.onPlayerMove(session, 160, 0, 240, 0)
        coVerify(exactly = 0) { router.triggerMine(any(), any()) }

        now[0] = 128_000L
        system.onPlayerMove(session, 240, 0, 320, 0)
        system.onPlayerMove(session, 320, 0, 400, 0)
        system.onPlayerMove(session, 400, 0, 440, 0)
        coVerify(exactly = 1) { router.triggerMine(session, 1) }
    }

}
