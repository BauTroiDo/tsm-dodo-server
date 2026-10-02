package tw.tsm.gamelogic.systems

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tw.tsm.domain.data.FurnaceDismantleEntry
import tw.tsm.domain.data.FurnaceSelectDef
import tw.tsm.domain.data.FurnaceShopCategory
import tw.tsm.domain.data.FurnaceShopItem
import tw.tsm.domain.data.FurnaceShopSnapshot
import tw.tsm.domain.data.FurnaceSlotDef
import tw.tsm.domain.data.ItemDef
import tw.tsm.domain.valueobjects.ThingData
import java.time.LocalDate
import java.util.Random
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.floor

private val logger = KotlinLogging.logger {}

sealed class FurnaceBuyResult {
    data class Success(
        val itemId: Int,
        val flagId: Int,
        val roleCountIndex: Int,
        val roleCountValue: Int,
        val roleCountMax: Int,
    ) : FurnaceBuyResult()

    data class Fail(val code: Int) : FurnaceBuyResult()
}

sealed class FurnaceDismantleResult {
    data class Success(
        val entries: List<FurnaceDismantleEntry>,
        val roleCountIndex: Int?,
        val roleCountValue: Int?,
        val roleCountMax: Int?,
    ) : FurnaceDismantleResult()

    data class Fail(val furnaceKind: Int = 0, val itemId: Int = 0) : FurnaceDismantleResult()
}

/**
 * DVT UIFurnace / protocol 89.
 *
 * Distribution/shop composition comes from IOS_FurnaceSelect_C.dat + IOS_FurnaceSlot_C.dat.
 * Exact original RNG seed/crit chance is server-side evidence that is not present in Lua/data.
 * Offline policy:
 * - deterministic seed = character + category + persisted UISell refresh count;
 * - isCrit=false (do not invent a discount probability).
 *
 * This preserves the important DVT invariant: opening/relogging does not reroll for free,
 * while a paid category refresh changes only that category.
 */
class FurnaceSystem(
    private val slotDefs: List<FurnaceSlotDef>,
    private val selectDefs: List<FurnaceSelectDef>,
    private val itemDefs: Map<Int, ItemDef>,
    private val inventorySystem: InventorySystem,
    private val roleCountSystem: RoleCountSystem,
    private val bitFlagSystem: BitFlagSystem,
    private val uiSellSystem: UISellSystem,
    private val today: () -> LocalDate = { LocalDate.now() },
) {
    private val locks = ConcurrentHashMap<Long, Mutex>()

    suspend fun snapshot(characterId: Long): FurnaceShopSnapshot {
        val categories = (1..6).map { category ->
            val version = uiSellSystem.getPurchaseCount(characterId, refreshSellId(category))
            generateCategory(characterId, category, version)
        }
        return FurnaceShopSnapshot(
            baseRate = BASE_RATE,
            activeRate = ACTIVE_RATE,
            categories = categories,
        )
    }

    suspend fun buy(
        characterId: Long,
        category: Int,
        slot: Int,
        requestedItemId: Int,
    ): FurnaceBuyResult = lockFor(characterId).withLock {
        if (category !in 1..6) return@withLock FurnaceBuyResult.Fail(BUY_ERR_KIND)
        if (slot !in 1..SHOP_SIZE) return@withLock FurnaceBuyResult.Fail(BUY_ERR_SLOT)

        val version = uiSellSystem.getPurchaseCount(characterId, refreshSellId(category))
        val shop = generateCategory(characterId, category, version)
        val selected = shop.items.getOrNull(slot - 1)
            ?: return@withLock FurnaceBuyResult.Fail(BUY_ERR_SLOT)
        if (selected.itemId != requestedItemId) {
            return@withLock FurnaceBuyResult.Fail(BUY_ERR_ITEM)
        }

        val flagId = purchaseFlag(category, slot)
        if (flagId <= 0) return@withLock FurnaceBuyResult.Fail(BUY_ERR_SLOT)
        if (flagId in bitFlagSystem.getSetFlagIds(characterId)) {
            return@withLock FurnaceBuyResult.Fail(BUY_ERR_PURCHASED)
        }

        val def = itemDefs[selected.itemId] ?: return@withLock FurnaceBuyResult.Fail(BUY_ERR_ITEM)
        val roleIndex = debrisRoleCount(category)
        val storeRate = if (category in GOLD_CATEGORIES) GOLD_STORE_RATE else 1
        val critRateNumerator = if (shop.isCrit) 1 else 2
        val costLong = def.furnaceCount * selected.quantity.toLong() * storeRate.toLong() *
            critRateNumerator.toLong() / 2L
        if (costLong <= 0 || costLong > Int.MAX_VALUE) {
            return@withLock FurnaceBuyResult.Fail(BUY_ERR_ITEM)
        }
        val cost = costLong.toInt()
        val oldDebris = roleCountSystem.getValue(characterId, roleIndex)
        val maxDebris = roleCountSystem.getMaxValue(characterId, roleIndex)
        if (oldDebris < cost) return@withLock FurnaceBuyResult.Fail(BUY_ERR_DEBRIS)

        if (!inventorySystem.canAddItemToBag(characterId, selected.itemId, selected.quantity)) {
            return@withLock FurnaceBuyResult.Fail(BUY_ERR_BAG_FULL)
        }

        val beforeBag = inventorySystem.getBag(characterId).toMap()
        roleCountSystem.setValue(characterId, roleIndex, oldDebris - cost, maxDebris)
        try {
            roleCountSystem.saveCounts(characterId)
            val added = inventorySystem.addItemsToBagExact(
                characterId,
                selected.itemId,
                selected.quantity,
            )
            if (added == null) {
                rollbackRoleCount(characterId, roleIndex, oldDebris, maxDebris)
                return@withLock FurnaceBuyResult.Fail(BUY_ERR_BAG_FULL)
            }

            try {
                bitFlagSystem.setFlag(characterId, flagId)
            } catch (e: Exception) {
                restoreBag(characterId, beforeBag)
                rollbackRoleCount(characterId, roleIndex, oldDebris, maxDebris)
                logger.error(e) { "Lò rollback mua thất bại khi set bitFlag: charId=${characterId} flag=${flagId}" }
                return@withLock FurnaceBuyResult.Fail(BUY_ERR_ITEM)
            }
        } catch (e: Exception) {
            restoreBag(characterId, beforeBag)
            rollbackRoleCount(characterId, roleIndex, oldDebris, maxDebris)
            logger.error(e) { "Lò mua thất bại: charId=${characterId} category=${category} slot=${slot}" }
            return@withLock FurnaceBuyResult.Fail(BUY_ERR_ITEM)
        }

        FurnaceBuyResult.Success(
            itemId = selected.itemId,
            flagId = flagId,
            roleCountIndex = roleIndex,
            roleCountValue = oldDebris - cost,
            roleCountMax = maxDebris,
        )
    }

    suspend fun dismantle(
        characterId: Long,
        requests: List<Pair<Int, Int>>,
    ): FurnaceDismantleResult = lockFor(characterId).withLock {
        if (requests.isEmpty() || requests.size > SHOP_SIZE) {
            return@withLock FurnaceDismantleResult.Fail()
        }
        if (requests.map { it.first }.toSet().size != requests.size) {
            return@withLock FurnaceDismantleResult.Fail()
        }

        data class Prepared(
            val slot: Int,
            val qty: Int,
            val thing: ThingData,
            val def: ItemDef,
            val chips: Int,
        )

        val prepared = ArrayList<Prepared>(requests.size)
        var furnaceKind = 0
        for ((slot, qty) in requests) {
            val thing = inventorySystem.getBagItem(characterId, slot)
                ?: return@withLock FurnaceDismantleResult.Fail(furnaceKind)
            val def = itemDefs[thing.itemId]
                ?: return@withLock FurnaceDismantleResult.Fail(furnaceKind, thing.itemId)

            if (qty <= 0 || qty > thing.quantity || thing.isLocked ||
                thing.styleLevel > 0 ||
                def.specialAbility == WEAPON_RECIPE ||
                def.specialAbility == RANDOM_WEAPON_RECIPE ||
                def.furnaceKind !in 1..4 ||
                def.furnaceCount <= 0
            ) {
                return@withLock FurnaceDismantleResult.Fail(def.furnaceKind, thing.itemId)
            }
            if (furnaceKind == 0) furnaceKind = def.furnaceKind
            if (def.furnaceKind != furnaceKind) {
                return@withLock FurnaceDismantleResult.Fail(def.furnaceKind, thing.itemId)
            }

            val perItem = floor(def.furnaceCount.toDouble() / BASE_RATE * ACTIVE_RATE).toLong()
            val total = perItem * qty.toLong()
            if (perItem <= 0 || total > Int.MAX_VALUE) {
                return@withLock FurnaceDismantleResult.Fail(def.furnaceKind, thing.itemId)
            }
            prepared += Prepared(slot, qty, thing, def, total.toInt())
        }

        val beforeBag = inventorySystem.getBag(characterId).toMap()
        val entries = prepared.map {
            FurnaceDismantleEntry(it.thing.itemId, it.def.furnaceKind, it.chips)
        }

        if (!inventorySystem.removeBagItemsExact(
                characterId,
                prepared.map { it.slot to it.qty },
            )
        ) {
            return@withLock FurnaceDismantleResult.Fail(furnaceKind, prepared.first().thing.itemId)
        }

        if (furnaceKind == GODSKIN_KIND) {
            val reward = entries.sumOf { it.chipCount }
            val added = try {
                inventorySystem.addItemsToBagExact(characterId, GODSKIN_CHIP_ITEM_ID, reward)
            } catch (_: Exception) {
                null
            }
            if (added == null) {
                restoreBag(characterId, beforeBag)
                return@withLock FurnaceDismantleResult.Fail(furnaceKind, prepared.first().thing.itemId)
            }
            return@withLock FurnaceDismantleResult.Success(entries, null, null, null)
        }

        val roleIndex = when (furnaceKind) {
            NPC_KIND -> RoleCountSystem.NPC_DEBRIS
            EQUIP_KIND -> RoleCountSystem.EQUIP_DEBRIS
            EXCLUSIVE_WEAPON_KIND -> RoleCountSystem.SWEAPON_DEBRIS
            else -> return@withLock FurnaceDismantleResult.Fail(furnaceKind, prepared.first().thing.itemId)
        }
        val old = roleCountSystem.getValue(characterId, roleIndex)
        val max = roleCountSystem.getMaxValue(characterId, roleIndex)
        val gain = entries.sumOf { it.chipCount }
        if (gain > Int.MAX_VALUE - old) {
            restoreBag(characterId, beforeBag)
            return@withLock FurnaceDismantleResult.Fail(furnaceKind, prepared.first().thing.itemId)
        }

        roleCountSystem.setValue(characterId, roleIndex, old + gain, max)
        try {
            roleCountSystem.saveCounts(characterId)
        } catch (e: Exception) {
            restoreBag(characterId, beforeBag)
            rollbackRoleCount(characterId, roleIndex, old, max)
            logger.error(e) { "Lò persist mảnh thất bại: charId=${characterId} kind=${furnaceKind}" }
            return@withLock FurnaceDismantleResult.Fail(furnaceKind, prepared.first().thing.itemId)
        }

        FurnaceDismantleResult.Success(entries, roleIndex, old + gain, max)
    }

    suspend fun clearPurchasedFlags(characterId: Long, category: Int): List<Pair<Int, Boolean>> {
        if (category !in 1..6) return emptyList()
        val set = bitFlagSystem.getSetFlagIds(characterId)
        val changes = ArrayList<Pair<Int, Boolean>>(SHOP_SIZE)
        for (slot in 1..SHOP_SIZE) {
            val flag = purchaseFlag(category, slot)
            if (flag in set) {
                bitFlagSystem.clearFlag(characterId, flag)
                changes += flag to false
            }
        }
        return changes
    }

    fun categoryForRefreshSellId(sellId: Int): Int? =
        categoryFromRefreshSellId(sellId)

    fun refreshSellIds(): Set<Int> = REFRESH_SELL_IDS.values.toSet()

    private fun generateCategory(characterId: Long, category: Int, version: Int): FurnaceShopCategory {
        val patterns = selectDefs.filter { it.kind == category }
        require(patterns.isNotEmpty()) { "Thiếu FurnaceSelect kind=${category}" }
        require(patterns.sumOf { it.sample } == 10_000) {
            "FurnaceSelect kind=${category} sample sum != 10000"
        }

        val rng = Random(seed(characterId, category, version))
        val roll = rng.nextInt(10_000)
        var cursor = 0
        val pattern = patterns.firstOrNull {
            cursor += it.sample
            roll < cursor
        } ?: patterns.last()

        require(pattern.awardCounts.size == 6 && pattern.awardCounts.sum() == SHOP_SIZE) {
            "FurnaceSelect id=${pattern.id} không tạo đúng 8 ô"
        }

        val slotKind = slotKindForCategory(category)
        val todayValue = today()
        val result = ArrayList<FurnaceShopItem>(SHOP_SIZE)
        for (awardKind in 1..6) {
            val wanted = pattern.awardCounts[awardKind - 1]
            if (wanted <= 0) continue
            val pool = slotDefs.asSequence()
                .filter { it.kind == slotKind && it.awardKind == awardKind }
                .filter { isOpen(it, todayValue) }
                .filter { itemDefs.containsKey(it.itemId) }
                .toMutableList()
            require(pool.size >= wanted) {
                "Thiếu FurnaceSlot kind=${slotKind} award=${awardKind} need=${wanted} have=${pool.size}"
            }
            java.util.Collections.shuffle(pool, rng)
            result += pool.take(wanted).map { FurnaceShopItem(it.itemId, 1) }
        }
        java.util.Collections.shuffle(result, rng)
        require(result.size == SHOP_SIZE)

        return FurnaceShopCategory(category, isCrit = false, items = result)
    }

    private fun isOpen(def: FurnaceSlotDef, date: LocalDate): Boolean {
        if (def.timedOpen < 1) return true
        return try {
            val open = LocalDate.of(def.openYear, def.openMonth, def.openDay)
            val close = LocalDate.of(def.closeYear, def.closeMonth, def.closeDay)
            !date.isBefore(open) && !date.isAfter(close)
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun restoreBag(characterId: Long, before: Map<Int, ThingData>) {
        val current = inventorySystem.getBag(characterId)
        val slots = before.keys union current.keys
        val changes = slots
            .filter { before[it] != current[it] }
            .associateWith { before[it] }
        if (changes.isNotEmpty()) {
            try {
                inventorySystem.setBagItemsAtomic(characterId, changes)
            } catch (e: Exception) {
                logger.error(e) { "Lò rollback túi thất bại: charId=${characterId}" }
            }
        }
    }

    private suspend fun rollbackRoleCount(characterId: Long, index: Int, value: Int, max: Int) {
        roleCountSystem.setValue(characterId, index, value, max)
        try {
            roleCountSystem.saveCounts(characterId)
        } catch (e: Exception) {
            logger.error(e) { "Lò rollback RoleCount thất bại: charId=${characterId} index=${index}" }
        }
    }

    private fun lockFor(characterId: Long): Mutex = locks.computeIfAbsent(characterId) { Mutex() }

    companion object {
        const val SHOP_SIZE = 8
        const val BASE_RATE = 1.0
        const val ACTIVE_RATE = 1.0
        const val GOLD_STORE_RATE = 2

        const val NPC_KIND = 1
        const val EQUIP_KIND = 2
        const val EXCLUSIVE_WEAPON_KIND = 3
        const val GODSKIN_KIND = 4
        const val GODSKIN_CHIP_ITEM_ID = 55403

        const val WEAPON_RECIPE = 234
        const val RANDOM_WEAPON_RECIPE = 235

        const val BUY_ERR_KIND = 2
        const val BUY_ERR_SLOT = 3
        const val BUY_ERR_ITEM = 4
        const val BUY_ERR_PURCHASED = 5
        const val BUY_ERR_DEBRIS = 6
        const val BUY_ERR_BAG_FULL = 7

        private val GOLD_CATEGORIES = setOf(3, 4, 6)

        private val REFRESH_SELL_IDS = mapOf(
            1 to 24,
            2 to 25,
            3 to 33,
            4 to 34,
            5 to 59,
            6 to 60,
        )

        private val FLAG_START = mapOf(
            1 to 1518,
            2 to 1526,
            3 to 7067,
            4 to 7075,
            5 to 7257,
            6 to 7265,
        )

        fun refreshSellId(category: Int): Int =
            REFRESH_SELL_IDS[category] ?: error("Furnace category không hợp lệ: ${category}")

        fun categoryFromRefreshSellId(sellId: Int): Int? =
            REFRESH_SELL_IDS.entries.firstOrNull { it.value == sellId }?.key

        fun purchaseFlag(category: Int, slot: Int): Int {
            val start = FLAG_START[category] ?: return 0
            if (slot !in 1..SHOP_SIZE) return 0
            return start + slot - 1
        }

        fun slotKindForCategory(category: Int): Int = when (category) {
            1, 3 -> 1
            2, 4 -> 2
            5, 6 -> 3
            else -> error("Furnace category không hợp lệ: ${category}")
        }

        fun debrisRoleCount(category: Int): Int = when (category) {
            1, 3, 5, 6 -> RoleCountSystem.NPC_DEBRIS
            2, 4 -> RoleCountSystem.EQUIP_DEBRIS
            else -> error("Furnace category không hợp lệ: ${category}")
        }

        private fun seed(characterId: Long, category: Int, version: Int): Long {
            var x = characterId xor (category.toLong() shl 40) xor (version.toLong() shl 8)
            x = x xor 0x4655524E414345L
            x = (x xor (x ushr 30)) * -4658895280553007687L
            x = (x xor (x ushr 27)) * -7723592293110705685L
            return x xor (x ushr 31)
        }
    }
}
