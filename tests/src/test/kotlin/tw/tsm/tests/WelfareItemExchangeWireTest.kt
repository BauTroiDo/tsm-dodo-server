package tw.tsm.tests

import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.app.handlers.WelfareHandler
import tw.tsm.domain.data.LoginAwardDef
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.domain.interfaces.repositories.WelfareStateRepository
import tw.tsm.domain.valueobjects.ThingData
import tw.tsm.gamelogic.systems.*
import tw.tsm.protocol.PacketReader
import tw.tsm.protocol.PacketWriter

class WelfareItemExchangeWireTest {
    private fun buildPacket(kind: Int, day: Int, qty: Int): ByteArray =
        PacketWriter().writeByte(kind).writeIntLE(day).writeByte(qty).toByteArray()

    private data class Fixture(
        val handler: WelfareHandler, val session: GameSession,
        val inventory: InventorySystem, val money: MoneySystem,
    )
    private fun setup(award: LoginAwardDef): Fixture {
        val stateRepo=mockk<WelfareStateRepository>()
        coEvery { stateRepo.getLastLoginClaimDay(any()) } returns DispatchSystem.currentDayKey()
        val roleCount=mockk<RoleCountSystem>(relaxed=true)
        val flags=mockk<BitFlagSystem>(relaxed=true)
        val inventory=mockk<InventorySystem>(relaxed=true)
        val point=mockk<PointSystem>(relaxed=true)
        val money=mockk<MoneySystem>(relaxed=true)
        val currency=mockk<PlayerCurrencySystem>(relaxed=true)
        val sender=mockk<ResponseSender>(relaxed=true)
        val session=mockk<GameSession>(relaxed=true)
        every { session.state } returns SessionState.IN_GAME
        every { session.roleId } returns 77
        every { session.playerId } returns 7700L
        val handler=WelfareHandler(
            listOf(award),stateRepo,roleCount,flags,inventory,point,money,currency,sender
        )
        return Fixture(handler,session,inventory,money)
    }

    @Test fun supportsPhysicalRecipeFromAuthoritativeDvtTable() = runBlocking {
        val row=LoginAwardDef(9,0,107,listOf(55403 to 150),55411,1,0,0)
        val (handler,session,inventory)=setup(row)
        coEvery {
            inventory.exchangeWelfareItemsAtomic(77L,listOf(55403 to 150),55411 to 1,2,107)
        } returns mapOf(0 to ThingData(itemId=55403,quantity=50),1 to ThingData(itemId=55411,quantity=2))
        every { inventory.getBag(77L) } returns mapOf(0 to ThingData(itemId=55403,quantity=50),1 to ThingData(itemId=55411,quantity=2))
        val packets=mutableListOf<ByteArray>()
        every { session.send(capture(packets)) } just Runs
        handler.handle(session,2,buildPacket(9,107,2))
        coVerify(exactly=1) { inventory.exchangeWelfareItemsAtomic(77L,any(),any(),2,107) }
        val packet=PacketReader(packets.last())
        assertEquals(87,packet.readUByte())
        assertEquals(2,packet.readUByte())
        assertEquals(0,packet.readUByte())
        assertEquals(9,packet.readUByte())
        assertEquals(0,packet.readUByte())
    }

    @Test fun mixedGoldRecipeCommitsWhenSourceItemsAndMoneyAreSufficient() = runBlocking {
        val row=LoginAwardDef(9,0,101,listOf(55403 to 150,65351 to 100000),34564,1,0,0)
        val (handler,session,inventory,money)=setup(row)
        val completed=mapOf(1 to ThingData(itemId=34564,quantity=1))
        coEvery {
            inventory.exchangeWelfareGoldItemsAtomic(
                77L,listOf(55403 to 150),34564 to 1,1,101,100000,money
            )
        } returns completed
        every { inventory.getBag(77L) } returns mapOf(1 to ThingData(itemId=34564,quantity=1))
        every { money.getGold(77L) } returns 200000
        every { money.getMoney(77L) } returns (100000 to 0)
        val packets=mutableListOf<ByteArray>()
        every { session.send(capture(packets)) } just Runs
        handler.handle(session,2,buildPacket(9,101,1))
        coVerify(exactly=1) {
            inventory.exchangeWelfareGoldItemsAtomic(77L,any(),any(),1,101,100000,money)
        }
        coVerify(exactly=0) { inventory.exchangeWelfareItemsAtomic(any(),any(),any(),any(),any()) }
        val packet=PacketReader(packets.last())
        assertEquals(87,packet.readUByte())
        assertEquals(2,packet.readUByte())
        assertEquals(0,packet.readUByte())
        assertEquals(9,packet.readUByte())
        assertEquals(0,packet.readUByte())
    }

    @Test fun insufficientGoldRejectsWithoutConsumingBagOrPayingAnything() = runBlocking {
        val row=LoginAwardDef(9,0,201,listOf(46672 to 20,65351 to 100000),46580,1,0,0)
        val (handler,session,inventory,money)=setup(row)
        every { money.getGold(77L) } returns 381
        val packets=mutableListOf<ByteArray>()
        every { session.send(capture(packets)) } just Runs
        handler.handle(session,2,buildPacket(9,201,1))
        coVerify(exactly=0) { inventory.exchangeWelfareGoldItemsAtomic(any(),any(),any(),any(),any(),any(),any()) }
        coVerify(exactly=0) { inventory.exchangeWelfareItemsAtomic(any(),any(),any(),any(),any()) }
        val packet=PacketReader(packets.last())
        assertEquals(87,packet.readUByte())
        assertEquals(2,packet.readUByte())
        assertEquals(0,packet.readUByte())
        assertEquals(9,packet.readUByte())
        assertEquals(3,packet.readUByte())
    }

    @Test fun quantityAboveClientLimitIsRejected() = runBlocking {
        val row=LoginAwardDef(9,0,107,listOf(55403 to 150),55411,1,0,0)
        val (handler,session,inventory)=setup(row)
        val packets=mutableListOf<ByteArray>()
        every { session.send(capture(packets)) } just Runs
        handler.handle(session,2,buildPacket(9,107,51))
        coVerify(exactly=0) { inventory.exchangeWelfareItemsAtomic(any(),any(),any(),any(),any()) }
        val packet=PacketReader(packets.last())
        assertEquals(87,packet.readUByte())
        assertEquals(2,packet.readUByte())
        assertEquals(0,packet.readUByte())
        assertEquals(9,packet.readUByte())
        assertEquals(1,packet.readUByte())
    }
}
