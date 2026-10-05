package tw.tsm.tests

import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import tw.tsm.app.handlers.HouseBoardHandler
import tw.tsm.app.response.HouseBoardResponseSender
import tw.tsm.data.loaders.SouvenirDatLoader
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.domain.interfaces.SessionSender
import tw.tsm.domain.interfaces.repositories.CharacterRecord
import tw.tsm.domain.interfaces.repositories.HouseProfile
import tw.tsm.gamelogic.systems.GiftHistoryEntry
import tw.tsm.gamelogic.systems.GiftWallEntry
import tw.tsm.gamelogic.systems.HouseBoardFriend
import tw.tsm.gamelogic.systems.HouseBoardSystem
import tw.tsm.gamelogic.systems.HouseBoardView
import tw.tsm.gamelogic.systems.PointSystem
import tw.tsm.gamelogic.systems.SouvenirSendResult
import tw.tsm.gamelogic.systems.WorldManager
import tw.tsm.protocol.PacketReader
import tw.tsm.protocol.PacketWriter

class HouseBoardGiftQ12Test {
    private fun fixture(vararg parts: String): ByteArray {
        val direct = Path.of(parts.first(), *parts.drop(1).toTypedArray())
        if (Files.exists(direct)) return Files.readAllBytes(direct)
        return Files.readAllBytes(Path.of("..", *parts))
    }

    @Test
    fun `real DVT souvenir table is 19 exact 18-byte rows`() {
        val bytes = fixture("File.dat", "Data-ios", "IOS_Souvenir_C.dat")
        assertEquals(4 + 19 * 18, bytes.size)
        val defs = SouvenirDatLoader.load(bytes)
        assertEquals(19, defs.size)
        assertEquals(711, defs.values.minOf { it.roleCount })
        assertEquals(729, defs.values.maxOf { it.roleCount })
        assertEquals(setOf(1, 3), defs.values.map { it.payment }.toSet())
        assertThrows(IllegalArgumentException::class.java) {
            SouvenirDatLoader.load(bytes + byteArrayOf(0))
        }
    }

    @Test
    fun `S105-001 profile uses 4-byte friendly exactly`() {
        val packets = mutableListOf<ByteArray>()
        val session = mockk<SessionSender> {
            every { send(capture(packets)) } just Runs
        }
        HouseBoardResponseSender().sendProfile(
            session,
            HouseBoardView(
                character = CharacterRecord(
                    id = 1001L,
                    accountId = 1L,
                    name = "Hero",
                ),
                profile = HouseProfile(
                    characterId = 1001L,
                    sign = 2,
                    birthMonth = 8,
                    birthDay = 15,
                    charm = 123456,
                    signature = "Sig",
                    impression = "Imp",
                    favorite = "Fav",
                    area = "Area",
                    hobby = "Hobby",
                ),
                friends = listOf(
                    HouseBoardFriend(
                        roleId = 2002L,
                        name = "Friend",
                        friendly = 70000,
                    ),
                ),
            ),
        )

        val r = PacketReader(packets.single())
        assertEquals(105, r.readUByte())
        assertEquals(1, r.readUByte())
        assertEquals(0, r.readUByte())
        assertEquals(1001L, r.readULongLE())
        assertEquals(0, r.readUByte())
        assertEquals(2, r.readUByte())
        assertEquals(8, r.readUByte())
        assertEquals(15, r.readUByte())
        assertEquals(123456, r.readIntLE())
        assertEquals("Sig", r.readBig5WithByteLen())
        assertEquals("Imp", r.readBig5WithByteLen())
        assertEquals("Fav", r.readBig5WithByteLen())
        assertEquals("Area", r.readBig5WithByteLen())
        assertEquals("Hobby", r.readBig5WithByteLen())
        assertEquals(1, r.readUByte())
        assertEquals(2002L, r.readULongLE())
        assertEquals("Friend", r.readBig5WithByteLen())
        assertEquals(70000, r.readIntLE())
        assertEquals(0, r.remaining)
    }

    @Test
    fun `S105 gift wall history and incoming gift match Lua wire`() {
        val packets = mutableListOf<ByteArray>()
        val session = mockk<SessionSender> {
            every { send(capture(packets)) } just Runs
        }
        val sender = HouseBoardResponseSender()

        sender.sendGiftWall(
            session,
            listOf(GiftWallEntry("Alice", 33001, 7)),
        )
        var r = PacketReader(packets.removeAt(0))
        assertEquals(105, r.readUByte())
        assertEquals(8, r.readUByte())
        assertEquals(0, r.readUByte())
        assertEquals(1, r.readUByte())
        assertEquals("Alice", r.readBig5WithByteLen())
        assertEquals(33001, r.readUShortLE())
        assertEquals(7, r.readIntLE())
        assertEquals(0, r.remaining)

        val entry = GiftHistoryEntry(
            senderId = 123456789L,
            senderName = "Bob",
            itemId = 33002,
            quantity = 3,
            time = 46000.5,
        )
        sender.sendGiftHistory(session, listOf(entry))
        r = PacketReader(packets.removeAt(0))
        assertEquals(105, r.readUByte())
        assertEquals(9, r.readUByte())
        assertEquals(0, r.readUByte())
        assertEquals(1, r.readUByte())
        assertEquals(123456789L, r.readULongLE())
        assertEquals("Bob", r.readBig5WithByteLen())
        assertEquals(33002, r.readUShortLE())
        assertEquals(3, r.readUByte())
        assertEquals(46000.5, r.readDoubleLE())
        assertEquals(0, r.remaining)

        sender.sendIncomingGift(session, entry)
        r = PacketReader(packets.removeAt(0))
        assertEquals(105, r.readUByte())
        assertEquals(10, r.readUByte())
        assertEquals(0, r.readUByte())
        assertEquals(123456789L, r.readULongLE())
        assertEquals("Bob", r.readBig5WithByteLen())
        assertEquals(33002, r.readUShortLE())
        assertEquals(3, r.readUByte())
        assertEquals(46000.5, r.readDoubleLE())
        assertEquals(0, r.remaining)
    }

    @Test
    fun `C105-003 parses target item quantity source exactly and relays Lua result`() = runBlocking {
        val system = mockk<HouseBoardSystem>()
        val response = mockk<ResponseSender>(relaxed = true)
        val point = mockk<PointSystem>(relaxed = true)
        val world = mockk<WorldManager>(relaxed = true)
        val session = mockk<GameSession> {
            every { state } returns SessionState.IN_GAME
            every { roleId } returns 77
        }
        val packets = mutableListOf<ByteArray>()
        val sessionSender = session as SessionSender
        every { sessionSender.send(capture(packets)) } just Runs

        coEvery {
            system.sendGift(77L, 9001L, 33003, 4, HouseBoardSystem.SOURCE_SHOP)
        } returns SouvenirSendResult.Fail(HouseBoardSystem.RESULT_NOT_FIRST_TURN)

        val handler = HouseBoardHandler(
            system,
            HouseBoardResponseSender(),
            response,
            point,
            world,
        )
        val body = PacketWriter()
            .writeLongLE(9001L)
            .writeShortLE(33003)
            .writeByte(4)
            .writeByte(HouseBoardSystem.SOURCE_SHOP)
            .toByteArray()
        assertEquals(12, body.size)

        handler.handle(session, 3, body)

        coVerify(exactly = 1) {
            system.sendGift(77L, 9001L, 33003, 4, HouseBoardSystem.SOURCE_SHOP)
        }
        val r = PacketReader(packets.single())
        assertEquals(105, r.readUByte())
        assertEquals(3, r.readUByte())
        assertEquals(0, r.readUByte())
        assertEquals(14, r.readUByte())
        assertEquals(0, r.remaining)
    }
}
