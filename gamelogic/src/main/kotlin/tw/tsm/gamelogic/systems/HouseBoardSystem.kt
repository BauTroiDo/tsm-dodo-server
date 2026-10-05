package tw.tsm.gamelogic.systems

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tw.tsm.domain.constants.FriendConstants
import tw.tsm.domain.data.SouvenirDef
import tw.tsm.domain.interfaces.repositories.CharacterRecord
import tw.tsm.domain.interfaces.repositories.CharacterRepository
import tw.tsm.domain.interfaces.repositories.FriendRepository
import tw.tsm.domain.interfaces.repositories.GiftRecord
import tw.tsm.domain.interfaces.repositories.HouseBoardRepository
import tw.tsm.domain.interfaces.repositories.HouseProfile
import tw.tsm.domain.util.OleDate
import java.util.concurrent.ConcurrentHashMap

private val houseLogger = KotlinLogging.logger {}

data class HouseBoardFriend(
    val roleId: Long,
    val name: String,
    val friendly: Int,
)

data class HouseBoardView(
    val character: CharacterRecord,
    val profile: HouseProfile,
    val friends: List<HouseBoardFriend>,
)

data class GiftWallEntry(
    val name: String,
    val itemId: Int,
    val quantity: Int,
)

data class GiftHistoryEntry(
    val senderId: Long,
    val senderName: String,
    val itemId: Int,
    val quantity: Int,
    val time: Double,
)

sealed interface SouvenirSendResult {
    data class Success(
        val record: GiftRecord,
        val senderName: String,
        val targetName: String,
        val payment: Int,
        val cost: Int,
        val bagReductions: List<Pair<Int, Int>>,
        val newFriendly: Int?,
        val newCharm: Int,
    ) : SouvenirSendResult

    data class Fail(val resultCode: Int) : SouvenirSendResult
}

class HouseBoardSystem(
    private val repository: HouseBoardRepository,
    private val characterRepository: CharacterRepository,
    private val friendRepository: FriendRepository,
    private val inventorySystem: InventorySystem,
    private val moneySystem: MoneySystem,
    private val pointSystem: PointSystem,
    private val roleCountSystem: RoleCountSystem,
    private val souvenirDefs: Map<Int, SouvenirDef>,
) {
    private val locks = ConcurrentHashMap<Long, Mutex>()

    suspend fun view(characterId: Long): HouseBoardView? {
        val character = characterRepository.findById(characterId) ?: return null
        val profile = repository.getProfile(characterId)
        val friends = friendRepository.findFriends(characterId)
            .mapNotNull { record ->
                val friend = characterRepository.findById(record.friendId) ?: return@mapNotNull null
                HouseBoardFriend(record.friendId, friend.name, record.friendly)
            }
        return HouseBoardView(character, profile, friends)
    }

    suspend fun giftWall(characterId: Long): List<GiftWallEntry> {
        val all = repository.allGifts(characterId)
        if (all.isEmpty()) return emptyList()
        val result = ArrayList<GiftWallEntry>()
        for ((itemId, records) in all.groupBy { it.itemId }) {
            val latest = records.first()
            val senderName = characterRepository.findById(latest.senderId)?.name.orEmpty()
            val total = records.sumOf { it.quantity.toLong() }
                .coerceAtMost(Int.MAX_VALUE.toLong())
                .toInt()
            result += GiftWallEntry(senderName, itemId, total)
        }
        return result.sortedWith(
            compareBy<GiftWallEntry> { souvenirDefs[it.itemId]?.quality ?: Int.MAX_VALUE }
                .thenByDescending { souvenirDefs[it.itemId]?.price ?: 0 }
        )
    }

    suspend fun history(characterId: Long): List<GiftHistoryEntry> =
        repository.recentGifts(characterId, 100).map { record ->
            GiftHistoryEntry(
                senderId = record.senderId,
                senderName = characterRepository.findById(record.senderId)?.name.orEmpty(),
                itemId = record.itemId,
                quantity = record.quantity,
                time = record.time,
            )
        }

    suspend fun updateProfile(
        characterId: Long,
        settingType: Int,
        text: String? = null,
        byteValue: Int? = null,
        month: Int? = null,
        day: Int? = null,
    ): Boolean {
        val current = repository.getProfile(characterId)
        val next = when (settingType) {
            1 -> current.copy(signature = text?.take(MAX_TEXT_BYTES) ?: return false)
            2 -> current.copy(impression = text?.take(MAX_TEXT_BYTES) ?: return false)
            3 -> current.copy(favorite = text?.take(MAX_TEXT_BYTES) ?: return false)
            4 -> current.copy(sign = byteValue?.takeIf { it in 0..255 } ?: return false)
            5 -> {
                val m = month ?: return false
                val d = day ?: return false
                if (m !in 1..12 || d !in 1..31) return false
                current.copy(birthMonth = m, birthDay = d)
            }
            6 -> current.copy(area = text?.take(MAX_TEXT_BYTES) ?: return false)
            7 -> current.copy(hobby = text?.take(MAX_TEXT_BYTES) ?: return false)
            else -> return false
        }
        repository.saveProfile(next)
        return true
    }

    suspend fun sendGift(
        senderId: Long,
        targetId: Long,
        itemId: Int,
        quantity: Int,
        sourceKind: Int,
    ): SouvenirSendResult = lockFor(senderId).withLock {
        if (senderId == targetId || quantity !in 1..99 || sourceKind !in SOURCE_SHOP..SOURCE_BAG) {
            return@withLock SouvenirSendResult.Fail(RESULT_NO_DATA)
        }

        val sender = characterRepository.findById(senderId)
            ?: return@withLock SouvenirSendResult.Fail(RESULT_NO_DATA)
        val target = characterRepository.findById(targetId)
            ?: return@withLock SouvenirSendResult.Fail(RESULT_NO_DATA)
        if (sender.turn < 1) return@withLock SouvenirSendResult.Fail(RESULT_NOT_FIRST_TURN)

        val def = souvenirDefs[itemId]
            ?: return@withLock SouvenirSendResult.Fail(RESULT_NO_DATA)

        val totalCostLong = def.price.toLong() * quantity.toLong()
        if (totalCostLong < 0 || totalCostLong > Int.MAX_VALUE) {
            return@withLock SouvenirSendResult.Fail(RESULT_NO_DATA)
        }
        val totalCost = totalCostLong.toInt()

        val oldFeedback = roleCountSystem.getValue(senderId, RoleCountSystem.SOUVENIR_FEEDBACK_EXP)
        val oldItemCount = roleCountSystem.getValue(senderId, def.roleCount)
        val oldDailySpend = roleCountSystem.getValue(senderId, RoleCountSystem.BUY_GIFT_MAX_MONEY)
        val dailySpendMax = roleCountSystem.getMaxValue(senderId, RoleCountSystem.BUY_GIFT_MAX_MONEY)

        if (
            sourceKind == SOURCE_SHOP &&
            def.payment == PAYMENT_MONEY &&
            dailySpendMax > 0 &&
            oldDailySpend.toLong() + totalCost >= dailySpendMax.toLong()
        ) {
            return@withLock SouvenirSendResult.Fail(RESULT_DAILY_LIMIT)
        }

        val friendRecord = friendRepository.findFriends(senderId).firstOrNull { it.friendId == targetId }
        val oldFriendly = friendRecord?.friendly
        val newFriendly = oldFriendly?.let {
            (it.toLong() + def.addFriendly.toLong() * quantity)
                .coerceAtMost(FriendConstants.MAX_FRIENDLY.toLong())
                .toInt()
        }

        val bagReductions = ArrayList<Pair<Int, Int>>()
        var debitedPayment = 0
        var removedBagQuantity = 0

        try {
            when (sourceKind) {
                SOURCE_SHOP -> {
                    when (def.payment) {
                        PAYMENT_GOLD -> {
                            if (!pointSystem.subtractRealPoint(senderId, totalCost)) {
                                return@withLock SouvenirSendResult.Fail(RESULT_NO_DATA)
                            }
                            pointSystem.savePoints(senderId)
                            debitedPayment = PAYMENT_GOLD
                        }
                        PAYMENT_MONEY -> {
                            if (!moneySystem.subtractGold(senderId, totalCost)) {
                                return@withLock SouvenirSendResult.Fail(RESULT_NO_DATA)
                            }
                            moneySystem.saveMoney(senderId)
                            debitedPayment = PAYMENT_MONEY
                        }
                        else -> return@withLock SouvenirSendResult.Fail(RESULT_NO_DATA)
                    }
                }

                SOURCE_BAG -> {
                    val plan = planBagRemoval(senderId, itemId, quantity)
                        ?: return@withLock SouvenirSendResult.Fail(RESULT_NO_DATA)
                    for ((slot, count) in plan) {
                        if (!inventorySystem.removeFromBag(senderId, slot, count)) {
                            throw IllegalStateException("Souvenir bag removal failed slot=$slot count=$count")
                        }
                        bagReductions += slot to count
                        removedBagQuantity += count
                    }
                }
            }

            if (newFriendly != null && newFriendly != oldFriendly) {
                friendRepository.updateFriendly(senderId, targetId, newFriendly)
                friendRepository.updateFriendly(targetId, senderId, newFriendly)
            }

            roleCountSystem.setValue(
                senderId,
                RoleCountSystem.SOUVENIR_FEEDBACK_EXP,
                safeAdd(oldFeedback, def.addExp.toLong() * quantity),
                roleCountSystem.getMaxValue(senderId, RoleCountSystem.SOUVENIR_FEEDBACK_EXP),
            )
            roleCountSystem.setValue(
                senderId,
                def.roleCount,
                safeAdd(oldItemCount, quantity.toLong()),
                roleCountSystem.getMaxValue(senderId, def.roleCount),
            )
            if (sourceKind == SOURCE_SHOP && def.payment == PAYMENT_MONEY) {
                roleCountSystem.setValue(
                    senderId,
                    RoleCountSystem.BUY_GIFT_MAX_MONEY,
                    safeAdd(oldDailySpend, totalCost.toLong()),
                    dailySpendMax,
                )
            }
            roleCountSystem.saveCounts(senderId)

            val giftRecord = GiftRecord(
                senderId = senderId,
                targetId = targetId,
                itemId = itemId,
                quantity = quantity,
                time = OleDate.nowLocal(),
            )
            val profile = repository.applyGift(
                giftRecord,
                safeMultiply(def.addCharm, quantity),
            )

            return@withLock SouvenirSendResult.Success(
                record = giftRecord,
                senderName = sender.name,
                targetName = target.name,
                payment = if (sourceKind == SOURCE_SHOP) def.payment else 0,
                cost = if (sourceKind == SOURCE_SHOP) totalCost else 0,
                bagReductions = bagReductions,
                newFriendly = newFriendly,
                newCharm = profile.charm,
            )
        } catch (t: Throwable) {
            houseLogger.error(t) {
                "HouseBoard send gift rollback: senderId=$senderId targetId=$targetId itemId=$itemId qty=$quantity"
            }

            if (newFriendly != null && oldFriendly != null && newFriendly != oldFriendly) {
                runCatching {
                    friendRepository.updateFriendly(senderId, targetId, oldFriendly)
                    friendRepository.updateFriendly(targetId, senderId, oldFriendly)
                }
            }
            roleCountSystem.setValue(
                senderId,
                RoleCountSystem.SOUVENIR_FEEDBACK_EXP,
                oldFeedback,
                roleCountSystem.getMaxValue(senderId, RoleCountSystem.SOUVENIR_FEEDBACK_EXP),
            )
            roleCountSystem.setValue(
                senderId,
                def.roleCount,
                oldItemCount,
                roleCountSystem.getMaxValue(senderId, def.roleCount),
            )
            roleCountSystem.setValue(
                senderId,
                RoleCountSystem.BUY_GIFT_MAX_MONEY,
                oldDailySpend,
                dailySpendMax,
            )
            runCatching { roleCountSystem.saveCounts(senderId) }

            when (debitedPayment) {
                PAYMENT_GOLD -> if (pointSystem.addRealPoint(senderId, totalCost)) {
                    runCatching { pointSystem.savePoints(senderId) }
                }
                PAYMENT_MONEY -> if (moneySystem.addGold(senderId, totalCost)) {
                    runCatching { moneySystem.saveMoney(senderId) }
                }
            }
            if (removedBagQuantity > 0) {
                runCatching { inventorySystem.addItemsToBag(senderId, itemId, removedBagQuantity) }
            }

            return@withLock SouvenirSendResult.Fail(RESULT_NO_DATA)
        }
    }

    private fun planBagRemoval(
        characterId: Long,
        itemId: Int,
        quantity: Int,
    ): List<Pair<Int, Int>>? {
        var remaining = quantity
        val result = ArrayList<Pair<Int, Int>>()
        for ((slot, thing) in inventorySystem.getBag(characterId).toSortedMap()) {
            if (thing.itemId != itemId || remaining <= 0) continue
            val take = minOf(thing.quantity, remaining)
            if (take > 0) {
                result += slot to take
                remaining -= take
            }
        }
        return result.takeIf { remaining == 0 }
    }

    private fun safeAdd(base: Int, delta: Long): Int =
        (base.toLong() + delta).coerceIn(0, Int.MAX_VALUE.toLong()).toInt()

    private fun safeMultiply(a: Int, b: Int): Int =
        (a.toLong() * b.toLong()).coerceIn(0, Int.MAX_VALUE.toLong()).toInt()

    private fun lockFor(characterId: Long): Mutex =
        locks.computeIfAbsent(characterId) { Mutex() }

    companion object {
        const val SOURCE_SHOP = 1
        const val SOURCE_BAG = 2

        const val PAYMENT_GOLD = 1
        const val PAYMENT_MONEY = 3

        const val RESULT_SUCCESS = 1
        const val RESULT_NO_DATA = 8
        const val RESULT_DAILY_LIMIT = 9
        const val RESULT_NOT_FIRST_TURN = 14

        private const val MAX_TEXT_BYTES = 255
    }
}
