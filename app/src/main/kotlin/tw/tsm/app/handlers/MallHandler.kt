package tw.tsm.app.handlers

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.data.GoodsSaleDef
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.PacketHandler
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.gamelogic.systems.DoroSystem
import tw.tsm.gamelogic.systems.InventorySystem
import tw.tsm.gamelogic.systems.MoneySystem
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
) : PacketHandler {
    override val mainKind: Int = Opcodes.MALL

    override suspend fun handle(session: GameSession, subKind: Int, data: ByteArray) {
        if (session.state != SessionState.IN_GAME) return
        when (subKind) {
            1 -> handlePurchase(session, data)
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

        if (!deductPayment(charId, packageDef)) {
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
            responseSender.sendMallPurchaseResult(session, itemId, RESULT_FAIL)
            return
        }

        for ((slot, thing) in changedSlots) {
            responseSender.sendSetBagItem(session, slot, thing, showMsg = false)
        }
        syncCurrency(session, packageDef)

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
        private const val REQUEST_SIZE = 11
        private const val RESULT_SUCCESS = 1
        private const val RESULT_FAIL = 2
        private const val CURRENCY_POINT = 1
        private const val CURRENCY_GAME_GOLD = 3
    }
}
