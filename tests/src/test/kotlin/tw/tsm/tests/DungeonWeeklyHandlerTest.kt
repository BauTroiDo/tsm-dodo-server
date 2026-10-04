package tw.tsm.tests

import io.mockk.*
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tw.tsm.app.handlers.DungeonHandler
import tw.tsm.domain.data.DungeonDef
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.gamelogic.systems.BattleManager
import tw.tsm.gamelogic.systems.DungeonRoomRegistry
import tw.tsm.gamelogic.systems.DungeonSystem
import tw.tsm.gamelogic.systems.DvtWeeklyQualification
import tw.tsm.protocol.PacketReader

class DungeonWeeklyHandlerTest {
    private fun weeklyDef() = DungeonDef(
        id = 18,
        nameId = 0,
        kind = DungeonSystem.KIND_WEEKLY,
        minLv = 30,
        maxLv = 450,
        maxPlayer = 5,
        missionFlag = 0,
        dailyFlag = 12722,
        dailyCount = 1,
        skipFlag = 0,
        timeMinutes = 30,
        sceneId = 62015,
        costPoint = 12762,
        lootTable = 82,
        extraLoot = 0,
        infoTextId = 0,
        needPar = emptyList(),
        allPassPrize = 34366,
    )

    private fun session(id: Long, sent: MutableList<ByteArray>): GameSession =
        mockk<GameSession>(relaxed = true).also {
            every { it.state } returns SessionState.IN_GAME
            every { it.playerId } returns id
            every { it.roleId } returns id.toInt()
            every { it.characterName } returns "P$id"
            every { it.level } returns 200
            every { it.sex } returns 0
            every { it.hair } returns 1
            every { it.color1 } returns 0
            every { it.color2 } returns 0
            every { it.equipItemIds } returns intArrayOf()
            every { it.turn } returns 1
            every { it.titleId } returns 0
            every { it.send(capture(sent)) } just Runs
        }

    @Test
    fun `server sends S47-019 and never starts when weekly qualification fails`() = runTest {
        val system = mockk<DungeonSystem>()
        val battle = mockk<BattleManager>(relaxed = true)
        val rooms = DungeonRoomRegistry()
        val packets = mutableListOf<ByteArray>()
        val owner = session(7001, packets)

        every { system.getDef(18) } returns weeklyDef()
        coEvery { system.validateNormalRoomEntry(any(), 18, any()) } returns DungeonSystem.RESULT_OK
        coEvery { system.validateWeeklyRoomStart(any()) } returns mapOf(
            owner.playerId to DvtWeeklyQualification.FIGHT_NPC_REQUIRED,
        )

        val handler = DungeonHandler(
            system = system,
            rooms = rooms,
            battleManager = battle,
            scope = this,
            countdownMs = 0,
        )
        handler.handle(owner, 2, byteArrayOf(18, 0, 0))
        packets.clear()

        handler.handle(owner, 12, ByteArray(0))

        assertEquals(1, packets.size)
        val r = PacketReader(packets.single())
        assertEquals(47, r.readUByte())
        assertEquals(19, r.readUByte())
        assertEquals(0, r.readUByte())
        assertEquals(DvtWeeklyQualification.FIGHT_NPC_REQUIRED, r.readUByte())
        coVerify(exactly = 0) { battle.createWeeklyDungeonBattle(any(), any(), any()) }
    }

    @Test
    fun `qualified weekly room starts style24 path without Eve teleport`() = runTest {
        val system = mockk<DungeonSystem>(relaxed = true)
        val battle = mockk<BattleManager>()
        val rooms = DungeonRoomRegistry()
        val packets = mutableListOf<ByteArray>()
        val owner = session(7002, packets)

        every { system.getDef(18) } returns weeklyDef()
        coEvery { system.validateNormalRoomEntry(any(), 18, any()) } returns DungeonSystem.RESULT_OK
        coEvery { system.validateWeeklyRoomStart(any()) } returns emptyMap()
        coEvery { battle.createWeeklyDungeonBattle(any(), any(), any()) } returns true

        val handler = DungeonHandler(
            system = system,
            rooms = rooms,
            battleManager = battle,
            scope = this,
            countdownMs = 0,
        )
        handler.handle(owner, 2, byteArrayOf(18, 0, 0))
        packets.clear()

        handler.handle(owner, 12, ByteArray(0))
        runCurrent()

        val subKinds = packets.map { PacketReader(it).let { r -> r.readUByte(); r.readUByte() } }
        assertTrue(11 in subKinds, "owner prepare/countdown packet missing")
        assertTrue(18 in subKinds, "dungeon start time packet missing")
        assertTrue(20 in subKinds, "weekly UI close packet missing")
        verify(exactly = 1) { system.markRoomStarted(any(), any()) }
        coVerify(exactly = 1) { battle.createWeeklyDungeonBattle(any(), match { it.size == 10 }, any()) }
        coVerify(exactly = 0) { system.enterMultiplayer(any()) }
    }
}
