package tw.tsm.tests

import io.mockk.Runs
import io.mockk.capture
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tw.tsm.app.handlers.DungeonHandler
import tw.tsm.domain.data.DungeonDef
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.gamelogic.systems.DungeonRoomRegistry
import tw.tsm.gamelogic.systems.DungeonSystem
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
}
