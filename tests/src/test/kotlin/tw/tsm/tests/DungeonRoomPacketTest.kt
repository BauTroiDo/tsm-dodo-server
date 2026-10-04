package tw.tsm.tests

import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tw.tsm.app.handlers.DungeonHandler
import tw.tsm.domain.data.DungeonDef
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.gamelogic.systems.DungeonRoomRegistry
import tw.tsm.gamelogic.systems.DungeonSystem
import tw.tsm.gamelogic.systems.WorldManager
import tw.tsm.protocol.PacketReader
import tw.tsm.protocol.PacketWriter

class DungeonRoomPacketTest {
    private fun dungeonDef() = DungeonDef(
        id = 1,
        nameId = 20018,
        kind = 1,
        minLv = 20,
        maxLv = 450,
        maxPlayer = 5,
        missionFlag = 12332,
        dailyFlag = 12334,
        dailyCount = 1,
        skipFlag = 0,
        timeMinutes = 20,
        sceneId = 62002,
        costPoint = 50,
        lootTable = 1,
        extraLoot = 0,
        infoTextId = 0,
        needPar = emptyList(),
        allPassPrize = 0,
    )

    private fun session(
        id: Long,
        name: String,
        sent: MutableList<ByteArray>,
        equips: IntArray = intArrayOf(),
    ): GameSession = mockk<GameSession>(relaxed = true).also {
        every { it.state } returns SessionState.IN_GAME
        every { it.playerId } returns id
        every { it.roleId } returns id.toInt()
        every { it.characterName } returns name
        every { it.level } returns 88
        every { it.sex } returns 1
        every { it.hair } returns 7
        every { it.color1 } returns 0x11223344
        every { it.color2 } returns 0x55667788
        every { it.equipItemIds } returns equips
        every { it.turn } returns 2
        every { it.titleId } returns 321
        every { it.send(capture(sent)) } just Runs
    }

    @Test
    fun `join packet matches DungeonPlayerData fixed 11-slot wire`() = runBlocking {
        val system = mockk<DungeonSystem>()
        coEvery { system.refreshPeriodicProgress(any()) } just Runs
        every { system.getDef(1) } returns dungeonDef()
        coEvery { system.validateNormalRoomEntry(any(), 1, any()) } returns DungeonSystem.RESULT_OK

        val rooms = DungeonRoomRegistry()
        val handler = DungeonHandler(system, rooms)

        val ownerPackets = mutableListOf<ByteArray>()
        val joinerPackets = mutableListOf<ByteArray>()
        val owner = session(1001, "Owner", ownerPackets, intArrayOf(111, 222))
        val joiner = session(1002, "Joiner", joinerPackets)

        handler.handle(owner, 2, byteArrayOf(1, 0, 0))
        val room = rooms.roomOf(owner.playerId)!!
        ownerPackets.clear()

        val joinPayload = PacketWriter()
            .writeUIntLE(room.id)
            .writeByte(0)
            .toByteArray()
        handler.handle(joiner, 3, joinPayload)

        assertEquals(1, joinerPackets.size)
        val reader = PacketReader(joinerPackets.single())
        assertEquals(47, reader.readUByte())
        assertEquals(3, reader.readUByte())
        assertEquals(0, reader.readUByte())
        assertEquals(0, reader.readUByte())
        assertEquals(room.id, reader.readUIntLE())
        assertEquals(1, reader.readUByte())

        assertEquals(owner.playerId, reader.readULongLE())
        assertEquals("Owner", reader.readBig5WithByteLen())
        assertEquals(88, reader.readUByte())
        assertEquals(1, reader.readUByte())
        assertEquals(7, reader.readUByte())
        reader.readIntLE()
        reader.readIntLE()

        assertEquals(111, reader.readUShortLE())
        assertEquals(222, reader.readUShortLE())
        repeat(9) { assertEquals(0, reader.readUShortLE()) }

        assertEquals(2, reader.readUByte())
        assertEquals(321, reader.readUShortLE())
        assertEquals(0, reader.readUByte())
        assertEquals(1, reader.readUShortLE())
        assertEquals(0, reader.remaining)

        assertEquals(1, ownerPackets.size)
        val notify = PacketReader(ownerPackets.single())
        assertEquals(47, notify.readUByte())
        assertEquals(13, notify.readUByte())
        assertEquals(0, notify.readUByte())
        assertEquals(joiner.playerId, notify.readULongLE())
        assertEquals("Joiner", notify.readBig5WithByteLen())
    }

    @Test
    fun `password checkbox returns four-digit offline fallback`() = runBlocking {
        val system = mockk<DungeonSystem>()
        coEvery { system.refreshPeriodicProgress(any()) } just Runs
        every { system.getDef(1) } returns dungeonDef()
        coEvery { system.validateNormalRoomEntry(any(), 1, any()) } returns DungeonSystem.RESULT_OK

        val packets = mutableListOf<ByteArray>()
        val owner = session(3001, "Owner", packets)
        val rooms = DungeonRoomRegistry()
        val handler = DungeonHandler(system, rooms)

        handler.handle(owner, 2, byteArrayOf(1, 0, 1))

        val reader = PacketReader(packets.single())
        assertEquals(47, reader.readUByte())
        assertEquals(2, reader.readUByte())
        assertEquals(0, reader.readUByte())
        assertEquals(0, reader.readUByte())
        val password = reader.readBig5WithByteLen()
        assertEquals(4, password.length)
        assertEquals(true, password.all(Char::isDigit))
        assertEquals(password, rooms.roomOf(owner.playerId)?.password)
    }
    @Test
    fun invitedPlayerBypassesLockedRoomPassword() = runBlocking {
        val system = mockk<DungeonSystem>()
        coEvery { system.refreshPeriodicProgress(any()) } just Runs
        every { system.getDef(1) } returns dungeonDef()
        coEvery { system.validateNormalRoomEntry(any(), 1, any()) } returns DungeonSystem.RESULT_OK
        val rooms = DungeonRoomRegistry()
        val world = mockk<WorldManager>()

        val ownerPackets = mutableListOf<ByteArray>()
        val targetPackets = mutableListOf<ByteArray>()
        val owner = session(4101, "Owner", ownerPackets)
        val target = session(4102, "Target", targetPackets)
        every { world.getPlayerById(4102L) } returns target

        val handler = DungeonHandler(system, rooms, world)
        handler.handle(owner, 2, byteArrayOf(1, 0, 1))
        val room = rooms.roomOf(owner.playerId)!!
        ownerPackets.clear()

        handler.handle(
            owner,
            8,
            PacketWriter().writeLongLE(target.playerId).toByteArray(),
        )

        val inviteAck = PacketReader(ownerPackets.single())
        assertEquals(47, inviteAck.readUByte())
        assertEquals(8, inviteAck.readUByte())
        assertEquals(0, inviteAck.readUByte())
        assertEquals(0, inviteAck.readUByte())

        val invitation = PacketReader(targetPackets.single())
        assertEquals(47, invitation.readUByte())
        assertEquals(15, invitation.readUByte())
        assertEquals(0, invitation.readUByte())
        assertEquals(room.id, invitation.readUIntLE())
        assertEquals(1, invitation.readUShortLE())
        assertEquals(owner.playerId, invitation.readULongLE())
        assertEquals("Owner", invitation.readBig5WithByteLen())
        assertEquals(88, invitation.readUByte())
        assertEquals(2, invitation.readUByte())
        assertEquals(0, invitation.readUByte())
        assertEquals(0, invitation.readUByte())
        assertEquals(0, invitation.readUByte())
        assertEquals(1, invitation.readUByte())
        assertEquals(0, invitation.remaining)
        assertEquals(true, rooms.isInvited(room.id, target.playerId))

        val joinPayload = PacketWriter()
            .writeUIntLE(room.id)
            .writeByte(0)
            .toByteArray()
        handler.handle(target, 3, joinPayload)

        val joinReply = PacketReader(targetPackets.last())
        assertEquals(47, joinReply.readUByte())
        assertEquals(3, joinReply.readUByte())
        assertEquals(0, joinReply.readUByte())
        assertEquals(0, joinReply.readUByte())
        assertEquals(false, rooms.isInvited(room.id, target.playerId))
    }

    @Test
    fun quickJoinSelectsUnlockedMatchingRoom() = runBlocking {
        val system = mockk<DungeonSystem>()
        coEvery { system.refreshPeriodicProgress(any()) } just Runs
        every { system.getDef(1) } returns dungeonDef()
        coEvery { system.validateNormalRoomEntry(any(), 1, any()) } returns DungeonSystem.RESULT_OK
        val rooms = DungeonRoomRegistry()
        val handler = DungeonHandler(system, rooms)

        val ownerPackets = mutableListOf<ByteArray>()
        val joinerPackets = mutableListOf<ByteArray>()
        val owner = session(4201, "Owner", ownerPackets)
        val joiner = session(4202, "Joiner", joinerPackets)

        handler.handle(owner, 2, byteArrayOf(1, 0, 0))
        ownerPackets.clear()

        handler.handle(
            joiner,
            13,
            PacketWriter().writeShortLE(1).toByteArray(),
        )

        val reply = PacketReader(joinerPackets.single())
        assertEquals(47, reply.readUByte())
        assertEquals(3, reply.readUByte())
        assertEquals(0, reply.readUByte())
        assertEquals(0, reply.readUByte())
        assertEquals(1L, rooms.roomOf(joiner.playerId)?.id)
    }

    @Test
    fun worldRecruitPacketMatchesDvtReceiverOrder() = runBlocking {
        val system = mockk<DungeonSystem>()
        coEvery { system.refreshPeriodicProgress(any()) } just Runs
        every { system.getDef(1) } returns dungeonDef()
        coEvery { system.validateNormalRoomEntry(any(), 1, any()) } returns DungeonSystem.RESULT_OK
        val rooms = DungeonRoomRegistry()
        val world = mockk<WorldManager>()

        val ownerPackets = mutableListOf<ByteArray>()
        val watcherPackets = mutableListOf<ByteArray>()
        val owner = session(4301, "Owner", ownerPackets)
        val watcher = session(4302, "Watcher", watcherPackets)
        every { world.getOnlineSessions() } returns listOf(owner, watcher)

        val handler = DungeonHandler(system, rooms, world)
        handler.handle(owner, 2, byteArrayOf(1, 0, 0))
        val room = rooms.roomOf(owner.playerId)!!
        ownerPackets.clear()

        handler.handle(owner, 9, byteArrayOf(2))

        val ack = PacketReader(ownerPackets.first())
        assertEquals(47, ack.readUByte())
        assertEquals(9, ack.readUByte())
        assertEquals(0, ack.readUByte())
        assertEquals(0, ack.readUByte())

        val recruit = PacketReader(watcherPackets.single())
        assertEquals(47, recruit.readUByte())
        assertEquals(16, recruit.readUByte())
        assertEquals(0, recruit.readUByte())
        assertEquals(room.id, recruit.readUIntLE())
        assertEquals(1, recruit.readUShortLE())
        assertEquals(owner.playerId, recruit.readULongLE())
        assertEquals("Owner", recruit.readBig5WithByteLen())
        assertEquals(2, recruit.readUByte())
        assertEquals(0, recruit.readUByte())
        assertEquals(0, recruit.remaining)
    }

    @Test
    fun `single create sends DVT start time packet`() = runBlocking {
        val singleDef = dungeonDef().copy(
            id = 2,
            kind = 2,
            minLv = 15,
            maxLv = 80,
            maxPlayer = 1,
            missionFlag = 12322,
            dailyFlag = 12336,
            timeMinutes = 10,
            sceneId = 62001,
        )
        val system = mockk<DungeonSystem>()
        coEvery { system.refreshPeriodicProgress(any()) } just Runs
        every { system.getDef(2) } returns singleDef
        coEvery { system.validateSingleCreate(any(), 2) } returns DungeonSystem.RESULT_OK
        coEvery { system.enterSingle(any(), 2) } returns true
        every { system.markSingleStarted(any(), 2, any()) } just Runs

        val packets = mutableListOf<ByteArray>()
        val player = session(5101, "Solo", packets)
        val handler = DungeonHandler(system)

        handler.handle(player, 2, byteArrayOf(2, 0, 0))

        assertEquals(2, packets.size)

        val create = PacketReader(packets[0])
        assertEquals(47, create.readUByte())
        assertEquals(2, create.readUByte())
        assertEquals(0, create.readUByte())
        assertEquals(0, create.readUByte())

        val start = PacketReader(packets[1])
        assertEquals(47, start.readUByte())
        assertEquals(18, start.readUByte())
        assertEquals(0, start.readUByte())
        val oaDate = start.readDoubleLE()
        assertEquals(true, oaDate > 40_000.0)
        assertEquals(0, start.remaining)

        verify(exactly = 1) { system.markSingleStarted(player, 2, any()) }
    }

}
