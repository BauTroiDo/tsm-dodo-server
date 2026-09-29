package tw.tsm.app.handlers

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.constants.EAttribute
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.PacketHandler
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.domain.interfaces.repositories.CharacterRepository
import tw.tsm.domain.interfaces.repositories.SkillRepository
import tw.tsm.gamelogic.systems.FlyPointSystem
import tw.tsm.gamelogic.systems.InventorySystem
import tw.tsm.gamelogic.systems.PartySystem
import tw.tsm.gamelogic.systems.PointSystem
import tw.tsm.gamelogic.systems.RoleCountSystem
import tw.tsm.gamelogic.systems.SkillSystem
import tw.tsm.gamelogic.systems.SlotMachineSystem
import tw.tsm.gamelogic.systems.UISellSystem
import tw.tsm.gamelogic.systems.UiSellQuote
import tw.tsm.gamelogic.systems.WorldManager
import tw.tsm.gamelogic.systems.PlayerStateManager
import tw.tsm.protocol.PacketReader

private val logger = KotlinLogging.logger {}

/** Bản đồ cấm dịch chuyển ký ức (tương ứng sceneId == 10701 trong UITeleport.CheckTeleport của client)*/
private const val FORBIDDEN_TELEPORT_MAP = 10701

/**
 * Handler tiêu hao giao diện (mainKind=84)
 *
 * Nguồn: protocal.lua protocolTable[84], Logic/UISell.lua
 *
 * C:084-001 truy vấn dữ liệu 介消 — +số hiệu 介消(2) +args(2×N)
 * C:084-002 sử dụng 介消 — +loại(1) +số hiệu 介消(2) loại 1: vật phẩm +chỉ số túi đồ(1); loại 2: điểm/miễn phí +args(2×N)
 *
 * 介消 đã cài đặt:
 * - sellId=1/2/3/56 mở rộng số ô Tiền Trang/lữ quán/túi đồ/kho báu (RoleCount tăng dần)
 * - sellId=7 sử dụng dịch chuyển ký ức (ưu tiên vật phẩm 46474, nguyên bảo dự phòng)
 * - sellId=8 mở rộng điểm dịch chuyển ký ức (giá tăng dần, tối đa 5 ô)
 */
class UISellHandler(
    private val responseSender: ResponseSender,
    private val uiSellSystem: UISellSystem,
    private val pointSystem: PointSystem,
    private val inventorySystem: InventorySystem,
    private val flyPointSystem: FlyPointSystem,
    private val worldManager: WorldManager,
    private val characterRepository: CharacterRepository,
    private val partySystem: PartySystem,
    private val roleCountSystem: RoleCountSystem,
    private val skillRepository: SkillRepository,
    private val playerStateManager: PlayerStateManager,
    private val skillSystem: SkillSystem,
    private val slotMachineSystem: SlotMachineSystem
) : PacketHandler {

    override val mainKind: Int = Opcodes.UI_SELL

    override suspend fun handle(session: GameSession, subKind: Int, data: ByteArray) {
        if (session.state != SessionState.IN_GAME) return

        when (subKind) {
            1 -> handleQuery(session, data)
            2 -> handleUse(session, data)
            else -> logger.warn { "Sub-protocol ký gửi chưa xử lý: subKind=$subKind" }
        }
    }

    /**
     * C:084-001 truy vấn dữ liệu 介消
     *
     * Định dạng: +số hiệu 介消(2) +args(2×N)
     */
    private suspend fun handleQuery(session: GameSession, data: ByteArray) {
        if (data.size < 2) return
        val reader = PacketReader(data)
        val sellId = reader.readUShortLE()
        val args = readArgs(reader)

        val charId = session.roleId.toLong()
        val quote = if (sellId == UISellSystem.SELL_SLOT_COIN) {
            querySlotCoin(charId, args)
        } else {
            uiSellSystem.query(charId, sellId)
        }
        when (quote) {
            is UiSellQuote.Fail -> {
                responseSender.sendUiSellFail(session, 1, sellId, quote.reason, quote.value)
                logger.debug { "${session.characterName} tra cứu ký gửi thất bại: sellId=$sellId reason=${quote.reason}" }
            }
            is UiSellQuote.Money ->
                responseSender.sendUiSellMoneyQuery(session, sellId, quote.currencyKind, quote.cost)
            is UiSellQuote.Item ->
                responseSender.sendUiSellItemQuery(session, sellId, quote.itemId, quote.count)
        }

        logger.debug { "${session.characterName} tra cứu ký gửi: sellId=$sellId args=$args" }
    }

    /**
     * C:084-002 sử dụng UISell.
     *
     * DVT Lua:
     * - generic item path: useKind(1)+sellId(2)+bagIndex(UInt16)+args(UInt16...)
     * - sellId=11 item path đặc biệt:
     *   useKind(1)+sellId(2)+stackCount(1)+
     *   stackCount×[bagIndex(UInt16)+qty(UInt16)]+args(UInt16...)
     * - money/free path: useKind(1)+sellId(2)+args(UInt16...)
     */
    private suspend fun handleUse(session: GameSession, data: ByteArray) {
        if (data.size < 3) return
        val reader = PacketReader(data)
        val useKind = reader.readUByte()
        val sellId = reader.readUShortLE()

        if (sellId == UISellSystem.SELL_SLOT_COIN) {
            handleSlotCoinUse(session, useKind, reader)
            return
        }

        val bagIndex = if (useKind == USE_KIND_ITEM) {
            if (reader.remaining < 2) return
            reader.readUShortLE()
        } else {
            -1
        }
        val args = readArgs(reader)

        val charId = session.roleId.toLong()

        // kiểm tra hợp lệ lại báo giá (thiết kế vô trạng thái: lấy trạng thái hiện tại của server làm chuẩn, không tin client)
        val quote = uiSellSystem.query(charId, sellId)
        if (quote is UiSellQuote.Fail) {
            responseSender.sendUiSellFail(session, 2, sellId, quote.reason, quote.value)
            return
        }

        // kiểm tra hợp lệ hiệu ứng trước (kiểm tra trước rồi khấu trừ, tránh khấu trừ xong hiệu ứng thất bại)
        when (sellId) {
            UISellSystem.SELL_TURN3_UNLOCK_SKILL -> {
                val reason = validateTurn3Unlock(charId, args)
                if (reason != 0) {
                    responseSender.sendUiSellFail(session, 2, sellId, reason)
                    return
                }
            }
            UISellSystem.SELL_TURN3_CHANGE_ELEMENT -> {
                val reason = validateTurn3ElementChange(charId, args)
                if (reason != 0) {
                    responseSender.sendUiSellFail(session, 2, sellId, reason)
                    return
                }
            }
            UISellSystem.SELL_FLY_TELEPORT -> {
                val reason = validateFlyTeleport(session, args)
                if (reason != 0) {
                    responseSender.sendUiSellFail(session, 2, sellId, reason)
                    return
                }
            }
        }

        // khấu trừ tiền
        if (!processPayment(session, sellId, useKind, bagIndex, quote)) return

        // thực hiện hiệu ứng
        when (sellId) {
            UISellSystem.SELL_FLY_TELEPORT -> executeFlyTeleport(session, sellId, args)
            UISellSystem.SELL_FLY_EXTEND -> executeFlyExtend(session, sellId)
            UISellSystem.SELL_TURN3_UNLOCK_SKILL -> executeTurn3Unlock(session, sellId, args)
            UISellSystem.SELL_TURN3_CHANGE_ELEMENT -> executeTurn3ElementChange(session, sellId, args)
            else -> {
                val roleCountIndex = uiSellSystem.expansionRoleCountIndex(sellId)
                if (roleCountIndex != null) {
                    executeExpansion(session, sellId, roleCountIndex)
                }
            }
        }
    }

    /**
     * Báo giá sellId=11 của UISlotMachine.
     *
     * Lua client:
     * - args=[amount]      => tiêu hao item31171 x amount;
     * - args=[0, amount]   => tiêu hao real Point, 9 Point / 1 xu.
     */
    private suspend fun querySlotCoin(charId: Long, args: List<Int>): UiSellQuote {
        val amount: Int
        val itemRoute: Boolean
        when {
            args.size == 1 -> {
                amount = args[0]
                itemRoute = true
            }
            args.size == 2 && args[0] == 0 -> {
                amount = args[1]
                itemRoute = false
            }
            else -> return UiSellQuote.Fail(UISellSystem.REASON_NOT_USABLE)
        }

        if (amount <= 0) return UiSellQuote.Fail(UISellSystem.REASON_NOT_USABLE)
        val coin = slotMachineSystem.getCoin(charId)
        if (coin + amount > SlotMachineSystem.MAX_COIN) {
            return UiSellQuote.Fail(UISellSystem.REASON_LIMIT_REACHED)
        }

        return if (itemRoute) {
            UiSellQuote.Item(UISellSystem.SLOT_COIN_ITEM_ID, amount)
        } else {
            UiSellQuote.Money(
                UISellSystem.CURRENCY_GOLD,
                amount.toLong() * UISellSystem.SLOT_COIN_POINT_PRICE
            )
        }
    }

    /**
     * C84-2 special của sellId=11.
     *
     * Item route dùng nhiều stack UInt16 đúng UISell.ReceiveQuery().
     * Point route dùng args=[0, amount].
     */
    private suspend fun handleSlotCoinUse(
        session: GameSession,
        useKind: Int,
        reader: PacketReader
    ) {
        val charId = session.roleId.toLong()
        val sellId = UISellSystem.SELL_SLOT_COIN

        if (useKind == USE_KIND_ITEM) {
            if (reader.remaining < 1) {
                responseSender.sendUiSellFail(session, 2, sellId, UISellSystem.REASON_ITEM_DEDUCT_FAIL)
                return
            }

            val stackCount = reader.readUByte()
            if (stackCount <= 0 || reader.remaining < stackCount * 4) {
                responseSender.sendUiSellFail(session, 2, sellId, UISellSystem.REASON_ITEM_DEDUCT_FAIL)
                return
            }

            val consumptions = ArrayList<Pair<Int, Int>>(stackCount)
            repeat(stackCount) {
                val bagIndex = reader.readUShortLE()
                val qty = reader.readUShortLE()
                consumptions += bagIndex to qty
            }
            val args = readArgs(reader)
            val quote = querySlotCoin(charId, args)
            if (quote !is UiSellQuote.Item ||
                consumptions.map { it.first }.toSet().size != consumptions.size ||
                consumptions.sumOf { it.second } != quote.count
            ) {
                responseSender.sendUiSellFail(session, 2, sellId, UISellSystem.REASON_PRICE_ERROR)
                return
            }

            for ((slot, qty) in consumptions) {
                val thing = inventorySystem.getBagItem(charId, slot)
                if (qty <= 0 || thing == null ||
                    thing.itemId != UISellSystem.SLOT_COIN_ITEM_ID ||
                    thing.quantity < qty
                ) {
                    responseSender.sendUiSellFail(
                        session, 2, sellId, UISellSystem.REASON_ITEM_DEDUCT_FAIL
                    )
                    return
                }
            }

            val amount = quote.count
            val updated = try {
                slotMachineSystem.depositCoinWithPayment(charId, amount) {
                    inventorySystem.removeBagItemsExact(charId, consumptions)
                }
            } catch (e: Exception) {
                logger.error(e) { "Nạp xu SlotMachine bằng item thất bại: charId=" + charId }
                null
            }

            if (updated == null) {
                responseSender.sendUiSellFail(
                    session, 2, sellId, UISellSystem.REASON_ITEM_DEDUCT_FAIL
                )
                return
            }

            consumptions.forEach { (slot, qty) ->
                responseSender.sendReduceBagItem(session, slot, qty)
            }
            responseSender.sendSlotMachineCoin(session, updated.coin)
            responseSender.sendUiSellSuccess(session, sellId)
            logger.info {
                session.characterName + " nạp " + amount +
                    " xu Vòng quay bằng item31171, coin=" + updated.coin
            }
            return
        }

        if (useKind == USE_KIND_POINT) {
            val args = readArgs(reader)
            val quote = querySlotCoin(charId, args)
            if (quote !is UiSellQuote.Money || args.size != 2 || args[0] != 0) {
                responseSender.sendUiSellFail(session, 2, sellId, UISellSystem.REASON_PRICE_ERROR)
                return
            }

            val amount = args[1]
            val cost = quote.cost.toInt()
            val updated = try {
                slotMachineSystem.depositCoinWithPayment(charId, amount) {
                    if (!pointSystem.subtractRealPoint(charId, cost)) {
                        false
                    } else {
                        try {
                            pointSystem.savePoints(charId)
                            true
                        } catch (e: Exception) {
                            pointSystem.addRealPoint(charId, cost)
                            try {
                                pointSystem.savePoints(charId)
                            } catch (rollbackError: Exception) {
                                logger.error(rollbackError) {
                                    "Rollback Point nạp SlotMachine thất bại: charId=" + charId
                                }
                            }
                            logger.error(e) {
                                "Persist Point nạp SlotMachine thất bại: charId=" + charId
                            }
                            false
                        }
                    }
                }
            } catch (e: Exception) {
                logger.error(e) { "Nạp xu SlotMachine bằng Point thất bại: charId=" + charId }
                null
            }

            if (updated == null) {
                responseSender.sendUiSellFail(
                    session, 2, sellId, UISellSystem.REASON_GOLD_NOT_ENOUGH
                )
                return
            }

            val (real, virtual) = pointSystem.getPoints(charId)
            responseSender.sendPointBalance(session, real, virtual)
            responseSender.sendSlotMachineCoin(session, updated.coin)
            responseSender.sendUiSellSuccess(session, sellId)
            logger.info {
                session.characterName + " nạp " + amount + " xu Vòng quay bằng " +
                    cost + " Point, coin=" + updated.coin
            }
            return
        }

        responseSender.sendUiSellFail(session, 2, sellId, UISellSystem.REASON_PRICE_ERROR)
    }

    /**
     * Đọc args ở cuối gói tin (mỗi trường 2B LE)
     */
    private fun readArgs(reader: PacketReader): List<Int> {
        val args = mutableListOf<Int>()
        while (reader.remaining >= 2) {
            args.add(reader.readUShortLE())
        }
        return args
    }

    /**
     * Thực hiện khấu trừ tiền
     *
     * @return true=khấu trừ tiền thành công; khi thất bại đã trả về gói tin lỗi
     */
    private suspend fun processPayment(
        session: GameSession, sellId: Int, useKind: Int, bagIndex: Int, quote: UiSellQuote
    ): Boolean {
        val charId = session.roleId.toLong()

        when (quote) {
            is UiSellQuote.Item -> {
                // đường dẫn client chọn vật phẩm: kiểm tra hợp lệ vật phẩm ô túi đồ khớp với báo giá
                if (useKind != USE_KIND_ITEM) {
                    responseSender.sendUiSellFail(session, 2, sellId, UISellSystem.REASON_PRICE_ERROR)
                    return false
                }
                val thing = inventorySystem.getBagItem(charId, bagIndex)
                if (thing == null || thing.itemId != quote.itemId || thing.quantity < quote.count) {
                    responseSender.sendUiSellFail(session, 2, sellId, UISellSystem.REASON_ITEM_DEDUCT_FAIL)
                    return false
                }
                if (!inventorySystem.removeFromBag(charId, bagIndex, quote.count)) {
                    responseSender.sendUiSellFail(session, 2, sellId, UISellSystem.REASON_ITEM_DEDUCT_FAIL)
                    return false
                }
                responseSender.sendReduceBagItem(session, bagIndex, quote.count)
                logger.info { "${session.characterName} ký gửi trừ vật phẩm: sellId=$sellId item=${quote.itemId}×${quote.count} bag=$bagIndex" }
            }
            is UiSellQuote.Money -> {
                if (useKind != USE_KIND_POINT) {
                    responseSender.sendUiSellFail(session, 2, sellId, UISellSystem.REASON_PRICE_ERROR)
                    return false
                }
                if (!pointSystem.subtractRealPoint(charId, quote.cost.toInt())) {
                    responseSender.sendUiSellFail(session, 2, sellId, UISellSystem.REASON_GOLD_NOT_ENOUGH)
                    return false
                }
                // làm mới hiển thị nguyên bảo trên client
                val (real, virtual) = pointSystem.getPoints(charId)
                responseSender.sendPointBalance(session, real, virtual)
                logger.info { "${session.characterName} ký gửi trừ nguyên bảo: sellId=$sellId cost=${quote.cost} còn lại=$real" }
            }
            is UiSellQuote.Fail -> return false  // đã được xử lý ở bên gọi
        }
        return true
    }

    private suspend fun validateTurn3Unlock(charId: Long, args: List<Int>): Int {
        if (args.size != 1) return UISellSystem.REASON_NOT_USABLE
        val state = playerStateManager.getState(charId) ?: return UISellSystem.REASON_NOT_USABLE
        if (state.turn < 3) return UISellSystem.REASON_NOT_USABLE
        val skillId = args[0]
        if (skillId !in TURN3_LIGHT_SKILLS && skillId !in TURN3_DARK_SKILLS) return UISellSystem.REASON_NOT_USABLE
        val turn3 = skillRepository.findTurn3State(charId)
        val expected = if (skillId in TURN3_LIGHT_SKILLS) TURN3_LIGHT else TURN3_DARK
        if (turn3.element != expected || turn3.skills.containsKey(skillId)) return UISellSystem.REASON_NOT_USABLE
        return 0
    }

    private suspend fun validateTurn3ElementChange(charId: Long, args: List<Int>): Int {
        if (args.size != 1) return UISellSystem.REASON_NOT_USABLE
        val state = playerStateManager.getState(charId) ?: return UISellSystem.REASON_NOT_USABLE
        if (state.turn < 3) return UISellSystem.REASON_NOT_USABLE
        val turn3 = skillRepository.findTurn3State(charId)
        val target = args[0]
        if (target !in setOf(TURN3_LIGHT, TURN3_DARK) || turn3.element == 0 || target == turn3.element) {
            return UISellSystem.REASON_NOT_USABLE
        }
        return 0
    }

    private suspend fun executeTurn3Unlock(session: GameSession, sellId: Int, args: List<Int>) {
        val charId = session.roleId.toLong()
        val skillId = args.single()
        val state = skillRepository.findTurn3State(charId)
        val updated = state.copy(skills = state.skills + (skillId to 1))
        skillRepository.saveTurn3State(charId, updated)
        responseSender.sendSetAttribute(session, EAttribute.TURN3_ELEMENT, updated.element, true)
        sendMergedSkillState(session, charId, updated.skills)
        responseSender.sendUiSellSuccess(session, sellId)
        logger.info { "${session.characterName} unlocked Turn3 skill=$skillId" }
    }

    private suspend fun executeTurn3ElementChange(session: GameSession, sellId: Int, args: List<Int>) {
        val charId = session.roleId.toLong()
        val target = args.single()
        val state = skillRepository.findTurn3State(charId)
        // Skill khác hệ bị loại bỏ để client không giữ skill trái với Turn3Element.
        val allowed = if (target == TURN3_LIGHT) TURN3_LIGHT_SKILLS else TURN3_DARK_SKILLS
        skillRepository.saveTurn3State(charId, state.copy(
            element = target,
            skills = state.skills.filterKeys { it in allowed }
        ))
        session.turn3Element = target
        responseSender.sendSetAttribute(session, EAttribute.TURN3_ELEMENT, target, true)
        sendMergedSkillState(session, charId, state.skills.filterKeys { it in allowed })
        responseSender.sendUiSellSuccess(session, sellId)
        logger.info { "${session.characterName} changed Turn3 element=$target" }
    }

    private suspend fun sendMergedSkillState(
        session: GameSession,
        charId: Long,
        turn3Skills: Map<Int, Int>
    ) {
        // S8:13 clears the client's whole skill table before reloading it, so Turn3
        // skills must be merged with ordinary persisted skills rather than sent alone.
        // SkillSystem cache is authoritative while the player is online; upgrades can be
        // dirty and not persisted yet when UISell 57/58 triggers a full S8:13 reload.
        val normalSkills = skillSystem.getCache(charId)?.skills?.toMap()
            ?: skillRepository.findByCharacterId(charId)
        val state = playerStateManager.getState(charId) ?: return
        responseSender.sendPlayerSkillData(
            session,
            state.element,
            state.skillPoint,
            normalSkills + turn3Skills
        )
    }

    /**
     * sellId=7 kiểm tra hợp lệ trước khi dịch chuyển ký ức
     *
     * Tương ứng UITeleport.CheckTeleport() của client: không thể dịch chuyển khi đang chiến đấu/ở cảnh cụ thể/đang tổ đội.
     * args = [loại (1=dịch chuyển ký ức), chỉ số điểm dịch chuyển]
     *
     * @return 0=đạt, còn lại là mã nguyên nhân thất bại
     */
    private suspend fun validateFlyTeleport(session: GameSession, args: List<Int>): Int {
        if (args.size < 2 || args[0] != 1) {
            // TODO: các loại dịch chuyển khác của args[0] != 1 chưa thấy trường hợp sử dụng trong client
            return UISellSystem.REASON_NOT_USABLE
        }
        if (session.battleId != 0L) return UISellSystem.REASON_NOT_USABLE
        if (session.mapId == FORBIDDEN_TELEPORT_MAP) return UISellSystem.REASON_SCENE_FORBIDDEN
        if (partySystem.isInParty(session.playerId)) return UISellSystem.REASON_NOT_USABLE

        val charId = session.roleId.toLong()
        val point = flyPointSystem.loadAll(charId)[args[1]]
        if (point == null || point.sceneId <= 0) return UISellSystem.REASON_NOT_USABLE

        return 0
    }

    /**
     * sellId=7 thực hiện dịch chuyển ký ức
     */
    private suspend fun executeFlyTeleport(session: GameSession, sellId: Int, args: List<Int>) {
        val charId = session.roleId.toLong()
        val index = args[1]
        val point = flyPointSystem.loadAll(charId)[index] ?: return

        responseSender.sendUiSellSuccess(session, sellId)

        worldManager.leaveMap(session)
        worldManager.enterMap(session, point.sceneId, point.x, point.y)
        characterRepository.updatePosition(charId, point.sceneId, point.x, point.y)
        responseSender.sendPlayerTeleport(session, session.playerId, point.sceneId, point.x, point.y)
        responseSender.sendSceneChange(
            session, session.playerId, point.sceneId, point.x, point.y,
            session.equipItemIds, session.titleId
        )

        logger.info { "${session.characterName} dịch chuyển điểm nhớ (ký gửi): index=$index → scene=${point.sceneId} (${point.x}, ${point.y})" }
    }

    /**
     * sellId=1/2/3/56 thực hiện mở rộng số ô
     *
     * Sau khi RoleCount tăng, gửi xuống bằng S:085-002, client Item.RefreshRoleCount /
     * Inn.RefreshRoleCount sẽ tự động tính lại số ô và làm mới giao diện.
     */
    private suspend fun executeExpansion(session: GameSession, sellId: Int, roleCountIndex: Int) {
        val charId = session.roleId.toLong()
        val newValue = roleCountSystem.increment(charId, roleCountIndex)
        val maxValue = roleCountSystem.getMaxValue(charId, roleCountIndex)

        responseSender.sendUiSellSuccess(session, sellId)
        responseSender.sendRoleCountUpdate(session, mapOf(roleCountIndex to (newValue to maxValue)))

        logger.info { "${session.characterName} mở rộng số ô ký gửi: sellId=$sellId roleCount[$roleCountIndex]=$newValue" }
    }

    /**
     * sellId=8 thực hiện mở rộng điểm dịch chuyển ký ức
     */
    private suspend fun executeFlyExtend(session: GameSession, sellId: Int) {
        val charId = session.roleId.toLong()
        val newCount = uiSellSystem.incrementPurchaseCount(charId, sellId)

        responseSender.sendUiSellSuccess(session, sellId)

        // gửi lại dữ liệu phù dịch chuyển ký ức, client dựa vào đó mở khoá ô mới
        val points = flyPointSystem.loadAll(charId)
        responseSender.sendFlyPointData(session, newCount, points)

        logger.info { "${session.characterName} mở rộng điểm nhớ dịch chuyển: đã mở rộng $newCount ô" }
    }

    companion object {
        /** C:084-002 loại 1: tiêu hao vật phẩm*/
        private const val USE_KIND_ITEM = 1

        /** C:084-002 loại 2: điểm/miễn phí*/
        private const val USE_KIND_POINT = 2

        private const val TURN3_LIGHT = 7
        private const val TURN3_DARK = 8
        private val TURN3_LIGHT_SKILLS = setOf(22001,22002,22003,22004,22007,22010,22015,22016,22011)
        private val TURN3_DARK_SKILLS = setOf(23001,23002,23003,23004,23009,23010,23015,23016,23011)
    }
}
