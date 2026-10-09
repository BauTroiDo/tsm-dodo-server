package tw.tsm.app.handlers

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.data.GoodsSaleDef
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.PacketHandler
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.gamelogic.systems.DoroSystem
import tw.tsm.gamelogic.systems.InventorySystem
import tw.tsm.gamelogic.systems.MoneySystem
import tw.tsm.gamelogic.systems.MallQuotaSystem
import tw.tsm.gamelogic.systems.PlayerCurrencySystem
import tw.tsm.gamelogic.systems.PointSystem
import tw.tsm.protocol.PacketReader

private val mallLogger = KotlinLogging.logger {}

class MallHandler(
    private val doroSystem: DoroSystem,
    private val inventorySystem: InventorySystem,
    private val pointSystem: PointSystem,
    private val moneySystem: MoneySystem,
    private val currencySystem: PlayerCurrencySystem,
    private val responseSender: ResponseSender,
    private val goodsSaleDefs: List<GoodsSaleDef> = emptyList(),
    private val quotaSystem: MallQuotaSystem? = null,
) : PacketHandler {
    override val mainKind: Int = Opcodes.MALL
    private val charLocks = ConcurrentHashMap<Long, Mutex>()

    override suspend fun handle(session: GameSession, subKind: Int, data: ByteArray) {
        if (session.state != SessionState.IN_GAME) return
        when (subKind) {
            1 -> charLocks.computeIfAbsent(session.roleId.toLong()) { Mutex() }
                .withLock { handlePurchase(session, data) }
            else -> mallLogger.warn {
                "Mall chưa hỗ trợ subKind=" + subKind + " (" + data.size + " bytes)"
            }
        }
    }

    private suspend fun handlePurchase(session: GameSession, data: ByteArray) {
        if (data.size != REQUEST_SIZE) {
            mallLogger.warn { "C66-1 sai size=" + data.size + ", expected=" + REQUEST_SIZE }
            return
        }

        val reader = PacketReader(data)
        val storeKind = reader.readUByte()
        val mainPage = reader.readUByte()
        val showKind = reader.readUByte()
        val pageIndex = reader.readUByte()
        val itemId = reader.readUShortLE()
        val clientPrice = reader.readUShortLE()
        val packageCount = reader.readUShortLE()
        val free = reader.readUByte()

        // UIShop uses storeKind=1 for normal Tiệm items, including Elf gift bags.
        // Prior implementation only handled Doro storeKind=5, so every ordinary
        // purchase failed and the PC client displayed a misleading "not enough silver".
        if (storeKind == STORE_STANDARD) {
            purchaseStandard(
                session, mainPage, showKind, pageIndex, itemId,
                clientPrice, packageCount, free
            )
            return
        }

        if (storeKind != DoroSystem.STORE_DORO || packageCount != 1 || free != 0) {
            mallLogger.warn {
                "Từ chối Mall C66-1 ngoài scope Doro: store=" + storeKind +
                    " item=" + itemId + " count=" + packageCount + " free=" + free
            }
            responseSender.sendMallPurchaseResult(session, itemId, RESULT_FAIL)
            return
        }

        val packageDef = doroSystem.findPackage(
            storeKind = storeKind,
            mainPageIndex = mainPage,
            showKind = showKind,
            pageItemIndex = pageIndex,
            itemId = itemId,
            saleCredit = clientPrice,
        )
        if (packageDef == null) {
            mallLogger.warn {
                "Doro request không khớp GS_C: " + session.characterName +
                    " store=" + storeKind + " main=" + mainPage + " show=" + showKind +
                    " index=" + pageIndex + " item=" + itemId + " price=" + clientPrice
            }
            responseSender.sendMallPurchaseResult(session, itemId, RESULT_FAIL)
            return
        }

        val rewards = doroSystem.draw(packageDef)
        if (rewards == null || rewards.size != packageDef.value) {
            mallLogger.error {
                "Doro pool không hợp lệ: package=" + packageDef.itemId +
                    " draws=" + packageDef.value +
                    " poolWeight=" + doroSystem.poolWeight(packageDef.itemId)
            }
            responseSender.sendMallPurchaseResult(session, itemId, RESULT_FAIL)
            return
        }

        val charId = session.roleId.toLong()
        val rewardPairs = rewards.map { it.itemId to it.count }
        if (!inventorySystem.canAddRewardsToBag(charId, rewardPairs)) {
            mallLogger.info {
                session.characterName + " Doro thất bại: túi không đủ chỗ package=" + itemId
            }
            responseSender.sendMallPurchaseResult(session, itemId, RESULT_FAIL)
            return
        }

        val doroQuota = if (packageDef.saleKind != 0) {
            quotaSystem?.reserve(charId, packageDef, 1) ?: run {
                responseSender.sendMallPurchaseResult(session, itemId, RESULT_FAIL)
                return
            }
        } else null

        if (!deductPayment(charId, packageDef)) {
            if (doroQuota != null) quotaSystem?.cancel(doroQuota)
            mallLogger.info {
                session.characterName + " Doro không đủ currency=" + packageDef.currencyKind +
                    " cost=" + packageDef.saleCredit + " package=" + itemId
            }
            responseSender.sendMallPurchaseResult(session, itemId, RESULT_FAIL)
            return
        }

        val changedSlots = try {
            inventorySystem.addRewardsToBagExact(charId, rewardPairs)
        } catch (e: Exception) {
            mallLogger.error(e) {
                "Doro add reward exception: charId=" + charId + " package=" + itemId
            }
            null
        }

        if (changedSlots == null) {
            refundPayment(charId, packageDef)
            if (doroQuota != null) quotaSystem?.cancel(doroQuota)
            responseSender.sendMallPurchaseResult(session, itemId, RESULT_FAIL)
            return
        }

        for ((slot, thing) in changedSlots) {
            responseSender.sendSetBagItem(session, slot, thing, showMsg = false)
        }
        syncCurrency(session, packageDef)
        if (doroQuota != null) {
            try {
                quotaSystem?.complete(doroQuota)
                responseSender.sendRoleCountUpdate(
                    session, mapOf(doroQuota.flag to (doroQuota.nextCount to 0))
                )
            } catch (e: Exception) {
                mallLogger.error(e) { "Doro quota UI sync failed after completed purchase" }
            }
        }

        // Ordering is required by DVT client:
        // S23-150 opens UIDoro while Mall.IsMallDoro()==true.
        // S66-3 success clears that state, so it must come second.
        responseSender.sendDoroResult(session, rewards)
        responseSender.sendMallPurchaseResult(session, itemId, RESULT_SUCCESS)

        mallLogger.info {
            session.characterName + " Doro OK: package=" + itemId +
                " currency=" + packageDef.currencyKind +
                " cost=" + packageDef.saleCredit +
                " draws=" + rewards.size +
                " rewards=" + rewards.joinToString { it.itemId.toString() + "x" + it.count }
        }
    }

    /**
     * C66-001 ordinary PC DVT Tiệm purchase.
     *
     * Always validate the server's exact GS_C row, price and page index. Only
     * saleKind=0 without limited-use flags is accepted here: limited-time,
     * daily-limit and event goods require separate counters/validation.
     */
    private suspend fun purchaseStandard(
        session: GameSession,
        mainPage: Int,
        showKind: Int,
        pageIndex: Int,
        itemId: Int,
        clientPrice: Int,
        packageCount: Int,
        free: Int,
    ) {
        fun reject() = responseSender.sendMallPurchaseResult(session, itemId, RESULT_FAIL)
        if (free != 0 || packageCount !in 1..MAX_STANDARD_PURCHASE_COUNT) {
            reject()
            return
        }
        val offer = goodsSaleDefs.firstOrNull { row ->
            row.storeKind == STORE_STANDARD &&
                row.mainKind == mainPage &&
                row.subKind == showKind &&
                row.pageItemIndex == pageIndex &&
                row.itemId == itemId &&
                row.saleCredit == clientPrice &&
                row.open &&
                (row.bitFunction and NORMAL_CLIENT_BIT) == NORMAL_CLIENT_BIT &&
                row.saleKind in 0..4 &&
                (if (row.saleKind == 0) row.dayCount == 0
                 else row.flag > 0 && row.dayCount > 0) &&
                row.buyLimitFlag == 0 &&
                row.specialEvent == 0L &&
                row.timeKind == 0 &&
                row.value > 0
        }
        if (offer == null) {
            mallLogger.warn {
                "Reject normal Tiệm offer: store=1 page=$mainPage kind=$showKind " +
                    "index=$pageIndex item=$itemId clientPrice=$clientPrice"
            }
            reject()
            return
        }
        // Multiply in Long to avoid malicious C66 quantity/price integer overflow.
        val costLong = offer.saleCredit.toLong() * packageCount
        val amountLong = offer.value.toLong() * packageCount
        if (costLong !in 1..Int.MAX_VALUE.toLong() ||
            amountLong !in 1..MAX_STANDARD_TOTAL_ITEMS.toLong()
        ) {
            reject()
            return
        }
        val cost = costLong.toInt()
        val amount = amountLong.toInt()
        val characterId = session.roleId.toLong()
        val rewards = listOf(itemId to amount)
        if (!inventorySystem.canAddRewardsToBag(characterId, rewards)) {
            mallLogger.info { "Normal Tiệm bag full: charId=$characterId item=$itemId" }
            reject()
            return
        }
        val charge = offer.copy(saleCredit = cost)
        val reserved = if (offer.saleKind != 0) {
            quotaSystem?.reserve(characterId, offer, packageCount) ?: run {
                reject()
                return
            }
        } else null

        if (!deductPayment(characterId, charge)) {
            if (reserved != null) quotaSystem?.cancel(reserved)
            mallLogger.info {
                "Normal Tiệm insufficient balance: charId=$characterId " +
                    "kind=${offer.currencyKind} cost=$cost item=$itemId"
            }
            reject()
            return
        }
        val changedSlots = try {
            inventorySystem.addRewardsToBagExact(characterId, rewards)
        } catch (e: Exception) {
            mallLogger.error(e) { "Normal Tiệm item grant failed: charId=$characterId item=$itemId" }
            null
        }
        if (changedSlots == null) {
            refundPayment(characterId, charge)
            if (reserved != null) quotaSystem?.cancel(reserved)
            reject()
            return
        }
        changedSlots.forEach { (slot, thing) ->
            responseSender.sendSetBagItem(session, slot, thing, showMsg = false)
        }
        syncCurrency(session, charge)
        if (reserved != null) {
            try {
                quotaSystem?.complete(reserved)
                responseSender.sendRoleCountUpdate(
                    session, mapOf(reserved.flag to (reserved.nextCount to 0))
                )
            } catch (e: Exception) {
                mallLogger.error(e) { "Mall quota UI sync failed after completed purchase" }
            }
        }
        responseSender.sendMallPurchaseResult(session, itemId, RESULT_SUCCESS)
        mallLogger.info {
            "Normal Tiệm purchase succeeded: charId=$characterId item=$itemId " +
                "amount=$amount currency=${offer.currencyKind} cost=$cost"
        }
    }

    private suspend fun deductPayment(characterId: Long, def: GoodsSaleDef): Boolean =
        when (def.currencyKind) {
            CURRENCY_POINT -> {
                if (!pointSystem.subtractRealPoint(characterId, def.saleCredit)) {
                    false
                } else {
                    try {
                        pointSystem.savePoints(characterId)
                        true
                    } catch (e: Exception) {
                        pointSystem.addRealPoint(characterId, def.saleCredit)
                        mallLogger.error(e) {
                            "Persist Point deduction failed; rolled back charId=" + characterId
                        }
                        false
                    }
                }
            }

            CURRENCY_GAME_GOLD -> {
                if (!moneySystem.subtractGold(characterId, def.saleCredit)) {
                    false
                } else {
                    try {
                        moneySystem.saveMoney(characterId)
                        true
                    } catch (e: Exception) {
                        moneySystem.addGold(characterId, def.saleCredit)
                        mallLogger.error(e) {
                            "Persist gold deduction failed; rolled back charId=" + characterId
                        }
                        false
                    }
                }
            }

            in PlayerCurrencySystem.MIN_KIND..PlayerCurrencySystem.MAX_KIND ->
                try {
                    currencySystem.subtract(characterId, def.currencyKind, def.saleCredit)
                } catch (e: Exception) {
                    mallLogger.error(e) {
                        "Persist special currency deduction failed: charId=" + characterId +
                            " kind=" + def.currencyKind
                    }
                    false
                }

            else -> false
        }

    private suspend fun refundPayment(characterId: Long, def: GoodsSaleDef) {
        when (def.currencyKind) {
            CURRENCY_POINT -> {
                if (pointSystem.addRealPoint(characterId, def.saleCredit)) {
                    try {
                        pointSystem.savePoints(characterId)
                    } catch (e: Exception) {
                        mallLogger.error(e) { "Doro Point refund persist failed: charId=" + characterId }
                    }
                }
            }

            CURRENCY_GAME_GOLD -> {
                if (moneySystem.addGold(characterId, def.saleCredit)) {
                    try {
                        moneySystem.saveMoney(characterId)
                    } catch (e: Exception) {
                        mallLogger.error(e) { "Doro gold refund persist failed: charId=" + characterId }
                    }
                }
            }

            in PlayerCurrencySystem.MIN_KIND..PlayerCurrencySystem.MAX_KIND -> {
                try {
                    currencySystem.add(characterId, def.currencyKind, def.saleCredit)
                } catch (e: Exception) {
                    mallLogger.error(e) {
                        "Doro currency refund failed: charId=" + characterId + " kind=" + def.currencyKind
                    }
                }
            }
        }
    }

    private fun syncCurrency(session: GameSession, def: GoodsSaleDef) {
        val charId = session.roleId.toLong()
        when (def.currencyKind) {
            CURRENCY_POINT -> {
                val (real, virtual) = pointSystem.getPoints(charId)
                responseSender.sendPointBalance(session, real, virtual)
            }

            CURRENCY_GAME_GOLD ->
                responseSender.sendSubtractMoney(session, def.saleCredit)

            in PlayerCurrencySystem.MIN_KIND..PlayerCurrencySystem.MAX_KIND ->
                responseSender.sendPlayerCurrency(
                    session,
                    def.currencyKind,
                    currencySystem.get(charId, def.currencyKind)
                )
        }
    }

    companion object {
        private const val STORE_STANDARD = 1
        private const val NORMAL_CLIENT_BIT = 2
        private const val MAX_STANDARD_PURCHASE_COUNT = 99
        private const val MAX_STANDARD_TOTAL_ITEMS = 999
        private const val REQUEST_SIZE = 11
        private const val RESULT_SUCCESS = 1
        private const val RESULT_FAIL = 2
        private const val CURRENCY_POINT = 1
        private const val CURRENCY_GAME_GOLD = 3
    }
}
