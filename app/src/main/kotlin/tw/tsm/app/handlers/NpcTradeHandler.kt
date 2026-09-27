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
 * NPC 買賣處理器（mainKind = 27）
 *
 * 來源：protocal.lua protocolTable[27]
 * C:027-001 買東西, C:027-002 賣東西（事件商店）, C:027-003 賣東西（隨身雜貨商）
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
            3 -> handleSell(session, data)  // 隨身雜貨商賣出，格式與 subKind=2 相同
            else -> logger.warn { "未處理的交易子協定：subKind=$subKind" }
        }
    }

    /**
     * C:027-001 買東西
     *
     * 格式：+index(1) +quantity(4)
     * 來源：UIShop.lua:98-102
     *
     * index = 商品在 UIShop options 列表中的 0-based 索引，
     * 對應 [NpcShopManager] 中由 Eve Surface optionMode=1 建立的商品清單。
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

        // 查詢商品 — 由 EveFlowController 在 Surface optionMode=1 時設定
        val itemId = npcShopManager.getShopItem(charId, index)
        if (itemId == null) {
            responseSender.sendBuyResult(session, 6)  // 6=物品關閉（無商店資料）
            logger.debug { "${session.characterName} 購買失敗：無商店資料或索引越界 index=$index" }
            return
        }

        val def = itemDefs[itemId]
        if (def == null) {
            responseSender.sendBuyResult(session, 3)  // 3=物品不存在
            logger.warn { "${session.characterName} 購買失敗：itemDefs[$itemId] 不存在" }
            return
        }

        // 計算總價（用 Long 防溢位）
        val totalPrice = def.price.toLong() * quantity
        if (totalPrice > Int.MAX_VALUE || totalPrice <= 0) {
            responseSender.sendBuyResult(session, 3)
            return
        }

        // 扣錢
        if (!moneySystem.subtractGold(charId, totalPrice.toInt())) {
            responseSender.sendBuyResult(session, 1)  // 1=錢不夠
            return
        }

        // 加物品到背包
        val added = inventorySystem.addItemToBag(charId, itemId, quantity)
        if (added == null) {
            // 背包滿 → 退錢
            moneySystem.addGold(charId, totalPrice.toInt())
            responseSender.sendBuyResult(session, 2)  // 2=背包滿
            return
        }

        // 成功 — 發送金錢扣除 + 物品新增 + 購買結果
        responseSender.sendSubtractMoney(session, totalPrice.toInt())
        val (slot, thing) = added
        responseSender.sendSetBagItem(session, slot, thing, showMsg = false)
        responseSender.sendBuyResult(session, 0)  // 0=成功

        logger.info { "${session.characterName} 購買成功：itemId=$itemId x$quantity 花費 $totalPrice 金 → slot=$slot" }
    }

    /**
     * C:027-002 賣東西（事件商店）/ C:027-003 賣東西（隨身雜貨商）
     *
     * 格式：+len(1) +<<bagIndex(1) +quantity(4)>>
     * 來源：UINpcStore.lua:343-355
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
            val sellPrice = def.sellPrice.toLong() * sellQty  // 用 Long 防溢位

            // 增加金錢
            if (sellPrice > 0 && sellPrice <= Int.MAX_VALUE) {
                if (!moneySystem.addGold(characterId, sellPrice.toInt())) {
                    responseSender.sendMoneyFull(session, 1)
                    break
                }
                responseSender.sendAddMoney(session, sellPrice.toInt())
            }

            // 扣除物品
            inventorySystem.removeFromBag(characterId, bagIndex, sellQty)
            responseSender.sendReduceBagItem(session, bagIndex, sellQty)
            soldIndices.add(bagIndex)
        }

        // 回傳賣出結果：0=成功
        val result = if (soldIndices.isNotEmpty()) 0 else 4
        responseSender.sendSellResult(session, result, soldIndices)

        logger.info { "${session.characterName} 賣東西：賣出${soldIndices.size}件" }
    }
}
