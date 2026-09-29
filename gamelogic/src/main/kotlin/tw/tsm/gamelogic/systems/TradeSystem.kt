package tw.tsm.gamelogic.systems

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.data.ItemDef
import tw.tsm.domain.enums.TradePlayerState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.domain.interfaces.StatCalculator
import tw.tsm.domain.valueobjects.FollowNpcData
import tw.tsm.domain.valueobjects.ThingData
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

/**
 * Hệ thống giao dịch người chơi — Tương ứng `UITrade.lua` với 2 chế độ: giao dịch vật phẩm / giao dịch võ tướng
 *
 * Giao thức: mainKind=25
 * - Giao dịch vật phẩm: sub 1 khởi xướng / 2 tải lên / 3 xác nhận, hủy trả về S:025-002
 * - Giao dịch võ tướng: sub 10 khởi xướng / 11 tải lên / 12 xác nhận, hủy trả về S:025-011
 * Quy trình: Khởi xướng → Hai bên tải lên giá đưa ra → Xem giá của đối phương → Hai bên xác nhận → Server thực hiện trao đổi nguyên tử
 *
 * @param inventorySystem Quản lý túi đồ
 * @param moneySystem Quản lý tiền tệ
 * @param worldManager Tìm kiếm người chơi trực tuyến
 * @param responseSender Gửi gói tin
 * @param itemDefs Bảng định nghĩa vật phẩm (dùng để xác thực restrict)
 * @param petSystem Quản lý võ tướng (giao dịch võ tướng)
 * @param battleManager Truy vấn trạng thái chiến đấu (cấm giao dịch khi đang chiến đấu)
 * @param statCalculator Tính toán MaxHP/SP của võ tướng (dùng để hiển thị cho S:025-012)
 */
class TradeSystem(
    private val inventorySystem: InventorySystem,
    private val moneySystem: MoneySystem,
    private val worldManager: WorldManager,
    private val responseSender: ResponseSender,
    private val itemDefs: Map<Int, ItemDef>,
    private val petSystem: PetSystem,
    private val battleManager: BattleManager,
    private val statCalculator: StatCalculator
) {
    /** Nội dung giá đưa ra trong giao dịch (Chế độ vật phẩm dùng bagIndices; chế độ võ tướng dùng petSlot, 0 = chỉ đưa tiền) */
    data class TradeOffer(
        val money: Int,
        val bagIndices: List<Int> = emptyList(),
        val petSlot: Int = 0
    )

    /** Phiên giao dịch */
    class TradeSession(
        val playerA: Long,
        val playerB: Long,
        val mode: Int,
        @Volatile var stateA: TradePlayerState = TradePlayerState.WaitingUpload,
        @Volatile var stateB: TradePlayerState = TradePlayerState.WaitingUpload,
        @Volatile var offerA: TradeOffer? = null,
        @Volatile var offerB: TradeOffer? = null
    )

    /** playerId → Phiên giao dịch tương ứng (hai key cùng trỏ về một session) */
    private val activeTrades = ConcurrentHashMap<Long, TradeSession>()

    companion object {
        /** Chế độ giao dịch: Vật phẩm */
        const val MODE_ITEM = 1
        /** Chế độ giao dịch: Võ tướng */
        const val MODE_PET = 2

        // ── Mã lý do hủy (reason S:025-002 / S:025-011, đối chiếu nghiêm ngặt với protocal.lua:6189-6284) ──

        /** Hệ thống đang bận, vui lòng giao dịch sau (40010) */
        const val REASON_BUSY = 0
        /** Đối phương đã ngắt kết nối, không thể giao dịch (40011) */
        const val REASON_OFFLINE = 1
        /** Đối phương đang bận, không thể giao dịch (40012, đang trong một giao dịch khác) */
        const val REASON_PARTNER_BUSY = 2
        /** Đối phương hủy giao dịch (40013) */
        const val REASON_PARTNER_CANCELLED = 3
        /** Hai bên giao dịch hoàn tất (40014) */
        const val REASON_SUCCESS = 4
        /** Hai bên không cùng map, không thể giao dịch (40015) */
        const val REASON_DIFFERENT_SCENE = 5
        /** Một bên không đủ tiền vàng, hủy giao dịch (40016) */
        const val REASON_GOLD_INSUFFICIENT = 6
        /** Một bên đặt quá nhiều vật phẩm, hủy giao dịch (40017, túi đồ/tùy tùng đã đầy) */
        const val REASON_BAG_FULL = 7
        /** Đang trong trận chiến không thể giao dịch (40018) */
        const val REASON_IN_BATTLE = 8
        /** Hệ thống ngắt giao dịch (40019, lỗi chung; chế độ võ tướng 9 = 40071 Dữ liệu võ tướng không khớp) */
        const val REASON_SYSTEM = 9

        // ── Mã lý do hủy dành riêng cho giao dịch Võ tướng (S:025-011, protocal.lua:6240-6284) ──

        /** Dữ liệu võ tướng không khớp, hủy giao dịch (40071) */
        const val REASON_PET_MISMATCH = 9
        /** Trên người chỉ có thể mang tối đa 4 võ tướng, hủy giao dịch (40081) */
        const val REASON_PET_FOLLOW_FULL = 10
        /** Bản thân hoặc đối phương có võ tướng trùng lặp trong khách sạn, hủy giao dịch (40083) */
        const val REASON_PET_DUP_INN = 11
        /** Độ trung thành của võ tướng bằng 0, không thể giao dịch (40304) */
        const val REASON_PET_ZERO_LOYALTY = 12
        /** Bản thân hoặc đối phương có võ tướng trùng lặp trong xe ngựa, hủy giao dịch (40414) */
        const val REASON_PET_DUP_CART = 13
        /** Trên người võ tướng có mang vật phẩm không thể chuyển nhượng (40429) */
        const val REASON_PET_BOUND_EQUIP = 14
    }

    /** Kiểm tra xem người chơi có đang trong giao dịch không */
    fun isInTrade(playerId: Long): Boolean = activeTrades.containsKey(playerId)

    // ── Khởi xướng giao dịch (C:025-001 Vật phẩm / C:025-010 Võ tướng) ──

    /**
     * Xử lý yêu cầu giao dịch
     *
     * Xác thực trạng thái hai bên sau đó tạo TradeSession, gửi S:025-001 / S:025-010 tới bên được mời.
     */
    fun requestTrade(initiator: GameSession, targetId: Long, mode: Int = MODE_ITEM) {
        val initiatorId = initiator.playerId

        // Không thể tự giao dịch với chính mình
        if (initiatorId == targetId) return

        // Người khởi xướng đang trong giao dịch
        if (activeTrades.containsKey(initiatorId)) {
            sendCancel(initiator, mode, REASON_BUSY)
            return
        }

        // Kiểm tra người chơi mục tiêu có online không
        val target = worldManager.getPlayerById(targetId)
        if (target == null) {
            sendCancel(initiator, mode, REASON_OFFLINE)
            return
        }

        // Mục tiêu đang trong giao dịch
        if (activeTrades.containsKey(targetId)) {
            sendCancel(initiator, mode, REASON_PARTNER_BUSY)
            return
        }

        // Kiểm tra cùng map (40015)
        if (initiator.mapId != target.mapId) {
            sendCancel(initiator, mode, REASON_DIFFERENT_SCENE)
            return
        }

        // Kiểm tra trạng thái chiến đấu (40018)
        if (battleManager.isInBattle(initiatorId) || battleManager.isInBattle(targetId)) {
            sendCancel(initiator, mode, REASON_IN_BATTLE)
            return
        }

        // Tạo phiên giao dịch
        val session = TradeSession(playerA = initiatorId, playerB = targetId, mode = mode)

        // Sử dụng putIfAbsent để ngăn chặn tạo đồng thời
        if (activeTrades.putIfAbsent(initiatorId, session) != null) {
            sendCancel(initiator, mode, REASON_BUSY)
            return
        }
        if (activeTrades.putIfAbsent(targetId, session) != null) {
            // Đối phương trong khoảnh khắc này đã bị người khác mời, rollback lại
            activeTrades.remove(initiatorId)
            sendCancel(initiator, mode, REASON_PARTNER_BUSY)
            return
        }

        logger.info { "Tạo giao dịch (mode=$mode):${initiator.characterName}($initiatorId) →$targetId" }

        // Gửi thông báo bắt đầu giao dịch cho bên được mời
        if (mode == MODE_PET) {
            responseSender.sendPetTradeStart(target, initiatorId)
        } else {
            responseSender.sendTradeStart(target, initiatorId)
        }
    }

    // ── C:025-002 Tải lên vật phẩm giao dịch ──

    /**
     * Xử lý người chơi tải lên vật phẩm giao dịch
     *
     * Xác thực tính khả thi giao dịch của vật phẩm, sau khi cả hai bên tải lên xong thì gửi S:025-003 cho cả hai để hiển thị giá đối phương đưa ra.
     */
    fun uploadItems(playerId: Long, money: Int, bagIndices: List<Int>) {
        val trade = activeTrades[playerId] ?: return
        synchronized(trade) {
            if (trade.mode != MODE_ITEM) return
            val isA = (playerId == trade.playerA)
            val myState = if (isA) trade.stateA else trade.stateB

            // Chỉ có thể tải lên ở trạng thái WaitingUpload
            if (myState !is TradePlayerState.WaitingUpload) return

            // Xác thực tiền
            val characterId = getCharacterId(playerId) ?: return
            if (money < 0) return
            if (money > 0 && moneySystem.getGold(characterId) < money) {
                cancelTradeInternal(trade, playerId, REASON_GOLD_INSUFFICIENT, notifyInitiator = true)
                return
            }

            // Xác thực khả năng giao dịch của vật phẩm
            for (slot in bagIndices) {
                val item = inventorySystem.getBagItem(characterId, slot)
                if (item == null || item.isEmpty) {
                    cancelTradeInternal(trade, playerId, REASON_SYSTEM, notifyInitiator = true)
                    return
                }
                if (!isItemTradeable(item)) {
                    // Client đã chặn trước (UITrade không thể tick chọn), Server dùng mã lỗi chung để ngắt
                    cancelTradeInternal(trade, playerId, REASON_SYSTEM, notifyInitiator = true)
                    return
                }
            }

            // Lưu giá đưa ra và cập nhật trạng thái
            val offer = TradeOffer(money = money, bagIndices = bagIndices)
            if (isA) {
                trade.offerA = offer
                trade.stateA = TradePlayerState.Uploaded
            } else {
                trade.offerB = offer
                trade.stateB = TradePlayerState.Uploaded
            }

            logger.info { "Tải lên giao dịch: playerId=$playerId Tiền=$money Vật phẩm=${bagIndices.size} món" }

            // Cả hai bên đều đã tải lên → Gửi giá đưa ra của đối phương cho cả hai
            if (trade.stateA is TradePlayerState.Uploaded && trade.stateB is TradePlayerState.Uploaded) {
                sendTradeOffers(trade)
            }
        }
    }

    // ── C:025-011 Tải lên võ tướng giao dịch ──

    /**
     * Xử lý người chơi tải lên võ tướng giao dịch
     *
     * @param petSlot Ô võ tướng mang theo (1-4), 0 = Chỉ đưa tiền không đưa võ tướng
     */
    fun uploadPet(playerId: Long, money: Int, petSlot: Int) {
        val trade = activeTrades[playerId] ?: return
        synchronized(trade) {
            if (trade.mode != MODE_PET) return
            val isA = (playerId == trade.playerA)
            val myState = if (isA) trade.stateA else trade.stateB
            if (myState !is TradePlayerState.WaitingUpload) return

            val characterId = getCharacterId(playerId) ?: return
            if (money < 0) return
            if (money > 0 && moneySystem.getGold(characterId) < money) {
                cancelTradeInternal(trade, playerId, REASON_GOLD_INSUFFICIENT, notifyInitiator = true)
                return
            }

            // Xác thực võ tướng đưa ra
            if (petSlot > 0) {
                val pet = petSystem.getFollowNpc(characterId, petSlot)
                if (pet == null) {
                    cancelTradeInternal(trade, playerId, REASON_PET_MISMATCH, notifyInitiator = true)
                    return
                }
                val reason = validatePetGivable(pet)
                if (reason != null) {
                    cancelTradeInternal(trade, playerId, reason, notifyInitiator = true)
                    return
                }
            }

            val offer = TradeOffer(money = money, petSlot = petSlot)
            if (isA) {
                trade.offerA = offer
                trade.stateA = TradePlayerState.Uploaded
            } else {
                trade.offerB = offer
                trade.stateB = TradePlayerState.Uploaded
            }

            logger.info { "Tải lên võ tướng giao dịch: playerId=$playerId Tiền=$money petSlot=$petSlot" }

            if (trade.stateA is TradePlayerState.Uploaded && trade.stateB is TradePlayerState.Uploaded) {
                sendPetTradeOffers(trade)
            }
        }
    }

    // ── Xác nhận/Hủy (C:025-003 Vật phẩm / C:025-012 Võ tướng) ──

    /**
     * Xử lý xác nhận hoặc hủy giao dịch
     *
     * @param result 1=xác nhận, 2=hủy
     * @param expectedMode Chế độ tương ứng với sub protocol gốc (ngăn chặn giả mạo xác nhận chéo chế độ)
     */
    fun confirmOrCancel(playerId: Long, result: Int, expectedMode: Int = MODE_ITEM) {
        val trade = activeTrades[playerId] ?: return
        synchronized(trade) {
            if (trade.mode != expectedMode) return
            if (result == 2) {
                // Hủy: Chỉ cần thông báo cho đối phương (UI của mình đã đóng)
                cancelTradeInternal(trade, playerId, REASON_PARTNER_CANCELLED, notifyInitiator = false)
                return
            }

            // result == 1: Xác nhận
            val isA = (playerId == trade.playerA)
            val myState = if (isA) trade.stateA else trade.stateB

            // Chỉ có thể xác nhận ở trạng thái Uploaded
            if (myState !is TradePlayerState.Uploaded) return

            if (isA) {
                trade.stateA = TradePlayerState.Confirmed
            } else {
                trade.stateB = TradePlayerState.Confirmed
            }

            logger.info { "Xác nhận giao dịch (mode=${trade.mode}): playerId=$playerId" }

            // Cả hai bên đều xác nhận → Thực hiện trao đổi
            if (trade.stateA is TradePlayerState.Confirmed && trade.stateB is TradePlayerState.Confirmed) {
                if (trade.mode == MODE_PET) {
                    executePetTrade(trade)
                } else {
                    executeTrade(trade)
                }
            }
        }
    }

    // ── Ngắt kết nối/Hủy từ bên ngoài ──

    /**
     * Hủy giao dịch khi người chơi ngắt kết nối
     */
    fun cancelTradeOnDisconnect(playerId: Long) {
        val trade = activeTrades[playerId] ?: return
        synchronized(trade) {
            cancelTradeInternal(trade, playerId, REASON_OFFLINE, notifyInitiator = false)
        }
    }

    // ── Phương thức nội bộ ──

    /** Gửi gói tin hủy theo chế độ */
    private fun sendCancel(session: GameSession, mode: Int, reason: Int) {
        if (mode == MODE_PET) {
            responseSender.sendPetTradeCancel(session, reason)
        } else {
            responseSender.sendTradeCancel(session, reason)
        }
    }

    /**
     * Hủy giao dịch (Sử dụng nội bộ, phải được gọi trong khối synchronized)
     *
     * @param notifyInitiator true = Thông báo cho cả người kích hoạt (khi xác thực thất bại Server chủ động ngắt, cửa sổ giao dịch của người kích hoạt vẫn mở nên cần nhận gói tin để đóng); khi người chơi chủ động hủy thì UI đã tự đóng, truyền false
     */
    private fun cancelTradeInternal(trade: TradeSession, initiatorId: Long, reason: Int, notifyInitiator: Boolean) {
        // Xóa ánh xạ
        activeTrades.remove(trade.playerA)
        activeTrades.remove(trade.playerB)

        // Thông báo cho đối phương
        val partnerId = if (initiatorId == trade.playerA) trade.playerB else trade.playerA
        worldManager.getPlayerById(partnerId)?.let { sendCancel(it, trade.mode, reason) }

        if (notifyInitiator) {
            worldManager.getPlayerById(initiatorId)?.let { sendCancel(it, trade.mode, reason) }
        }

        logger.info { "Hủy giao dịch (mode=${trade.mode}): initiator=$initiatorId partner=$partnerId reason=$reason" }
    }

    /**
     * Gửi nội dung giao dịch của đối phương cho cả hai bên (S:025-003)
     */
    private fun sendTradeOffers(trade: TradeSession) {
        val sessionA = worldManager.getPlayerById(trade.playerA)
        val sessionB = worldManager.getPlayerById(trade.playerB)
        if (sessionA == null || sessionB == null) {
            cancelTradeInternal(trade, trade.playerA, REASON_OFFLINE, notifyInitiator = true)
            return
        }

        val charIdA = getCharacterId(trade.playerA)
        val charIdB = getCharacterId(trade.playerB)
        if (charIdA == null || charIdB == null) return

        val offerA = trade.offerA ?: return
        val offerB = trade.offerB ?: return

        // Thu thập vật phẩm A đưa ra → Gửi cho B
        val itemsA = offerA.bagIndices.mapNotNull { slot ->
            inventorySystem.getBagItem(charIdA, slot)
        }
        // Thu thập vật phẩm B đưa ra → Gửi cho A
        val itemsB = offerB.bagIndices.mapNotNull { slot ->
            inventorySystem.getBagItem(charIdB, slot)
        }

        responseSender.sendTradeConfirm(sessionA, offerB.money, itemsB)
        responseSender.sendTradeConfirm(sessionB, offerA.money, itemsA)
    }

    /**
     * Gửi võ tướng giao dịch của đối phương cho cả hai bên (S:025-012)
     */
    private fun sendPetTradeOffers(trade: TradeSession) {
        val sessionA = worldManager.getPlayerById(trade.playerA)
        val sessionB = worldManager.getPlayerById(trade.playerB)
        if (sessionA == null || sessionB == null) {
            cancelTradeInternal(trade, trade.playerA, REASON_OFFLINE, notifyInitiator = true)
            return
        }

        val charIdA = getCharacterId(trade.playerA) ?: return
        val charIdB = getCharacterId(trade.playerB) ?: return
        val offerA = trade.offerA ?: return
        val offerB = trade.offerB ?: return

        sendOnePetOffer(sessionA, charIdB, offerB)
        sendOnePetOffer(sessionB, charIdA, offerA)
    }

    /** Gửi giá võ tướng một bên đưa ra cho đối phương */
    private fun sendOnePetOffer(receiver: GameSession, giverCharId: Long, offer: TradeOffer) {
        val pet = if (offer.petSlot > 0) petSystem.getFollowNpc(giverCharId, offer.petSlot) else null
        if (pet != null) {
            val bonus = EquipBonusCalculator.calculateTotalBonus(pet.equips, itemDefs)
            val maxHp = statCalculator.calculateMaxHp(
                level = pet.level, hpx = pet.hpx + bonus.equipHpx,
                equipMaxHp = bonus.equipMaxHp, turn = pet.turnKind
            ).toInt()
            val maxSp = statCalculator.calculateMaxSp(
                level = pet.level, spx = pet.spx + bonus.equipSpx,
                equipMaxSp = bonus.equipMaxSp, turn = pet.turnKind
            )
            responseSender.sendPetTradeConfirm(receiver, offer.money, pet, maxHp, maxSp)
        } else {
            responseSender.sendPetTradeConfirm(receiver, offer.money, null, 0, 0)
        }
    }

    /**
     * Thực hiện giao dịch vật phẩm (Sau khi cả hai bên xác nhận)
     *
     * Xác thực lại vật phẩm và tiền, trao đổi nguyên tử.
     */
    private fun executeTrade(trade: TradeSession) {
        val charIdA = getCharacterId(trade.playerA)
        val charIdB = getCharacterId(trade.playerB)
        if (charIdA == null || charIdB == null) {
            cancelTradeInternal(trade, trade.playerA, REASON_SYSTEM, notifyInitiator = true)
            return
        }

        val offerA = trade.offerA ?: return
        val offerB = trade.offerB ?: return

        val sessionA = worldManager.getPlayerById(trade.playerA)
        val sessionB = worldManager.getPlayerById(trade.playerB)
        if (sessionA == null || sessionB == null) {
            cancelTradeInternal(trade, trade.playerA, REASON_OFFLINE, notifyInitiator = true)
            return
        }

        // Xác thực lại xem vật phẩm còn không
        val itemsA = mutableListOf<Pair<Int, ThingData>>() // (slot, thing)
        for (slot in offerA.bagIndices) {
            val item = inventorySystem.getBagItem(charIdA, slot)
            if (item == null || item.isEmpty) {
                cancelTradeInternal(trade, trade.playerA, REASON_SYSTEM, notifyInitiator = true)
                return
            }
            itemsA.add(Pair(slot, item))
        }

        val itemsB = mutableListOf<Pair<Int, ThingData>>()
        for (slot in offerB.bagIndices) {
            val item = inventorySystem.getBagItem(charIdB, slot)
            if (item == null || item.isEmpty) {
                cancelTradeInternal(trade, trade.playerB, REASON_SYSTEM, notifyInitiator = true)
                return
            }
            itemsB.add(Pair(slot, item))
        }

        // Xác thực tiền
        if (offerA.money > 0 && moneySystem.getGold(charIdA) < offerA.money) {
            cancelTradeInternal(trade, trade.playerA, REASON_GOLD_INSUFFICIENT, notifyInitiator = true)
            return
        }
        if (offerB.money > 0 && moneySystem.getGold(charIdB) < offerB.money) {
            cancelTradeInternal(trade, trade.playerB, REASON_GOLD_INSUFFICIENT, notifyInitiator = true)
            return
        }

        // Xác thực không gian túi đồ: Số lượng vật phẩm A nhận từ B, số lượng vật phẩm B nhận từ A
        // Vật phẩm giao ra sẽ làm trống ô, vật phẩm nhận vào sẽ chiếm ô, mức chênh lệch quyết định xem có cần thêm ô trống không
        val aNetIncoming = itemsB.size - itemsA.size
        if (aNetIncoming > 0) {
            val emptySlots = countEmptyBagSlots(charIdA)
            if (emptySlots < aNetIncoming) {
                cancelTradeInternal(trade, trade.playerA, REASON_BAG_FULL, notifyInitiator = true)
                return
            }
        }
        val bNetIncoming = itemsA.size - itemsB.size
        if (bNetIncoming > 0) {
            val emptySlots = countEmptyBagSlots(charIdB)
            if (emptySlots < bNetIncoming) {
                cancelTradeInternal(trade, trade.playerB, REASON_BAG_FULL, notifyInitiator = true)
                return
            }
        }

        // ── Thực hiện trao đổi ──

        // 1. Xóa vật phẩm của A
        for ((slot, _) in itemsA) {
            inventorySystem.setBagItemSync(charIdA, slot, null)
        }
        // 2. Xóa vật phẩm của B
        for ((slot, _) in itemsB) {
            inventorySystem.setBagItemSync(charIdB, slot, null)
        }
        // 3. Thêm vật phẩm của B cho A
        for ((_, thing) in itemsB) {
            inventorySystem.addItemDirectSync(charIdA, thing)
        }
        // 4. Thêm vật phẩm của A cho B
        for ((_, thing) in itemsA) {
            inventorySystem.addItemDirectSync(charIdB, thing)
        }
        // 5. Trao đổi tiền
        if (offerA.money > 0) {
            moneySystem.subtractGold(charIdA, offerA.money)
            moneySystem.addGold(charIdB, offerA.money)
        }
        if (offerB.money > 0) {
            moneySystem.subtractGold(charIdB, offerB.money)
            moneySystem.addGold(charIdA, offerB.money)
        }

        // Xóa giao dịch
        activeTrades.remove(trade.playerA)
        activeTrades.remove(trade.playerB)

        // Thông báo hai bên giao dịch hoàn tất (40014)
        responseSender.sendTradeCancel(sessionA, REASON_SUCCESS)
        responseSender.sendTradeCancel(sessionB, REASON_SUCCESS)

        // Cập nhật lại túi đồ và tiền
        responseSender.sendAllBagItems(sessionA, inventorySystem.getBag(charIdA), refresh = true)
        responseSender.sendAllBagItems(sessionB, inventorySystem.getBag(charIdB), refresh = true)
        val (goldA, auctionA) = moneySystem.getMoney(charIdA)
        val (goldB, auctionB) = moneySystem.getMoney(charIdB)
        responseSender.sendInitMoney(sessionA, goldA, auctionA)
        responseSender.sendInitMoney(sessionB, goldB, auctionB)

        logger.info {
            "Giao dịch hoàn tất: A=${trade.playerA}(đưa ${itemsA.size} món+${offerA.money} vàng) " +
                    "B=${trade.playerB}(đưa ${itemsB.size} món+${offerB.money} vàng)"
        }
    }

    /**
     * Thực hiện giao dịch võ tướng (Sau khi cả hai bên xác nhận)
     *
     * Xác thực lại võ tướng/tiền/sức chứa bên nhận và tính trùng lặp, trao đổi nguyên tử.
     */
    private fun executePetTrade(trade: TradeSession) {
        val charIdA = getCharacterId(trade.playerA)
        val charIdB = getCharacterId(trade.playerB)
        val sessionA = worldManager.getPlayerById(trade.playerA)
        val sessionB = worldManager.getPlayerById(trade.playerB)
        if (charIdA == null || charIdB == null || sessionA == null || sessionB == null) {
            cancelTradeInternal(trade, trade.playerA, REASON_OFFLINE, notifyInitiator = true)
            return
        }

        val offerA = trade.offerA ?: return
        val offerB = trade.offerB ?: return

        // Xác thực lại võ tướng đưa ra vẫn còn và có thể chuyển nhượng
        val petA = if (offerA.petSlot > 0) petSystem.getFollowNpc(charIdA, offerA.petSlot) else null
        val petB = if (offerB.petSlot > 0) petSystem.getFollowNpc(charIdB, offerB.petSlot) else null
        if (offerA.petSlot > 0 && petA == null) {
            cancelTradeInternal(trade, trade.playerA, REASON_PET_MISMATCH, notifyInitiator = true)
            return
        }
        if (offerB.petSlot > 0 && petB == null) {
            cancelTradeInternal(trade, trade.playerB, REASON_PET_MISMATCH, notifyInitiator = true)
            return
        }
        petA?.let { pet ->
            validatePetGivable(pet)?.let { reason ->
                cancelTradeInternal(trade, trade.playerA, reason, notifyInitiator = true)
                return
            }
        }
        petB?.let { pet ->
            validatePetGivable(pet)?.let { reason ->
                cancelTradeInternal(trade, trade.playerB, reason, notifyInitiator = true)
                return
            }
        }

        // Xác thực tiền
        if (offerA.money > 0 && moneySystem.getGold(charIdA) < offerA.money) {
            cancelTradeInternal(trade, trade.playerA, REASON_GOLD_INSUFFICIENT, notifyInitiator = true)
            return
        }
        if (offerB.money > 0 && moneySystem.getGold(charIdB) < offerB.money) {
            cancelTradeInternal(trade, trade.playerB, REASON_GOLD_INSUFFICIENT, notifyInitiator = true)
            return
        }

        // Xác thực bên nhận: Dung lượng tùy tùng + võ tướng trùng lặp (trừ đi con mình giao ra trước rồi mới tính)
        validatePetReceivable(charIdA, receiving = petB, giving = petA)?.let { reason ->
            cancelTradeInternal(trade, trade.playerA, reason, notifyInitiator = true)
            return
        }
        validatePetReceivable(charIdB, receiving = petA, giving = petB)?.let { reason ->
            cancelTradeInternal(trade, trade.playerB, reason, notifyInitiator = true)
            return
        }

        // ── Thực hiện trao đổi ──

        // 1. Xóa khỏi tùy tùng của hai bên (kickFollowNpc sẽ tiện thể xóa trạng thái xuất chiến)
        val removedA = petA?.let { petSystem.kickFollowNpc(charIdA, offerA.petSlot) }
        val removedB = petB?.let { petSystem.kickFollowNpc(charIdB, offerB.petSlot) }

        // 2. Thêm vào tùy tùng đối phương (Dung lượng đã xác thực, lý thuyết sẽ không thất bại; nếu thất bại thì rollback)
        val placedAtB = removedA?.let { petSystem.addExistingFollowNpc(charIdB, it) }
        val placedAtA = removedB?.let { petSystem.addExistingFollowNpc(charIdA, it) }
        if ((removedA != null && placedAtB == null) || (removedB != null && placedAtA == null)) {
            // Rollback: Trả về chủ cũ
            removedA?.let { petSystem.addExistingFollowNpc(charIdA, it) }
            removedB?.let { petSystem.addExistingFollowNpc(charIdB, it) }
            cancelTradeInternal(trade, trade.playerA, REASON_SYSTEM, notifyInitiator = true)
            return
        }

        // 3. Trao đổi tiền
        if (offerA.money > 0) {
            moneySystem.subtractGold(charIdA, offerA.money)
            moneySystem.addGold(charIdB, offerA.money)
        }
        if (offerB.money > 0) {
            moneySystem.subtractGold(charIdB, offerB.money)
            moneySystem.addGold(charIdA, offerB.money)
        }

        // Xóa giao dịch
        activeTrades.remove(trade.playerA)
        activeTrades.remove(trade.playerB)

        // Thông báo hai bên giao dịch hoàn tất (40014)
        responseSender.sendPetTradeCancel(sessionA, REASON_SUCCESS)
        responseSender.sendPetTradeCancel(sessionB, REASON_SUCCESS)

        // Đồng bộ danh sách võ tướng: Bên giao bị xóa, bên nhận nhận dữ liệu đầy đủ (S:015-008 sẽ tạo nhân vật)
        removedA?.let {
            responseSender.sendFollowNpcRemove(sessionA, sessionA.playerId, offerA.petSlot, showMsg = false)
        }
        removedB?.let {
            responseSender.sendFollowNpcRemove(sessionB, sessionB.playerId, offerB.petSlot, showMsg = false)
        }
        placedAtA?.let { (_, pet) -> responseSender.sendFollowNpcFullData(sessionA, listOf(pet)) }
        placedAtB?.let { (_, pet) -> responseSender.sendFollowNpcFullData(sessionB, listOf(pet)) }

        // Làm mới tiền
        val (goldA, auctionA) = moneySystem.getMoney(charIdA)
        val (goldB, auctionB) = moneySystem.getMoney(charIdB)
        responseSender.sendInitMoney(sessionA, goldA, auctionA)
        responseSender.sendInitMoney(sessionB, goldB, auctionB)

        logger.info {
            "Giao dịch võ tướng hoàn tất: A=${trade.playerA}(đưa npc=${petA?.npcId ?: 0}+${offerA.money} vàng) " +
                    "B=${trade.playerB}(đưa npc=${petB?.npcId ?: 0}+${offerB.money} vàng)"
        }
    }

    // ── Xác thực võ tướng ──

    /**
     * Kiểm tra xem võ tướng có thể giao ra không
     *
     * @return Mã lý do hủy, null = Có thể giao dịch
     */
    private fun validatePetGivable(pet: FollowNpcData): Int? {
        // Độ trung thành bằng 0 không thể giao dịch (40304)
        if (pet.loyalty <= 0) return REASON_PET_ZERO_LOYALTY

        // NPC Nhiệm vụ (canGrow=2) không thể chuyển nhượng — TODO: Client không có thông báo tương ứng, ngắt bằng lỗi dữ liệu không khớp, chờ xác thực logic bản PC
        if (pet.canGrow == 2) return REASON_PET_MISMATCH

        // Trên người võ tướng có vật phẩm không thể chuyển nhượng (40429): Kiểm tra từng trang bị restrict bit 2
        for ((_, equip) in pet.equips) {
            if (!equip.isEmpty && !isItemTradeable(equip)) {
                return REASON_PET_BOUND_EQUIP
            }
        }

        return null
    }

    /**
     * Kiểm tra bên nhận có thể nhận võ tướng không
     *
     * @param receiving Võ tướng sẽ nhận (null = đối phương chỉ đưa tiền)
     * @param giving Võ tướng mình giao ra (null = mình không đưa võ tướng), khấu trừ trước rồi mới xác thực dung lượng
     * @return Mã lý do hủy, null = Có thể nhận
     */
    private fun validatePetReceivable(charId: Long, receiving: FollowNpcData?, giving: FollowNpcData?): Int? {
        if (receiving == null) return null

        // Dung lượng tùy tùng (40081): Con mình giao ra sẽ làm trống một vị trí
        val followCount = petSystem.getFollowPets(charId).size - (if (giving != null) 1 else 0)
        if (followCount >= petSystem.getFollowCapacity(charId)) return REASON_PET_FOLLOW_FULL

        val npcId = receiving.npcId

        // Võ tướng trùng lặp: Khách sạn (Bao gồm kho võ tướng, 40083)
        if (petSystem.hasNpcInInn(charId, npcId)) return REASON_PET_DUP_INN

        // Võ tướng trùng lặp: Xe ngựa (40414)
        if (petSystem.getCartPets(charId).values.any { it.npcId == npcId }) return REASON_PET_DUP_CART

        // Võ tướng trùng lặp: Tùy tùng (Khấu trừ con mình giao ra; Client không có thông báo riêng, ngắt bằng lỗi dữ liệu không khớp)
        val followDup = petSystem.getFollowPets(charId).values.any {
            it.npcId == npcId && it.slotIndex != giving?.slotIndex
        }
        if (followDup) return REASON_PET_MISMATCH

        return null
    }

    // ── Xác thực vật phẩm ──

    /**
     * Kiểm tra vật phẩm có thể giao dịch không — Tương ứng UITrade.lua dòng 131-136
     */
    private fun isItemTradeable(thing: ThingData): Boolean {
        val def = itemDefs[thing.itemId] ?: return false

        // restrict & 2 → Không thể giao dịch (Trừ khi specialKind & 1 có thể giao dịch 1 lần)
        if ((def.restrict and 2) == 2 && (thing.specialKind and 1) != 1) return false

        // kind == 53 hoặc restrict2 & 1 → Cần cùng bang hội
        // TODO: Thêm so sánh orgId sau khi hoàn thành hệ thống bang hội
        // Tạm thời cho phép giao dịch mọi vật phẩm không bị restrict

        return true
    }

    /** Lấy characterId thông qua playerId (Hiện tại roleId chính là characterId) */
    private fun getCharacterId(playerId: Long): Long? {
        val session = worldManager.getPlayerById(playerId) ?: return null
        return session.roleId.toLong()
    }

    /** Đếm số ô trống trong túi đồ */
    private fun countEmptyBagSlots(characterId: Long): Int {
        var count = 0
        val bag = inventorySystem.getBag(characterId)
        for (i in 0 until tw.tsm.domain.constants.ItemConstants.INIT_BAG_SLOTS) {
            if (!bag.containsKey(i)) count++
        }
        return count
    }
}