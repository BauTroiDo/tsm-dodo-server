package tw.tsm.app.handlers

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.data.ItemDef
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.PacketHandler
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.gamelogic.systems.InventorySystem
import tw.tsm.gamelogic.systems.MoneySystem
import tw.tsm.gamelogic.systems.NpcShopManager
import tw.tsm.protocol.PacketReader

private val logger = KotlinLogging.logger {}

/**
 * Bộ xử lý mua bán NPC (mainKind = 27)
 *
 * Nguồn: protocal.lua protocolTable[27]
 * C:027-001 mua đồ, C:027-002 bán đồ (cửa hàng sự kiện), C:027-003 bán đồ (tiệm tạp hóa đi theo người)
 */
class NpcTradeHandler(
    private val inventorySystem: InventorySystem,
    private val moneySystem: MoneySystem,
    private val responseSender: ResponseSender,
    private val itemDefs: Map<Int, ItemDef>,
    private val npcShopManager: NpcShopManager
) : PacketHandler {

    override val mainKind: Int = Opcodes.NPC_TRADE  // 27

    override suspend fun handle(session: GameSession, subKind: Int, data: ByteArray) {
        if (session.state != SessionState.IN_GAME) return

        when (subKind) {
            1 -> handleBuy(session, data)
            2 -> handleSell(session, data)
            3 -> handleSell(session, data)  // tiệm tạp hóa đi theo người bán, định dạng giống subKind=2
            else -> logger.warn { "Sub-protocol giao dịch chưa xử lý: subKind=$subKind" }
        }
    }

    /**
     * C:027-001 mua đồ
     *
     * Định dạng: +index(1) +quantity(4)
     * Nguồn: UIShop.lua:98-102
     *
     * index = chỉ số 0-based của món hàng trong danh sách options của UIShop,
     * tương ứng danh sách hàng hóa do Eve Surface tạo với optionMode=1 trong [NpcShopManager].
     */
    private suspend fun handleBuy(session: GameSession, data: ByteArray) {
        if (data.size < 5) return  // index(1) + quantity(4)
        val reader = PacketReader(data)
        val index = reader.readUByte()
        val quantity = reader.readIntLE()

        if (quantity <= 0) {
            responseSender.sendBuyResult(session, 3)
            return
        }

        val charId = session.roleId.toLong()

        // tra cứu hàng hóa — do EveFlowController thiết lập khi Surface optionMode=1
        val itemId = npcShopManager.getShopItem(charId, index)
        if (itemId == null) {
            responseSender.sendBuyResult(session, 6)  // 6=vật phẩm bị đóng (không có dữ liệu cửa hàng)
            logger.debug { "${session.characterName} mua thất bại: không có dữ liệu cửa hàng hoặc chỉ số vượt giới hạn index=$index" }
            return
        }

        val def = itemDefs[itemId]
        if (def == null) {
            responseSender.sendBuyResult(session, 3)  // 3=vật phẩm không tồn tại
            logger.warn { "${session.characterName} mua thất bại: itemDefs[$itemId] không tồn tại" }
            return
        }

        // tính tổng giá (dùng Long để tránh tràn số)
        val totalPrice = def.price.toLong() * quantity
        if (totalPrice > Int.MAX_VALUE || totalPrice <= 0) {
            responseSender.sendBuyResult(session, 3)
            return
        }

        // trừ tiền
        if (!moneySystem.subtractGold(charId, totalPrice.toInt())) {
            responseSender.sendBuyResult(session, 1)  // 1=không đủ tiền
            return
        }

        // thêm vật phẩm vào túi đồ
        val added = inventorySystem.addItemToBag(charId, itemId, quantity)
        if (added == null) {
            // túi đồ đầy → hoàn tiền
            moneySystem.addGold(charId, totalPrice.toInt())
            responseSender.sendBuyResult(session, 2)  // 2=túi đồ đầy
            return
        }

        // thành công — gửi khấu trừ tiền + thêm vật phẩm + kết quả mua
        responseSender.sendSubtractMoney(session, totalPrice.toInt())
        val (slot, thing) = added
        responseSender.sendSetBagItem(session, slot, thing, showMsg = false)
        responseSender.sendBuyResult(session, 0)  // 0=thành công

        logger.info { "${session.characterName} mua thành công: itemId=$itemId x$quantity hết $totalPrice tiền vàng → slot=$slot" }
    }

    /**
     * C:027-002 bán đồ (cửa hàng sự kiện) / C:027-003 bán đồ (tiệm tạp hóa đi theo người)
     *
     * Định dạng: +len(1) +<<bagIndex(1) +quantity(4)>>
     * Nguồn: UINpcStore.lua:343-355
     */
    private suspend fun handleSell(session: GameSession, data: ByteArray) {
        if (data.size < 1) return
        val reader = PacketReader(data)
        val count = reader.readUByte()

        val characterId = session.roleId.toLong()
        val soldIndices = mutableListOf<Int>()

        for (i in 0 until count) {
            if (reader.remaining < 5) break  // bagIndex(1) + quantity(4)
            val bagIndex = reader.readUByte()
            val quantity = reader.readIntLE()
            if (quantity <= 0) continue

            val bagItem = inventorySystem.getBagItem(characterId, bagIndex) ?: continue
            val def = itemDefs[bagItem.itemId] ?: continue

            val sellQty = minOf(quantity, bagItem.quantity)
            val sellPrice = def.sellPrice.toLong() * sellQty  // dùng Long để tránh tràn số

            // tăng tiền
            if (sellPrice > 0 && sellPrice <= Int.MAX_VALUE) {
                if (!moneySystem.addGold(characterId, sellPrice.toInt())) {
                    responseSender.sendMoneyFull(session, 1)
                    break
                }
                responseSender.sendAddMoney(session, sellPrice.toInt())
            }

            // khấu trừ vật phẩm
            inventorySystem.removeFromBag(characterId, bagIndex, sellQty)
            responseSender.sendReduceBagItem(session, bagIndex, sellQty)
            soldIndices.add(bagIndex)
        }

        // trả về kết quả bán: 0=thành công
        val result = if (soldIndices.isNotEmpty()) 0 else 4
        responseSender.sendSellResult(session, result, soldIndices)

        logger.info { "${session.characterName} bán đồ: đã bán ${soldIndices.size} món" }
    }
}
