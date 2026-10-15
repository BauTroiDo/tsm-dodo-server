package tw.tsm.tests

import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.app.handlers.OfflineExpRewardService
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.gamelogic.systems.*
import tw.tsm.persistence.repositories.OfflineExpClaimRepository
import tw.tsm.protocol.PacketReader

class OfflineExpRewardWireTest {
    @Test fun threePositionalRowsPreserveDisabledGoldAndPremiumPointCost() = runBlocking {
        val repo=mockk<OfflineExpClaimRepository>()
        val counts=mockk<RoleCountSystem>()
        val points=mockk<PointSystem>()
        val states=mockk<PlayerStateManager>()
        val stats=mockk<StatSystem>()
        val collection=mockk<CollectionSystem>()
        val quests=mockk<QuestSystem>()
        val online=mockk<OnlineWelfareDaySystem>()
        val sender=mockk<ResponseSender>()
        val session=mockk<GameSession>()
        val state=mockk<PlayerState>()
        every { session.roleId } returns 77
        every { session.battleId } returns 0L
        every { state.turn } returns 0
        every { state.level } returns 30
        every { states.getState(77L) } returns state
        coEvery { quests.hasMissionFlag(77L,11804) } returns true
        every { counts.getValue(77L,171) } returns 480
        every { counts.getValue(77L,169) } returns 24
        every { sender.sendUiSellFail(session,1,28,any()) } just Runs
        val service=OfflineExpRewardService(repo,counts,points,states,stats,collection,
            quests,online,sender)
        val frame=service.welfareOffers(session)
        assertEquals(listOf(87,3,0,3),frame.take(4).map { it.toInt() and 255 })
        val reader=PacketReader(frame.copyOfRange(4,frame.size))
        assertEquals(50,reader.readUShortLE())
        assertEquals(0L,reader.readUIntLE())
        assertEquals(0,reader.readUShortLE())
        assertEquals(0L,reader.readUIntLE())
        assertEquals(100,reader.readUShortLE())
        assertEquals(5L,reader.readUIntLE())
        assertEquals(0,reader.remaining)
        service.quote(session,28,false)
        verify(exactly=1) { sender.sendUiSellFail(session,1,28,any()) }
        verify(exactly=0) { sender.sendUiSellMoneyQuery(any(),28,any(),any()) }
    }
}
