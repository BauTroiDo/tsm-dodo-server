package tw.tsm.app.handlers

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.constants.EAttribute
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.gamelogic.systems.*
import tw.tsm.persistence.repositories.*
import tw.tsm.protocol.PacketWriter
import java.math.BigInteger

/** DEV-approved historical TS Mobile formula. Gold option 28 intentionally disabled. */
class OfflineExpRewardService(
    private val repo: OfflineExpClaimRepository,
    private val counts: RoleCountSystem,
    private val points: PointSystem,
    private val playerStates: PlayerStateManager,
    private val stats: StatSystem,
    private val collection: CollectionSystem,
    private val quests: QuestSystem,
    private val online: OnlineWelfareDaySystem,
    private val sender: ResponseSender,
) {
    private val logger = KotlinLogging.logger {}
    private val unavailable = UISellSystem.REASON_NOT_USABLE
    fun isOfflineSell(id: Int) = id == 28 || id == 29 || id == 30
    private fun minutes(id: Long) = counts.getValue(id,171).coerceIn(0,1440)
    private fun percent(m: Int) = m * 5 / 100
    private fun premiumCost(m: Int) = (m+99)/100
    private suspend fun permitted(session: GameSession): Boolean {
        val id=session.roleId.toLong()
        val state=playerStates.getState(id) ?: return false
        return session.battleId == 0L && state.turn in 0..2 && state.level in 1..199 &&
            quests.hasMissionFlag(id,11804)
    }
    private fun emptyOffers() = PacketWriter(4)
        .writeByte(87).writeByte(3).writeByte(0).writeByte(0).toByteArray()

    /** Three positional UI rows: FREE, GOLD DISABLED, PREMIUM. */
    suspend fun welfareOffers(session: GameSession): ByteArray {
        if (!permitted(session)) return emptyOffers()
        val id=session.roleId.toLong()
        val m=minutes(id)
        if (m < 20 || counts.getValue(id,169) != percent(m)) return emptyOffers()
        return PacketWriter(22).writeByte(87).writeByte(3).writeByte(0).writeByte(3)
            .writeShortLE(50).writeIntLE(0)
            .writeShortLE(0).writeIntLE(0)
            .writeShortLE(100).writeIntLE(premiumCost(m)).toByteArray()
    }
    suspend fun quote(session: GameSession, sell: Int, hasArgs: Boolean) {
        if (hasArgs || sell == 28 || !permitted(session)) {
            sender.sendUiSellFail(session,1,sell,unavailable);return
        }
        val id=session.roleId.toLong()
        val m=minutes(id)
        if (m < 20 || counts.getValue(id,169)!=percent(m)) {
            sender.sendUiSellFail(session,1,sell,unavailable);return
        }
        if (sell==30) {
            // DVT UISell.lua receives free kind=254 and confirms with useKind=2.
            session.send(PacketWriter(6).writeByte(84).writeByte(1).writeByte(0)
                .writeShortLE(30).writeByte(254).toByteArray())
        } else if (sell==29) {
            sender.sendUiSellMoneyQuery(session,29,UISellSystem.CURRENCY_GOLD,premiumCost(m).toLong())
        } else sender.sendUiSellFail(session,1,sell,unavailable)
    }

    suspend fun claim(session: GameSession, sell: Int, useKind: Int, hasArgs: Boolean) {
        if (sell == 28 || useKind!=2 || hasArgs || !permitted(session)) {
            sender.sendUiSellFail(session,2,sell,unavailable);return
        }
        online.withClaimLock(session) {
            val id=session.roleId.toLong()
            val m=minutes(id)
            val pct=percent(m)
            val state=playerStates.getState(id)
            if (m<20 || counts.getValue(id,169)!=pct || state==null ||
                state.turn !in 0..2 || state.level>=200 || session.battleId!=0L) {
                sender.sendUiSellFail(session,2,sell,unavailable)
                return@withClaimLock
            }
            val premium=sell==29
            val fee=if(premium) premiumCost(m) else 0
            val currency=if(premium) OfflineClaimCurrency.PREMIUM_POINTS else OfflineClaimCurrency.FREE
            val result=points.withOfflineExpClaimFence(id) { pointBefore ->
                val previousLevel=state.level
                val previousExp=state.exp
                val upExp=stats.calculateUpExp(previousLevel,state.turn)
                val numerator=BigInteger.valueOf(upExp)
                    .multiply(BigInteger.valueOf(m.toLong()*5L))
                    .multiply(BigInteger.valueOf(if(premium)100L else 50L))
                val gainBig=numerator.divide(BigInteger.valueOf(1_000_000L))
                if(gainBig<=BigInteger.ZERO || previousExp<0L ||
                    gainBig>BigInteger.valueOf(Long.MAX_VALUE-previousExp)) null
                else {
                    val expAfter=previousExp+gainBig.toLong()
                    var newLevel=previousLevel
                    while(newLevel<200 && expAfter >= stats.calculateNeedTotalExp(newLevel+1,state.turn))newLevel++
                    val request=OfflineClaimRequest(
                        id,m,pct,previousExp,gainBig.toLong(),
                        previousLevel,newLevel,state.turn,
                        state.attrPoint,state.skillPoint,fee,
                        if(premium)pointBefore else 0,currency
                    )
                    val committed=repo.tryCommit(request)
                    if(committed==null)null else
                        Pair(committed,if(premium)committed.currencyAfter else pointBefore)
                }
            }
            if(result==null) {
                logger.warn { "Offline claim rejected id="+id+" sell="+sell+" minutes="+m }
                sender.sendUiSellFail(session,2,sell,unavailable)
                return@withClaimLock
            }
            synchronized(state) {
                state.exp=result.expAfter
                state.level=result.levelAfter
                state.attrPoint=result.attrPointAfter
                state.skillPoint=result.skillPointAfter
            }
            counts.setValue(id,169,0)
            counts.setValue(id,171,0)
            sender.sendRoleCountUpdate(session,mapOf(169 to (0 to 0),171 to (0 to 0)))
            sender.sendSetAttribute(session,EAttribute.EXP,
                result.expAfter.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),true)
            if(result.levelAfter!=session.level) {
                session.level=result.levelAfter
                sender.sendSetAttribute(session,EAttribute.LV,result.levelAfter,true)
                sender.sendSetAttribute(session,EAttribute.ATTR_POINT,result.attrPointAfter,true)
                sender.sendSetAttribute(session,EAttribute.SKILL_POINT,result.skillPointAfter,true)
                val cHpx=collection.getPlayerCollectionAttribute(id,EAttribute.HPX)
                val rawHp=stats.calculateMaxHp(
                    level=state.level,
                    hpx=state.hpx+state.equipHpx+cHpx+state.astrolabeHpx,
                    equipMaxHp=state.equipMaxHp,extraHp=0,
                    turn=state.turn,career=state.career,turn3Lv=0
                )
                val newHp=tw.tsm.domain.util.DvtPercentMath.applyFinalPercentFloor(
                    rawHp,state.hpPercent).coerceAtLeast(1L)
                val newSp=stats.calculateMaxSp(
                    level=state.level,spx=state.spx,
                    equipMaxSp=state.equipMaxSp,extraSp=0,
                    turn=state.turn,career=state.career,turn3Lv=0)
                playerStates.updateMaxHp(id,newHp)
                playerStates.updateMaxSp(id,newSp)
                playerStates.fullHp(id)
                playerStates.fullSp(id)
                sender.sendSetAttribute(session,EAttribute.MAX_HP,newHp.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),true)
                sender.sendSetAttribute(session,EAttribute.HP,newHp.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),true)
                sender.sendSetAttribute(session,EAttribute.MAX_SP,newSp,true)
                sender.sendSetAttribute(session,EAttribute.SP,newSp,true)
            }
            if(premium) {
                val (real,virtual)=points.getPoints(id)
                sender.sendPointBalance(session,real,virtual)
            }
            sender.sendUiSellSuccess(session,sell)
            logger.info { "Offline claim success id="+id+" sell="+sell+" minutes="+m }
        }
    }
}
