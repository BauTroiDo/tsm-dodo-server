package tw.tsm.app.handlers

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.data.LoginAwardDef
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.PacketHandler
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.domain.interfaces.repositories.WelfareStateRepository
import tw.tsm.gamelogic.systems.*
import tw.tsm.protocol.PacketReader
import tw.tsm.protocol.PacketWriter

private val welfareLogger = KotlinLogging.logger {}

/** DVT Phúc lợi / nút "Quà" trên HUD (protocolTable[87]). */
class WelfareHandler(
    private val loginAwardDefs: List<LoginAwardDef>,
    private val stateRepo: WelfareStateRepository,
    private val roleCountSystem: RoleCountSystem,
    private val bitFlagSystem: BitFlagSystem,
    private val inventorySystem: InventorySystem,
    private val pointSystem: PointSystem,
    private val moneySystem: MoneySystem,
    private val currencySystem: PlayerCurrencySystem,
    private val responseSender: ResponseSender,
) : PacketHandler {
    override val mainKind: Int = Opcodes.WELFARE

    override suspend fun handle(session: GameSession, subKind: Int, payload: ByteArray) {
        if (session.state != SessionState.IN_GAME) return
        when (subKind) {
            2 -> handleClaim(session, payload)
            3 -> handleOfflineExpData(session, payload)
            else -> welfareLogger.warn { "Welfare chưa hỗ trợ subKind=$subKind (${payload.size} bytes)" }
        }
    }

    private suspend fun prepareDaily(session: GameSession) {
        val charId = session.roleId.toLong()
        val today = DispatchSystem.currentDayKey()
        val lastClaim = stateRepo.getLastLoginClaimDay(charId)
        if (lastClaim != today) {
            if (bitFlagSystem.getSetFlagIds(charId).contains(LOGIN_BIT_FLAG)) {
                bitFlagSystem.clearFlag(charId, LOGIN_BIT_FLAG)
                responseSender.sendBitFlagChanges(session, listOf(LOGIN_BIT_FLAG to false))
            }
            val maxDay = loginAwardDefs.filter { it.group == AWARD_LOGIN }.maxOfOrNull { it.day } ?: 0
            if (maxDay > 0 && roleCountSystem.getValue(charId, LOGIN_SIGN_DAY) >= maxDay) {
                roleCountSystem.setValue(charId, LOGIN_SIGN_DAY, 0)
                roleCountSystem.saveCounts(charId)
                responseSender.sendRoleCountUpdate(session, mapOf(LOGIN_SIGN_DAY to (0 to 0)))
            }
        }
    }

    private suspend fun handleClaim(session: GameSession, payload: ByteArray) {
        if (payload.size != 6) return
        prepareDaily(session)
        val reader = PacketReader(payload)
        val kind = reader.readUByte()
        val requestedDay = reader.readIntLE()
        val count = reader.readUByte()
        if (reader.remaining != 0 || kind != AWARD_LOGIN || count != 1) {
            sendClaimResult(session, kind, RESULT_WRONG_KIND)
            return
        }

        val charId = session.roleId.toLong()
        val today = DispatchSystem.currentDayKey()
        if (stateRepo.getLastLoginClaimDay(charId) == today) {
            // DVT UpdateLoginAward() tự gửi lại C:087-002 mỗi khi RoleCount 107/108 refresh
            // trong lúc BitFlag.Login chưa kịp tới client. Xử lý duplicate cùng ngày theo kiểu
            // idempotent: KHÔNG phát thưởng lần nữa, nhưng đồng bộ state rồi ACK success để client
            // không hiện lỗi giả "sai chủng loại/đã nhận" do race packet.
            bitFlagSystem.setFlag(charId, LOGIN_BIT_FLAG)
            responseSender.sendBitFlagChanges(session, listOf(LOGIN_BIT_FLAG to true))
            responseSender.sendRoleCountUpdate(
                session,
                mapOf(
                    LOGIN_SIGN_DAY to (roleCountSystem.getValue(charId, LOGIN_SIGN_DAY) to 0),
                    CONTINUE_LOGIN to (roleCountSystem.getValue(charId, CONTINUE_LOGIN) to 0),
                )
            )
            sendClaimResult(session, kind, RESULT_SUCCESS)
            welfareLogger.debug { "Welfare login duplicate ACK: charId=$charId day=$requestedDay" }
            return
        }

        val expectedDay = roleCountSystem.getValue(charId, LOGIN_SIGN_DAY) + 1
        if (requestedDay != expectedDay) {
            sendClaimResult(session, kind, RESULT_CONDITION_NOT_MET)
            return
        }
        val award = loginAwardDefs.firstOrNull { it.group == AWARD_LOGIN && it.day == requestedDay }
        if (award == null) {
            sendClaimResult(session, kind, RESULT_BAD_REWARD)
            return
        }
        val rewards = award.rewards.filter { it.first > 0 && it.second > 0 }
        if (rewards.isEmpty()) {
            sendClaimResult(session, kind, RESULT_BAD_REWARD)
            return
        }

        val normalItems = rewards.filterNot { isVirtualReward(it.first) }
        val changedBag = if (normalItems.isNotEmpty()) {
            inventorySystem.addItemBundleToBagExact(charId, normalItems)
                ?: run { sendClaimResult(session, kind, RESULT_BAG_FULL); return }
        } else emptyList()

        for ((itemId, qty) in rewards) {
            if (!grantVirtualReward(charId, itemId, qty)) {
                welfareLogger.error { "Welfare login reward lỗi: charId=$charId itemId=$itemId qty=$qty" }
                sendClaimResult(session, kind, RESULT_BAD_REWARD)
                return
            }
        }

        roleCountSystem.setValue(charId, LOGIN_SIGN_DAY, requestedDay)
        roleCountSystem.increment(charId, CONTINUE_LOGIN, 1)
        roleCountSystem.saveCounts(charId)
        stateRepo.setLastLoginClaimDay(charId, today)
        bitFlagSystem.setFlag(charId, LOGIN_BIT_FLAG)

        for ((slot, thing) in changedBag) responseSender.sendSetBagItem(session, slot, thing)
        // Lua RoleCount.lua đăng ký 107/108 -> UILoginAward.RefreshRoleCount(), và
        // UpdateLoginAward() sẽ tự gửi lại C:087-002 nếu BitFlag.Login vẫn false.
        // Vì vậy phải đồng bộ BitFlag trước RoleCount để chặn vòng gửi lặp trên client.
        responseSender.sendBitFlagChanges(session, listOf(LOGIN_BIT_FLAG to true))
        responseSender.sendRoleCountUpdate(
            session,
            mapOf(
                LOGIN_SIGN_DAY to (roleCountSystem.getValue(charId, LOGIN_SIGN_DAY) to 0),
                CONTINUE_LOGIN to (roleCountSystem.getValue(charId, CONTINUE_LOGIN) to 0),
            )
        )
        syncVirtualBalances(session)
        sendClaimResult(session, kind, RESULT_SUCCESS)
        welfareLogger.info { "Welfare login claim: charId=$charId day=$requestedDay rewards=$rewards" }
    }

    private fun isVirtualReward(itemId: Int): Boolean = itemId in setOf(
        ITEM_POINT, ITEM_MONEY, ITEM_TS, ITEM_WARRIOR_TICKET, ITEM_CARD_TICKET, ITEM_EXCLUSIVE_TICKET
    )

    private suspend fun grantVirtualReward(charId: Long, itemId: Int, qty: Int): Boolean = when (itemId) {
        ITEM_POINT -> pointSystem.addRealPoint(charId, qty).also { if (it) pointSystem.savePoints(charId) }
        ITEM_MONEY -> moneySystem.addGold(charId, qty).also { if (it) moneySystem.saveMoney(charId) }
        ITEM_TS -> currencySystem.add(charId, 8, qty)
        ITEM_WARRIOR_TICKET -> currencySystem.add(charId, 12, qty)
        ITEM_CARD_TICKET -> currencySystem.add(charId, 13, qty)
        ITEM_EXCLUSIVE_TICKET -> currencySystem.add(charId, 14, qty)
        else -> true
    }

    private fun syncVirtualBalances(session: GameSession) {
        val charId = session.roleId.toLong()
        val (real, virtual) = pointSystem.getPoints(charId)
        responseSender.sendPointBalance(session, real, virtual)
        val (money, auctionMoney) = moneySystem.getMoney(charId)
        responseSender.sendInitMoney(session, money, auctionMoney)
        for (kind in listOf(8, 12, 13, 14)) {
            responseSender.sendPlayerCurrency(session, kind, currencySystem.get(charId, kind))
        }
    }

    private suspend fun handleOfflineExpData(session: GameSession, payload: ByteArray) {
        if (payload.isNotEmpty()) return
        prepareDaily(session)
        session.send(PacketWriter(4).writeByte(Opcodes.WELFARE).writeByte(3).writeByte(0).writeByte(0).toByteArray())
    }

    private fun sendClaimResult(session: GameSession, kind: Int, result: Int) {
        session.send(
            PacketWriter(5)
                .writeByte(Opcodes.WELFARE)
                .writeByte(2)
                .writeByte(0)
                .writeByte(kind)
                .writeByte(result)
                .toByteArray()
        )
    }

    companion object {
        private const val AWARD_LOGIN = 1
        private const val LOGIN_BIT_FLAG = 1
        private const val LOGIN_SIGN_DAY = 107
        private const val CONTINUE_LOGIN = 108
        private const val RESULT_SUCCESS = 0
        private const val RESULT_WRONG_KIND = 1
        private const val RESULT_ALREADY_CLAIMED = 2
        private const val RESULT_CONDITION_NOT_MET = 3
        private const val RESULT_BAG_FULL = 4
        private const val RESULT_BAD_REWARD = 7
        private const val ITEM_POINT = 65301
        private const val ITEM_MONEY = 65351
        private const val ITEM_TS = 65343
        private const val ITEM_WARRIOR_TICKET = 65347
        private const val ITEM_CARD_TICKET = 65348
        private const val ITEM_EXCLUSIVE_TICKET = 65349
    }
}
