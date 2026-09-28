package tw.tsm.tests

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.app.response.LoginResponseSender
import tw.tsm.app.response.SceneResponseSender
import tw.tsm.domain.interfaces.SessionSender
import tw.tsm.domain.interfaces.StatCalculator
import tw.tsm.domain.interfaces.repositories.CharacterRecord
import tw.tsm.domain.valueobjects.PlayerDataDto
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Independent JDK byte reader follows DVT Lua reads, not PacketReader/Writer. */
class DvtLoginWireTest {
    private class Capture : SessionSender {
        lateinit var packet: ByteArray
        override fun send(data: ByteArray) { packet = data }
        override fun close() {}
        fun reader() = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN)
    }

    @Test fun `login success includes final diagnostic field after account kind`() {
        val session = Capture()
        LoginResponseSender().sendLoginResult(session, 0, 0x1234, 0x2345,
            0x0102030405060708L, 1234.5, "account", "", 2)
        val r = session.reader()
        assertEquals(listOf(1, 2, 0, 0), List(4) { r.get().toInt() })
        assertEquals(0x1234, r.short.toInt())
        assertEquals(0x2345, r.short.toInt())
        assertEquals(0x0102030405060708L, r.long)
        assertEquals(1234.5, r.double)
        assertEquals(0, r.get().toInt())
        val account = ByteArray(r.get().toInt()).also { r.get(it) }
        assertEquals("account", String(account, Charsets.US_ASCII))
        assertEquals(0, r.get().toInt())
        assertEquals(2, r.get().toInt())
        assertEquals(0, r.int) // protocal.lua: NetworkDisconnectMessage
        assertFalse(r.hasRemaining())
    }

    @Test fun `failed login ends at result without success fields`() {
        val session = Capture()
        LoginResponseSender().sendLoginResult(session, 6, 0, 0, 0, 0.0, "", "", 0)
        assertArrayEquals(byteArrayOf(1, 2, 0, 6), session.packet)
    }

    @Test fun `disconnect supplies both int32 fields for every cause`() {
        for (reason in listOf(1, 17, 61, 255)) {
            val session = Capture()
            LoginResponseSender().sendDisconnect(session, reason)
            val r = session.reader()
            assertEquals(listOf(0, 0, 0), List(3) { r.get().toInt() })
            assertEquals(reason, r.get().toInt() and 255)
            assertEquals(0, r.int) // messageString
            assertEquals(0, r.int) // NetworkDisconnectMessage
            assertFalse(r.hasRemaining())
        }
    }

    @Test fun `player data dynamic attributes immediately follow turn3 skills`() {
        for (attrs in listOf(emptyList(), listOf(201 to -12345, 202 to 123456))) {
            val session = Capture()
            val dto = PlayerDataDto(hp = 0x12345678, sp = 0x3456,
                skills = listOf(0x1234 to 7, 0x2345 to 8),
                turn3Skills = listOf(0x3456 to 9), newAttrs = attrs)
            LoginResponseSender().sendPlayerData(session, dto)
            val r = session.reader()
            assertEquals(listOf(5, 3, 0), List(3) { r.get().toInt() })
            r.get() // element
            assertEquals(0x12345678, r.int)
            assertEquals(0x3456, r.short.toInt())
            // Role.ReceivePlayerData: six stats, level, exp, two points, kills,
            // honor, gov, astrolabe, max HP/SP, eight equip stats, five morals,
            // practice, organization, autoEat. Fixed prefix is 94 payload bytes.
            r.position(3 + 94)
            assertEquals(2, r.short.toInt())
            assertEquals(0x1234, r.short.toInt()); assertEquals(7, r.get().toInt())
            assertEquals(0x2345, r.short.toInt()); assertEquals(8, r.get().toInt())
            r.get(); r.long; repeat(6) { r.short }
            assertEquals(1, r.short.toInt())
            assertEquals(0x3456, r.short.toInt()); assertEquals(9, r.get().toInt())
            assertEquals(attrs.size, r.get().toInt())
            for ((kind, value) in attrs) {
                assertEquals(kind, r.get().toInt() and 255)
                assertEquals(value, r.int)
            }
            assertFalse(r.hasRemaining())
        }
    }

    @Test fun `player data DTO carries persisted cumulative EXP into login packet`() {
        val record = CharacterRecord(
            id = 31,
            accountId = 1,
            name = "CaoYenDao",
            level = 21,
            exp = 123456L
        )
        val stat = object : StatCalculator {
            override fun calculateMaxHp(
                level: Int, hpx: Int, equipMaxHp: Int, extraHp: Int,
                turn: Int, career: Int, turn3Lv: Int
            ): Long = 131L

            override fun calculateMaxSp(
                level: Int, spx: Int, equipMaxSp: Int, extraSp: Int,
                turn: Int, career: Int, turn3Lv: Int
            ): Int = 61
        }

        val dto = PlayerDataDto.fromCharacterRecord(record, stat)
        assertEquals(123456, dto.exp)

        val session = Capture()
        LoginResponseSender().sendPlayerData(session, dto)
        val r = session.reader()
        r.position(3) // mainKind, subKind, compress
        r.get()       // element
        r.int         // hp
        r.short       // sp
        repeat(6) { r.short } // int/atk/def/agi/hpx/spx
        assertEquals(21, r.get().toInt() and 0xFF)
        assertEquals(123456, r.int)
    }

    @Test fun `scene change includes outfit count before title`() {
        val session = Capture()
        SceneResponseSender().sendSceneChange(session, 123L, 10801, 570, 770,
            intArrayOf(500, 600), 777)
        val r = session.reader()
        assertEquals(listOf(12, 0, 0), List(3) { r.get().toInt() })
        assertEquals(123L, r.long)
        assertEquals(listOf(10801, 570, 770, 0, 0), List(5) { r.short.toInt() })
        assertEquals(2, r.get().toInt())
        assertEquals(500, r.short.toInt()); assertEquals(600, r.short.toInt())
        assertEquals(0, r.get().toInt())
        assertEquals(777, r.short.toInt())
        assertFalse(r.hasRemaining())
    }
}
