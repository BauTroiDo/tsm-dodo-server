package tw.tsm.tests

import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.app.response.PartyPetResponseSender
import tw.tsm.domain.interfaces.SessionSender
import tw.tsm.domain.valueobjects.FollowNpcData
import tw.tsm.protocol.ThingDataWire
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

class DvtFollowNpcFullWireTest {
    @Test fun `S15-8 matches original TSNPCVN follow pet wire for multiple pets`() {
        val sent = slot<ByteArray>()
        val session = mockk<SessionSender>(relaxed = true) {
            every { send(capture(sent)) } just Runs
        }

        val pets = listOf(
            FollowNpcData(
                slotIndex = 1, npcId = 22083, name = "Thủy Đậu Yêu",
                level = 18, exp = 24670, hp = 141, sp = 6,
                intelligence = 2, atk = 7, def = 7, agi = 8, hpx = 8, spx = 4,
                loyalty = 60, canGrow = 1, skillPoint = 3,
                skill1Lv = 1, ascendPillCount = 300,
                hpPillCount = 400, spPillCount = 500, upgradeLv = 2,
            ),
            FollowNpcData(slotIndex = 2, npcId = 17013, name = "Sĩ Binh", level = 6, exp = 405),
            FollowNpcData(slotIndex = 3, npcId = 10046, name = "Binh Đao Lê", level = 17, exp = 22199),
            FollowNpcData(slotIndex = 4, npcId = 10055, name = "Binh Tinh Giáp", level = 18, exp = 22672),
        )

        PartyPetResponseSender().sendFollowNpcFullData(session, pets)

        val b = ByteBuffer.wrap(sent.captured).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(15, b.get().toInt() and 0xFF)
        assertEquals(8, b.get().toInt() and 0xFF)
        assertEquals(0, b.get().toInt() and 0xFF)
        assertEquals(4, b.get().toInt() and 0xFF)

        for (expected in pets) {
            assertEquals(expected.slotIndex, b.get().toInt() and 0xFF)
            assertEquals(expected.npcId, b.short.toInt() and 0xFFFF)
            assertEquals(expected.exp.toInt(), b.int)
            assertEquals(expected.level, b.get().toInt() and 0xFF)
            assertEquals(expected.hp, b.int.toLong() and 0xFFFF_FFFFL)
            assertEquals(expected.sp, b.short.toInt() and 0xFFFF)
            assertEquals(expected.intelligence, b.short.toInt() and 0xFFFF)
            assertEquals(expected.atk, b.short.toInt() and 0xFFFF)
            assertEquals(expected.def, b.short.toInt() and 0xFFFF)
            assertEquals(expected.agi, b.short.toInt() and 0xFFFF)
            assertEquals(expected.hpx, b.short.toInt() and 0xFFFF)
            assertEquals(expected.spx, b.short.toInt() and 0xFFFF)
            assertEquals(expected.deathCount, b.get().toInt() and 0xFF)
            assertEquals(expected.loyalty, b.get().toInt() and 0xFF)
            assertEquals(expected.canGrow, b.get().toInt() and 0xFF)
            assertEquals(expected.skillPoint, b.short.toInt() and 0xFFFF)

            val nameLen = b.get().toInt() and 0xFF
            val nameBytes = ByteArray(nameLen)
            b.get(nameBytes)
            assertEquals(expected.name, String(nameBytes, StandardCharsets.UTF_16LE))

            assertEquals(expected.skill1Lv, b.get().toInt() and 0xFF)
            assertEquals(expected.skill2Lv, b.get().toInt() and 0xFF)
            assertEquals(expected.skill3Lv, b.get().toInt() and 0xFF)

            repeat(6) {
                b.position(b.position() + ThingDataWire.SIZE)
            }

            assertEquals(expected.ascendPillCount, b.short.toInt() and 0xFFFF)
            assertEquals(if (expected.canUseSpecialSkill) 1 else 0, b.get().toInt() and 0xFF)
            assertEquals(expected.soulId, b.int)
            assertEquals(expected.hpPillCount, b.short.toInt() and 0xFFFF)
            assertEquals(expected.spPillCount, b.short.toInt() and 0xFFFF)
            assertEquals(expected.upgradeLv, b.get().toInt() and 0xFF)
        }
        assertFalse(b.hasRemaining())
    }
}
