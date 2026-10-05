package tw.tsm.tests

import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import tw.tsm.app.handlers.WelfareHandler
import tw.tsm.data.GameDataManager
import tw.tsm.data.loaders.LoginAwardDatLoader
import tw.tsm.domain.data.LoginAwardDef
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.domain.interfaces.repositories.WelfareStateRepository
import tw.tsm.gamelogic.systems.*
import tw.tsm.protocol.PacketReader
import tw.tsm.protocol.PacketWriter

class WelfareLoginAwardTest {
    private fun fixture(vararg parts: String): ByteArray {
        val direct = Path.of(parts.first(), *parts.drop(1).toTypedArray())
        if (Files.exists(direct)) return Files.readAllBytes(direct)
        return Files.readAllBytes(Path.of("..", *parts))
    }

    @Test
    fun `real DVT login award table is 142 exact 46-byte rows`() {
        val bytes = fixture("File.dat", "Data-ios", "IOS_LoginAwardData_C.dat")
        assertEquals(4 + 142 * 46, bytes.size)
        val defs = LoginAwardDatLoader.load(bytes)
        assertEquals(142, defs.size)
        val login = defs.filter { it.group == 1 }
        assertEquals(28, login.size)
        assertEquals(1, login.first().day)
        assertEquals(65347 to 1, login.first().rewards.first())
        assertEquals(28, login.last().day)
        assertEquals(65349 to 3, login.last().rewards.first())
        assertThrows(IllegalArgumentException::class.java) {
            LoginAwardDatLoader.load(bytes + byteArrayOf(0))
        }
    }

    @Test
    fun `C87-002 login claim grants day reward and replies success`() = runBlocking {
        val loginAwards = listOf(
            LoginAwardDef(1, 0, 1, listOf(65347 to 1, 0 to 0, 0 to 0, 0 to 0, 0 to 0), 0, 0, 0, 0)
        )
        val stateRepo = mockk<WelfareStateRepository>()
        coEvery { stateRepo.getLastLoginClaimDay(any()) } returns 0
        coEvery { stateRepo.setLastLoginClaimDay(any(), any()) } just Runs
        val roleCounts = mockk<RoleCountSystem>()
        every { roleCounts.getValue(any(), 107) } returns 0
        every { roleCounts.getValue(any(), 108) } returns 1
        every { roleCounts.setValue(any(), any(), any(), any()) } just Runs
        every { roleCounts.increment(any(), any(), any()) } returns 1
        coEvery { roleCounts.saveCounts(any()) } just Runs
        val bitFlags = mockk<BitFlagSystem>()
        coEvery { bitFlags.getSetFlagIds(any()) } returns emptySet()
        coEvery { bitFlags.setFlag(any(), any()) } just Runs
        coEvery { bitFlags.clearFlag(any(), any()) } just Runs
        val inventory = mockk<InventorySystem>(relaxed = true)
        val point = mockk<PointSystem>(relaxed = true)
        every { point.getPoints(77L) } returns (0 to 0)
        val money = mockk<MoneySystem>(relaxed = true)
        every { money.getMoney(77L) } returns (0 to 0)
        val currency = mockk<PlayerCurrencySystem>(relaxed = true)
        every { currency.get(77L, any()) } returns 0
        coEvery { currency.add(77L, 12, 1) } returns true
        val response = mockk<ResponseSender>(relaxed = true)
        val packets = mutableListOf<ByteArray>()
        val session = mockk<GameSession> {
            every { state } returns SessionState.IN_GAME
            every { roleId } returns 77
            every { send(capture(packets)) } just Runs
        }
        val handler = WelfareHandler(loginAwards, stateRepo, roleCounts, bitFlags, inventory, point, money, currency, response)
        val body = PacketWriter().writeByte(1).writeIntLE(1).writeByte(1).toByteArray()
        assertEquals(6, body.size)

        handler.handle(session, 2, body)

        coVerify(exactly = 1) { currency.add(77L, 12, 1) }
        coVerify(exactly = 1) { stateRepo.setLastLoginClaimDay(77L, any()) }
        coVerify(exactly = 1) { bitFlags.setFlag(77L, 1) }
        val r = PacketReader(packets.last())
        assertEquals(87, r.readUByte())
        assertEquals(2, r.readUByte())
        assertEquals(0, r.readUByte())
        assertEquals(1, r.readUByte())
        assertEquals(0, r.readUByte())
        assertEquals(0, r.remaining)
    }
}
