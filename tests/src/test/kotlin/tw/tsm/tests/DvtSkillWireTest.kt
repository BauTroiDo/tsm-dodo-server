package tw.tsm.tests

import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.app.handlers.SkillUpgradeHandler
import tw.tsm.app.response.BattleResponseSender
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.domain.interfaces.SessionSender
import tw.tsm.domain.valueobjects.FollowNpcData
import tw.tsm.gamelogic.systems.PetSkillSystem
import tw.tsm.gamelogic.systems.SkillSystem
import java.nio.ByteBuffer
import java.nio.ByteOrder

class DvtSkillWireTest {
    private val pets = mockk<PetSkillSystem>()
    private val response = mockk<ResponseSender>(relaxed = true)
    private val session = mockk<GameSession>(relaxed = true) {
        every { state } returns SessionState.IN_GAME
        every { roleId } returns 42
    }
    private val skills = mockk<SkillSystem>()
    private val handler = SkillUpgradeHandler(skills, response, mockk(), pets)

    @Test fun `pet request reads byte slot then word id and absolute byte level`() = runTest {
        every { pets.upgrade(42, 2, listOf(0x7531 to 3, 0x7532 to 5)) } returns
            PetSkillSystem.Snapshot(FollowNpcData(2, 100, skillPoint = 17), mapOf(0x7531 to 3, 0x7532 to 5))
        handler.handle(session, 2, byteArrayOf(2, 0x31, 0x75, 3, 0x32, 0x75, 5))
        verify { response.sendFollowNpcAttribute(session, 2, 201, 3, 0x7531) }
        verify { response.sendFollowNpcAttribute(session, 2, 201, 5, 0x7532) }
        verify { response.sendFollowNpcAttribute(session, 2, 37, 17, 0) }
    }
    @Test fun `malformed or out of range pet request has no side effects`() = runTest {
        for (packet in listOf(byteArrayOf(), byteArrayOf(1, 1, 1), byteArrayOf(0, 1, 1, 1),
            byteArrayOf(5, 1, 1, 1), byteArrayOf(1, 1, 1, 1, 1), ByteArray(13) { 1 })) {
            handler.handle(session, 2, packet)
        }
        verify { pets wasNot Called }
        verify { response wasNot Called }
    }
    @Test fun `requests before entering game are ignored`() = runTest {
        every { session.state } returns SessionState.CONNECTED
        handler.handle(session, 2, byteArrayOf(1, 0x31, 0x75, 3))
        verify { pets wasNot Called }
    }
    @Test fun `partial player skill record rejects whole request`() = runTest {
        handler.handle(session, 1, byteArrayOf(0x31, 0x75, 3, 0x32))
        verify { skills wasNot Called }
        verify { response wasNot Called }
    }
    @Test fun `follow attribute wire matches Lua S8 2`() {
        var packet = byteArrayOf()
        val capture = object : SessionSender {
            override fun send(data: ByteArray) { packet = data }
            override fun close() {}
        }
        BattleResponseSender().sendFollowNpcAttribute(capture, 2, 201, 3, 30001)
        val r = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(listOf(8, 2, 0, 4), List(4) { r.get().toInt() and 255 })
        assertEquals(2, r.short.toInt())
        assertEquals(201, r.get().toInt() and 255)
        assertEquals(1, r.get().toInt())
        assertEquals(3, r.int)
        assertEquals(30001, r.int)
        assertFalse(r.hasRemaining())
    }
}
