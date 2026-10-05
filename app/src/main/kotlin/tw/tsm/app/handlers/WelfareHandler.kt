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
        welfareLogger.info {
            "Welfare C87-002: roleId=${session.roleId} playerId=${session.playerId} kind=$kind day=$requestedDay count=$count"
        }
        if (reader.remaining != 0 || count != 1) {
            sendClaimResult(session, kind, RESULT_WRONG_KIND)
            return
        }

        when (kind) {
            AWARD_LOGIN -> handleDailyLoginClaim(session, requestedDay)
            AWARD_OPEN_SERVER -> handleOpenServerClaim(session, requestedDay)
            else -> sendClaimResult(session, kind, RESULT_WRONG_KIND)
        }
    }

    private suspend fun handleDailyLoginClaim(session: GameSession, requestedDay: Int) {
        val charId = session.roleId.toLong()
        val today = DispatchSystem.currentDayKey()
        if (stateRepo.getLastLoginClaimDay(charId) == today) {
            bitFlagSystem.setFlag(charId, LOGIN_BIT_FLAG)
            responseSender.sendBitFlagChanges(session, listOf(LOGIN_BIT_FLAG to true))
            responseSender.sendRoleCountUpdate(
                session,
                mapOf(
                    LOGIN_SIGN_DAY to (roleCountSystem.getValue(charId, LOGIN_SIGN_DAY) to 0),
                    CONTINUE_LOGIN to (roleCountSystem.getValue(charId, CONTINUE_LOGIN) to 0),
                )
            )
            sendClaimResult(session, AWARD_LOGIN, RESULT_SUCCESS)
            welfareLogger.debug { "Welfare login duplicate ACK: charId=$charId day=$requestedDay" }
            return
        }

        val expectedDay = roleCountSystem.getValue(charId, LOGIN_SIGN_DAY) + 1
        if (requestedDay != expectedDay) {
            sendClaimResult(session, AWARD_LOGIN, RESULT_CONDITION_NOT_MET)
            return
        }
        val award = loginAwardDefs.firstOrNull { it.group == AWARD_LOGIN && it.day == requestedDay }
        if (award == null) {
            sendClaimResult(session, AWARD_LOGIN, RESULT_BAD_REWARD)
            return
        }
        if (!grantAward(session, award)) return

        roleCountSystem.setValue(charId, LOGIN_SIGN_DAY, requestedDay)
        roleCountSystem.increment(charId, CONTINUE_LOGIN, 1)
        roleCountSystem.saveCounts(charId)
        stateRepo.setLastLoginClaimDay(charId, today)
        bitFlagSystem.setFlag(charId, LOGIN_BIT_FLAG)

        responseSender.sendBitFlagChanges(session, listOf(LOGIN_BIT_FLAG to true))
        responseSender.sendRoleCountUpdate(
            session,
            mapOf(
                LOGIN_SIGN_DAY to (roleCountSystem.getValue(charId, LOGIN_SIGN_DAY) to 0),
                CONTINUE_LOGIN to (roleCountSystem.getValue(charId, CONTINUE_LOGIN) to 0),
            )
        )
        syncVirtualBalances(session)
        sendClaimResult(session, AWARD_LOGIN, RESULT_SUCCESS)
        welfareLogger.info { "Welfare login claim: charId=$charId day=$requestedDay rewards=${award.rewards}" }
    }

    /**
     * EAward.OpenServer=4 — chính là tab đang hiển thị "Quà đăng nhập" của client Việt hoá.
     * Lua UpdateOpenServer dùng Role.GetLoginDay() >= row.day và BitFlag(row.flag) để quyết định
     * Available/Complete/Processing. LoginService hiện gửi S:003-001 loginDay=1, nên backend claim
     * phải dùng cùng giá trị để không mở sớm ngày 2+. Không tự suy diễn lịch sử login chưa tồn tại.
     */
    private suspend fun handleOpenServerClaim(session: GameSession, requestedDay: Int) {
        val charId = session.roleId.toLong()
        val award = loginAwardDefs.firstOrNull { it.group == AWARD_OPEN_SERVER && it.day == requestedDay }
        if (award == null) {
            sendClaimResult(session, AWARD_OPEN_SERVER, RESULT_BAD_REWARD)
            return
        }
        if (OPEN_SERVER_LOGIN_DAY < award.day) {
            sendClaimResult(session, AWARD_OPEN_SERVER, RESULT_CONDITION_NOT_MET)
            return
        }

        val flagId = award.flag
        if (flagId <= 0) {
            sendClaimResult(session, AWARD_OPEN_SERVER, RESULT_BAD_REWARD)
            return
        }
        if (bitFlagSystem.getSetFlagIds(charId).contains(flagId)) {
            // Idempotent ACK: client may click again before the flag redraw finishes.
            responseSender.sendBitFlagChanges(session, listOf(flagId to true))
            sendClaimResult(session, AWARD_OPEN_SERVER, RESULT_SUCCESS)
            return
        }

        if (!grantAward(session, award)) return
        bitFlagSystem.setFlag(charId, flagId)
        responseSender.sendBitFlagChanges(session, listOf(flagId to true))
        syncVirtualBalances(session)
        sendClaimResult(session, AWARD_OPEN_SERVER, RESULT_SUCCESS)
        welfareLogger.info {
            "Welfare open-server claim: charId=$charId day=$requestedDay flag=$flagId rewards=${award.rewards}"
        }
    }

    private suspend fun grantAward(session: GameSession, award: LoginAwardDef): Boolean {
        val charId = session.roleId.toLong()
        val rewards = award.rewards.filter { it.first > 0 && it.second > 0 }
        if (rewards.isEmpty()) {
            sendClaimResult(session, award.group, RESULT_BAD_REWARD)
            return false
        }

        val normalItems = rewards.filterNot { isVirtualReward(it.first) }
        val changedBag = if (normalItems.isNotEmpty()) {
            inventorySystem.addItemBundleToBagExact(charId, normalItems)
                ?: run { sendClaimResult(session, award.group, RESULT_BAG_FULL); return false }
        } else emptyList()

        for ((itemId, qty) in rewards) {
            if (!grantVirtualReward(charId, itemId, qty)) {
                welfareLogger.error { "Welfare reward lỗi: charId=$charId kind=${award.group} itemId=$itemId qty=$qty" }
                sendClaimResult(session, award.group, RESULT_BAD_REWARD)
                return false
            }
        }
        for ((slot, thing) in changedBag) responseSender.sendSetBagItem(session, slot, thing)
        return true
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
        private const val AWARD_OPEN_SERVER = 4
        private const val OPEN_SERVER_LOGIN_DAY = 1
        private const val LOGIN_BIT_FLAG = 1
        private const val LOGIN_SIGN_DAY = 107
        private const val CONTINUE_LOGIN = 108
        private const val RESULT_SUCCESS = 0
        private const val RESULT_WRONG_KIND = 1
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
