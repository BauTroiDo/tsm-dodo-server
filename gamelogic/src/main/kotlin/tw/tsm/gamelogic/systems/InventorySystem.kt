package tw.tsm.gamelogic.systems

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.constants.BankResult
import tw.tsm.domain.constants.EThings
import tw.tsm.domain.constants.ItemConstants
import tw.tsm.domain.data.ItemDef
import tw.tsm.domain.enums.EquipSlot
import tw.tsm.domain.interfaces.repositories.InventoryRepository
import tw.tsm.domain.interfaces.repositories.MiningFuelCommit
import tw.tsm.domain.valueobjects.ThingData
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

/**
 * Hệ thống quản lý Túi đồ / Trang bị / Tiền trang / Bảo khố
 *
 * Duy trì trạng thái vật phẩm của mỗi nhân vật đang online trong bộ nhớ, kết hợp với lưu trữ liên tục (persistence) vào DB.
 * Tương ứng với `Item.lua` của bagThings / equipThings / bankThings / storageThings.
 *
 * Map bên trong sử dụng ConcurrentHashMap để ngăn ngừa hỏng cấu trúc do đồng thời (concurrency).
 *
 * @param inventoryRepo Truy cập lưu trữ liên tục
 * @param itemDefs Bảng định nghĩa vật phẩm (được tiêm từ GameDataManager.itemDefs ở tầng app)
 * @param roleCountSystem Tính toán động số ô trống của túi đồ (mở rộng Bag_1/2/3)
 */
class InventorySystem(
    private val inventoryRepo: InventoryRepository,
    private val itemDefs: Map<Int, ItemDef>,
    private val roleCountSystem: RoleCountSystem
) {
    /** Túi đồ: characterId → (slot → ThingData) */
    private val bags = ConcurrentHashMap<Long, ConcurrentHashMap<Int, ThingData>>()
    /** Trang bị: characterId → (fitType → ThingData) */
    private val equips = ConcurrentHashMap<Long, ConcurrentHashMap<Int, ThingData>>()
    /** Tiền trang: characterId → (slot → ThingData) */
    private val banks = ConcurrentHashMap<Long, ConcurrentHashMap<Int, ThingData>>()
    /** Bảo khố: characterId → (slot → ThingData) */
    private val storages = ConcurrentHashMap<Long, ConcurrentHashMap<Int, ThingData>>()

    // ── Tải/Gỡ dữ liệu ──

    /**
     * Tải dữ liệu túi đồ, trang bị, tiền trang, bảo khố của nhân vật từ DB vào bộ nhớ
     */
    suspend fun loadCharacterData(characterId: Long) {
        val bagData = ConcurrentHashMap(inventoryRepo.loadItems(characterId, EThings.BAG))
        val equipData = ConcurrentHashMap(inventoryRepo.loadItems(characterId, EThings.EQUIP))
        val bankData = ConcurrentHashMap(inventoryRepo.loadItems(characterId, EThings.BANK))
        val storageData = ConcurrentHashMap(inventoryRepo.loadItems(characterId, EThings.STORAGE))

        bags[characterId] = bagData
        equips[characterId] = equipData
        banks[characterId] = bankData
        storages[characterId] = storageData

        logger.info {
            "Tải vật phẩm nhân vật: characterId=$characterId Túi đồ=${bagData.size} " +
                "slotMin=${bagData.keys.minOrNull()} slotMax=${bagData.keys.maxOrNull()} " +
                "capacity=${roleCountSystem.getBagCapacity(characterId)} " +
                "Trang bị=${equipData.size} Tiền trang=${bankData.size} Bảo khố=${storageData.size}"
        }
    }

    /**
     * Lưu vật phẩm nhân vật (không gỡ khỏi bộ nhớ, dùng cho tự động lưu định kỳ)
     */
    suspend fun save(characterId: Long) {
        CharacterPersistenceGuard.withLock(characterId) {
            bags[characterId]?.let { inventoryRepo.saveAllItems(characterId, EThings.BAG, it) }
            equips[characterId]?.let { inventoryRepo.saveAllItems(characterId, EThings.EQUIP, it) }
            banks[characterId]?.let { inventoryRepo.saveAllItems(characterId, EThings.BANK, it) }
            storages[characterId]?.let { inventoryRepo.saveAllItems(characterId, EThings.STORAGE, it) }
        }
    }

    /**
     * Lưu và gỡ dữ liệu nhân vật khỏi bộ nhớ
     */
    suspend fun saveAndUnload(characterId: Long) {
        CharacterPersistenceGuard.withLock(characterId) {
            bags.remove(characterId)?.let { inventoryRepo.saveAllItems(characterId, EThings.BAG, it) }
            equips.remove(characterId)?.let { inventoryRepo.saveAllItems(characterId, EThings.EQUIP, it) }
            banks.remove(characterId)?.let { inventoryRepo.saveAllItems(characterId, EThings.BANK, it) }
            storages.remove(characterId)?.let { inventoryRepo.saveAllItems(characterId, EThings.STORAGE, it) }
            logger.info { "Lưu và gỡ vật phẩm nhân vật: characterId=$characterId" }
        }
    }

    // ── Lấy trạng thái ──

    /** Lấy tất cả vật phẩm trong túi đồ */
    suspend fun ownedHouseFurniture(characterId: Long): Map<Int, Int> =
        inventoryRepo.loadOwnedHouseFurniture(characterId)

    /** Character-fenced publish AFTER bag+furniture SQL commit, never before. */
    suspend fun redeemHouseFurniture(
        characterId: Long, slot: Int, itemId: Int, furnitureId: Int, quantity: Int,
    ): Int? = CharacterPersistenceGuard.withLock(characterId) {
        val bag = bags[characterId] ?: return@withLock null
        val current = bag[slot] ?: return@withLock null
        if (current.itemId != itemId || quantity !in 1..current.quantity || furnitureId !in 1..65535)
            return@withLock null
        val result = inventoryRepo.redeemHouseFurnitureAtomic(characterId, slot, itemId, furnitureId, quantity)
            ?: return@withLock null
        val after = result.bagAfter
        if (after == null) bag.remove(slot) else bag[slot] = after
        result.ownedTotal
    }

    fun getBag(characterId: Long): Map<Int, ThingData> =
        bags[characterId] ?: emptyMap()

    /** Lấy tất cả vật phẩm trong ô trang bị */
    fun getEquipment(characterId: Long): Map<Int, ThingData> =
        equips[characterId] ?: emptyMap()

    /** Lấy tất cả vật phẩm trong tiền trang */
    fun getBank(characterId: Long): Map<Int, ThingData> =
        banks[characterId] ?: emptyMap()

    /** Lấy tất cả vật phẩm trong bảo khố */
    fun getStorage(characterId: Long): Map<Int, ThingData> =
        storages[characterId] ?: emptyMap()

    /** Lấy vật phẩm tại ô chỉ định trong túi đồ */
    fun getBagItem(characterId: Long, slot: Int): ThingData? =
        bags[characterId]?.get(slot)

    /** Validate an ordered quest exchange on a copy before consuming any real items. */
    fun canApplyQuestItemChanges(characterId: Long, changes: List<Pair<Int, Int>>): Boolean {
        val simulated = bags[characterId]?.toMutableMap() ?: return false
        val capacity = roleCountSystem.getBagCapacity(characterId)
        for ((itemId, delta) in changes) {
            if (itemId <= 0) return false
            if (delta < 0) {
                var remaining = -delta.toLong()
                for ((slot, thing) in simulated.toSortedMap()) {
                    if (thing.itemId != itemId) continue
                    val take = minOf(thing.quantity.toLong(), remaining).toInt()
                    if (take == thing.quantity) simulated.remove(slot)
                    else simulated[slot] = thing.copy(quantity = thing.quantity - take)
                    remaining -= take
                    if (remaining == 0L) break
                }
                if (remaining != 0L) return false
            } else if (delta > 0) {
                val def = itemDefs[itemId] ?: return false
                val stackLimit = if (def.fitType == 0) ItemConstants.MAX_STACK else 1
                var remaining = delta.toLong()
                for ((slot, thing) in simulated.toSortedMap()) {
                    if (slot !in 0 until capacity || thing.itemId != itemId || thing.quantity >= stackLimit) continue
                    val add = minOf((stackLimit - thing.quantity).toLong(), remaining).toInt()
                    simulated[slot] = thing.copy(quantity = thing.quantity + add)
                    remaining -= add
                    if (remaining == 0L) break
                }
                for (slot in 0 until capacity) {
                    if (remaining == 0L) break
                    if (slot in simulated) continue
                    val add = minOf(stackLimit.toLong(), remaining).toInt()
                    simulated[slot] = ThingData(itemId = itemId, quantity = add)
                    remaining -= add
                }
                if (remaining != 0L) return false
            }
        }
        return true
    }

    /**
     * Kiểm tra toàn bộ số lượng vật phẩm có thể được thêm vào túi mà không bị thêm một phần.
     *
     * Dùng trước các giao dịch có khấu trừ tiền/Point để tránh mất tiền khi túi đã đầy.
     * Tính cả chỗ trống còn lại trong stack hiện có và các ô trống động theo RoleCount.
     */
    fun canAddItemToBag(characterId: Long, itemId: Int, totalQty: Int): Boolean {
        if (totalQty <= 0) return false
        val bag = bags[characterId] ?: return false
        val def = itemDefs[itemId] ?: return false
        val stackLimit = if (def.fitType == 0) ItemConstants.MAX_STACK else 1

        var remaining = totalQty
        if (stackLimit > 1) {
            for (thing in bag.values) {
                if (thing.itemId == itemId && thing.quantity < stackLimit) {
                    remaining -= (stackLimit - thing.quantity)
                    if (remaining <= 0) return true
                }
            }
        }

        val capacity = roleCountSystem.getBagCapacity(characterId)
        var emptySlots = 0
        for (slot in 0 until capacity) {
            if (!bag.containsKey(slot)) emptySlots++
        }
        val slotsNeeded = (remaining + stackLimit - 1) / stackLimit
        return emptySlots >= slotsNeeded
    }

    /**
     * Thêm chính xác toàn bộ số lượng hoặc hoàn tác trạng thái vật phẩm cùng ID nếu có lỗi/race.
     */
    /** Character-serialized BAG mutation; coordinates with autosave and staged Treasure commit. */
    suspend fun addItemsToBagExact(characterId: Long, itemId: Int, totalQty: Int): List<Pair<Int, ThingData>>? =
        CharacterPersistenceGuard.withLock(characterId) { addItemsToBagExactUnfenced(characterId, itemId, totalQty) }

    private suspend fun addItemsToBagExactUnfenced(
        characterId: Long,
        itemId: Int,
        totalQty: Int
    ): List<Pair<Int, ThingData>>? {
        if (!canAddItemToBag(characterId, itemId, totalQty)) return null
        val bag = bags[characterId] ?: return null
        val before = bag.filterValues { it.itemId == itemId }.toMap()
        val beforeQty = before.values.sumOf { it.quantity }

        val results = addItemsToBagUnfenced(characterId, itemId, totalQty)
        val afterQty = bag.values.filter { it.itemId == itemId }.sumOf { it.quantity }
        if (afterQty - beforeQty == totalQty) return results

        // Khôi phục đúng các slot trước giao dịch; trường hợp này chỉ xảy ra nếu trạng thái
        // túi thay đổi giữa bước kiểm tra dung lượng và bước thêm.
        val currentSlots = bag.entries.filter { it.value.itemId == itemId }.map { it.key }.toSet()
        for (slot in currentSlots union before.keys) {
            val original = before[slot]
            if (original == null) bag.remove(slot) else bag[slot] = original
            inventoryRepo.saveItem(characterId, EThings.BAG, slot, original)
        }
        logger.warn {
            "Hoàn tác addItemsToBagExact: characterId=$characterId itemId=$itemId qty=$totalQty"
        }
        return null
    }

    /**
     * Thêm nhiều loại vật phẩm vào túi theo một giao dịch all-or-nothing.
     *
     * Dùng cho phần thưởng Sưu Tập: một võ tướng có thể thưởng 1/3/5 loại vật phẩm,
     * và C:095-011 còn có thể nhận nhiều võ tướng cùng lúc. Phải mô phỏng toàn bộ
     * bundle trước khi ghi để không xảy ra trạng thái nhận một phần nhưng đã đánh dấu thưởng.
     *
     * @return các slot cuối cùng cần đồng bộ về client; null nếu bundle không thể thêm trọn vẹn.
     */
    /** Character-serialized BAG mutation; coordinates with autosave and staged Treasure commit. */
    suspend fun addItemBundleToBagExact(characterId: Long, rewards: List<Pair<Int, Int>>): List<Pair<Int, ThingData>>? =
        CharacterPersistenceGuard.withLock(characterId) { addItemBundleToBagExactUnfenced(characterId, rewards) }

    private suspend fun addItemBundleToBagExactUnfenced(
        characterId: Long,
        rewards: List<Pair<Int, Int>>,
    ): List<Pair<Int, ThingData>>? {
        val bag = bags[characterId] ?: run {
            logger.warn { "addItemBundleToBagExact: chưa nạp túi characterId=$characterId" }
            return null
        }
        val normalized = rewards
            .filter { (itemId, qty) -> itemId > 0 && qty > 0 }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, qtys) -> qtys.sum() }
        if (normalized.isEmpty()) return emptyList()

        val capacity = roleCountSystem.getBagCapacity(characterId)
        val before = bag.toMap()
        val simulated = before.toMutableMap()

        for ((itemId, totalQty) in normalized) {
            val def = itemDefs[itemId] ?: run {
                logger.warn { "addItemBundleToBagExact: thiếu ItemDef itemId=$itemId characterId=$characterId" }
                return null
            }
            val stackLimit = if (def.fitType == 0) ItemConstants.MAX_STACK else 1
            var remaining = totalQty

            if (stackLimit > 1) {
                for ((slot, thing) in simulated.toSortedMap()) {
                    if (remaining <= 0) break
                    if (thing.itemId == itemId && thing.quantity < stackLimit) {
                        val add = minOf(remaining, stackLimit - thing.quantity)
                        simulated[slot] = thing.copy(quantity = thing.quantity + add)
                        remaining -= add
                    }
                }
            }

            while (remaining > 0) {
                val emptySlot = (0 until capacity).firstOrNull { !simulated.containsKey(it) }
                    ?: run {
                        logger.warn {
                            "addItemBundleToBagExact: túi thực sự đầy characterId=$characterId " +
                                "capacity=$capacity bagSize=${before.size} itemId=$itemId remaining=$remaining"
                        }
                        return null
                    }
                val qty = minOf(remaining, stackLimit)
                simulated[emptySlot] = ThingData(itemId = itemId, quantity = qty)
                remaining -= qty
            }
        }

        val changedSlots = (before.keys union simulated.keys)
            .filter { before[it] != simulated[it] }
            .sorted()

        try {
            for (slot in changedSlots) {
                val next = simulated[slot]
                if (next == null) bag.remove(slot) else bag[slot] = next
                inventoryRepo.saveItem(characterId, EThings.BAG, slot, next)
            }
        } catch (e: Exception) {
            logger.error(e) { "addItemBundleToBagExact lỗi, rollback characterId=$characterId slots=$changedSlots" }
            for (slot in changedSlots) {
                val original = before[slot]
                if (original == null) bag.remove(slot) else bag[slot] = original
                try {
                    inventoryRepo.saveItem(characterId, EThings.BAG, slot, original)
                } catch (rollbackError: Exception) {
                    logger.error(rollbackError) {
                        "Rollback bundle thưởng thất bại: characterId=$characterId slot=$slot"
                    }
                }
            }
            return null
        }

        return changedSlots.mapNotNull { slot -> simulated[slot]?.let { slot to it } }
    }

    /**
     * Trừ nhiều stack túi đồ theo một giao dịch all-or-nothing.
     *
     * Dùng cho DVT UISell sellId=11: client có thể chọn nhiều stack item31171
     * và gửi bagIndex(UInt16)+qty(UInt16) cho từng stack. Server validate toàn bộ
     * trước khi mutation; nếu bất kỳ save/remove nào thất bại thì khôi phục snapshot.
     */
    /** Character-serialized BAG mutation; coordinates with autosave and staged Treasure commit. */
    /**
     * DB-first mining fuel mutation. A successful repository commit returns
     * the new BAG snapshot. Publish it under the same character write fence
     * shared by autosave, logout and other inventory writers.
     */
    suspend fun consumeMiningFuelAtomic(
        characterId: Long, slot: Int, itemId: Int, quantity: Int,
        commit: suspend (ThingData) -> MiningFuelCommit?,
    ): MiningFuelCommit? = CharacterPersistenceGuard.withLock(characterId) {
        val bag = bags[characterId] ?: return@withLock null
        val before = bag[slot] ?: return@withLock null
        if (slot < 0 || quantity <= 0 || before.itemId != itemId ||
            before.isLocked || before.quantity < quantity
        ) return@withLock null

        val outcome = commit(before) ?: return@withLock null
        val expectedAfter = if (quantity == before.quantity) null
            else before.copy(quantity = before.quantity - quantity)
        check(outcome.bagAfter == expectedAfter) {
            "Mining repository returned inconsistent post-commit BAG state"
        }
        val committedAfter = outcome.bagAfter
        if (committedAfter == null) bag.remove(slot)
        else bag[slot] = committedAfter
        outcome
    }

    suspend fun removeBagItemsExact(characterId: Long, consumptions: List<Pair<Int, Int>>): Boolean =
        CharacterPersistenceGuard.withLock(characterId) { removeBagItemsExactUnfenced(characterId, consumptions) }

    private suspend fun removeBagItemsExactUnfenced(
        characterId: Long,
        consumptions: List<Pair<Int, Int>>
    ): Boolean {
        if (consumptions.isEmpty()) return false
        val bag = bags[characterId] ?: return false
        if (consumptions.any { (slot, qty) -> slot < 0 || qty <= 0 } ||
            consumptions.map { it.first }.toSet().size != consumptions.size
        ) {
            return false
        }

        val before = linkedMapOf<Int, ThingData>()
        for ((slot, qty) in consumptions) {
            val thing = bag[slot] ?: return false
            if (thing.quantity < qty) return false
            before[slot] = thing
        }

        try {
            for ((slot, qty) in consumptions) {
                val current = bag[slot] ?: error("Bag slot disappeared during exact removal")
                val next = if (current.quantity == qty) null
                    else current.copy(quantity = current.quantity - qty)

                if (next == null) bag.remove(slot) else bag[slot] = next
                inventoryRepo.saveItem(characterId, EThings.BAG, slot, next)
            }
            return true
        } catch (e: Exception) {
            logger.error(e) {
                "removeBagItemsExact lỗi, rollback: characterId=" + characterId +
                    " slots=" + before.keys
            }
            for ((slot, original) in before) {
                bag[slot] = original
                try {
                    inventoryRepo.saveItem(characterId, EThings.BAG, slot, original)
                } catch (rollbackError: Exception) {
                    logger.error(rollbackError) {
                        "Rollback bag thất bại: characterId=" + characterId + " slot=" + slot
                    }
                }
            }
            return false
        }
    }

    /**
     * Kiểm tra một batch reward có thể vào túi trọn vẹn hay không.
     * Các item cùng ID được gộp lượng để tính stack, các ID khác nhau cùng tranh số ô trống.
     */
    fun canAddRewardsToBag(characterId: Long, rewards: List<Pair<Int, Int>>): Boolean {
        if (rewards.isEmpty()) return false
        val bag = bags[characterId] ?: return false
        val grouped = rewards
            .filter { it.first > 0 && it.second > 0 }
            .groupingBy { it.first }
            .fold(0) { acc, pair -> acc + pair.second }
        if (grouped.isEmpty()) return false

        val capacity = roleCountSystem.getBagCapacity(characterId)
        var emptySlots = (0 until capacity).count { !bag.containsKey(it) }

        for ((itemId, totalQty) in grouped) {
            val def = itemDefs[itemId] ?: return false
            val stackLimit = if (def.fitType == 0) ItemConstants.MAX_STACK else 1
            var remaining = totalQty
            if (stackLimit > 1) {
                val freeInExisting = bag.values
                    .filter { it.itemId == itemId && it.quantity < stackLimit }
                    .sumOf { stackLimit - it.quantity }
                remaining = (remaining - freeInExisting).coerceAtLeast(0)
            }
            val needed = if (remaining == 0) 0 else (remaining + stackLimit - 1) / stackLimit
            if (needed > emptySlots) return false
            emptySlots -= needed
        }
        return true
    }

    /**
     * Thêm toàn bộ batch reward hoặc rollback toàn bộ bag về snapshot trước giao dịch.
     * Trả về các slot cuối cùng đã thay đổi để caller sync client.
     */
    /** Character-serialized multi-container transfer; fenced with save/logout and Treasure settlement. */
    suspend fun addRewardsToBagExact(characterId: Long, rewards: List<Pair<Int, Int>>): List<Pair<Int, ThingData>>? =
        CharacterPersistenceGuard.withLock(characterId) { addRewardsToBagExactUnfenced(characterId, rewards) }

    private suspend fun addRewardsToBagExactUnfenced(
        characterId: Long,
        rewards: List<Pair<Int, Int>>
    ): List<Pair<Int, ThingData>>? {
        if (!canAddRewardsToBag(characterId, rewards)) return null
        val bag = bags[characterId] ?: return null
        val before = bag.toMap()
        val expected = rewards
            .filter { it.first > 0 && it.second > 0 }
            .groupingBy { it.first }
            .fold(0) { acc, pair -> acc + pair.second }
        val beforeCounts = expected.keys.associateWith { id ->
            before.values.filter { it.itemId == id }.sumOf { it.quantity }
        }

        try {
            for ((itemId, qty) in expected) {
                addItemsToBagUnfenced(characterId, itemId, qty)
            }
            val ok = expected.all { (itemId, qty) ->
                val afterQty = bag.values.filter { it.itemId == itemId }.sumOf { it.quantity }
                afterQty - (beforeCounts[itemId] ?: 0) == qty
            }
            if (ok) {
                return bag.entries
                    .filter { (slot, thing) -> before[slot] != thing }
                    .sortedBy { it.key }
                    .map { it.key to it.value }
            }
        } catch (e: Exception) {
            logger.error(e) { "addRewardsToBagExact lỗi: characterId=$characterId" }
        }

        val allSlots = bag.keys.toSet() union before.keys
        for (slot in allSlots) {
            val original = before[slot]
            if (original == null) bag.remove(slot) else bag[slot] = original
            inventoryRepo.saveItem(characterId, EThings.BAG, slot, original)
        }
        logger.warn { "Rollback batch reward bag: characterId=$characterId rewards=$expected" }
        return null
    }

    /**
     * Có đang sở hữu đạo cụ nhị chuyển (chuyển sinh lần 2) của võ tướng chỉ định hay không
     *
     * Tương ứng với Lua `UINpcTurn.CheckTurn(currentTurn=2)`:
     * `item.kind == 51 && attr1.kind == 67 && attr1.value == npcId && attr2.kind == 67`.
     */
    fun hasNpcSecondReincarnationToken(characterId: Long, npcId: Int): Boolean {
        val bag = bags[characterId] ?: return false
        return bag.values.any { thing ->
            val def = itemDefs[thing.itemId] ?: return@any false
            thing.quantity > 0 &&
                    def.kind == NPC_SECOND_REINCARNATION_ITEM_KIND &&
                    def.attr1Kind == NPC_SECOND_REINCARNATION_ATTR_KIND &&
                    def.attr1Value == npcId &&
                    def.attr2Kind == NPC_SECOND_REINCARNATION_ATTR_KIND
        }
    }

    /** Lấy vật phẩm tại vị trí chỉ định trong ô trang bị */
    fun getEquipItem(characterId: Long, fitType: Int): ThingData? =
        equips[characterId]?.get(fitType)

    /** Lấy vật phẩm tại ô chỉ định trong tiền trang */
    fun getBankItem(characterId: Long, slot: Int): ThingData? =
        banks[characterId]?.get(slot)

    /** Lấy vật phẩm tại ô chỉ định trong bảo khố */
    fun getStorageItem(characterId: Long, slot: Int): ThingData? =
        storages[characterId]?.get(slot)

    // ── Thao tác túi đồ ──

    /**
     * Thêm vật phẩm vào túi đồ (phương thức lối tắt cho đơn vị nhỏ)
     *
     * Ủy thác cho [addItemsToBag], chỉ trả về kết quả đầu tiên.
     *
     * @return Khi thành công trả về (slot, ThingData sau khi cập nhật), nếu túi đầy trả về null
     */
    suspend fun addItemToBag(characterId: Long, itemId: Int, quantity: Int): Pair<Int, ThingData>? {
        val results = addItemsToBag(characterId, itemId, quantity)
        return results.firstOrNull()
    }

    /**
     * Thêm vật phẩm vào túi đồ, hỗ trợ chia ô khi vượt mức (tràn)
     *
     * Tham khảo logic chia đợt đệ quy của tskero `TSItemContainer.addItem()`:
     * 1. Vật phẩm có thể cộng dồn (fitType==0) sẽ ưu tiên lấp đầy các ô đã có cùng ID (giới hạn = 9999)
     * 2. Phần dư sẽ đặt vào ô mới (giới hạn mỗi ô = 9999)
     * 3. Nếu túi đồ đầy thì dừng lại, trả về danh sách các vật phẩm đã được đưa vào thành công
     *
     * @param characterId ID nhân vật
     * @param itemId ID vật phẩm
     * @param totalQty Tổng số lượng muốn thêm
     * @return Danh sách các vật phẩm đã được đưa vào thành công [(slot, ThingData)], có thể trống (nếu túi đồ đầy hoàn toàn)
     */
    /** Character-serialized BAG mutation; coordinates with autosave and staged Treasure commit. */
    suspend fun addItemsToBag(characterId: Long, itemId: Int, totalQty: Int): List<Pair<Int, ThingData>> =
        CharacterPersistenceGuard.withLock(characterId) { addItemsToBagUnfenced(characterId, itemId, totalQty) }

    private suspend fun addItemsToBagUnfenced(characterId: Long, itemId: Int, totalQty: Int): List<Pair<Int, ThingData>> {
        val bag = bags[characterId] ?: return emptyList()
        val def = itemDefs[itemId]
        val stackLimit = if (def != null && def.fitType == 0) {
            ItemConstants.MAX_STACK  // 9999, giống với Item.lua MaxThingGridQuant
        } else {
            1  // Trang bị (fitType>0) mỗi ô chỉ chứa 1 cái
        }

        val results = mutableListOf<Pair<Int, ThingData>>()
        var remaining = totalQty

        // Giai đoạn một: Lấp đầy các ô đã có cùng ID
        if (stackLimit > 1) {
            for ((slot, thing) in bag) {
                if (remaining <= 0) break
                if (thing.itemId == itemId && thing.quantity < stackLimit) {
                    val canAdd = minOf(remaining, stackLimit - thing.quantity)
                    val updated = thing.copy(quantity = thing.quantity + canAdd)
                    bag[slot] = updated
                    inventoryRepo.saveItem(characterId, EThings.BAG, slot, updated)
                    results.add(Pair(slot, updated))
                    remaining -= canAdd
                }
            }
        }

        // Giai đoạn hai: Phần dư đặt vào ô mới
        while (remaining > 0) {
            val emptySlot = findEmptyBagSlot(characterId) ?: break
            val qty = minOf(remaining, stackLimit)
            val newThing = ThingData(itemId = itemId, quantity = qty)
            bag[emptySlot] = newThing
            inventoryRepo.saveItem(characterId, EThings.BAG, emptySlot, newThing)
            results.add(Pair(emptySlot, newThing))
            remaining -= qty
        }

        if (remaining > 0) {
            logger.warn { "Túi đồ đã đầy, không thể thêm $remaining vật phẩm còn lại có itemId=$itemId (characterId=$characterId)" }
        }

        return results
    }

    /**
     * Giảm số lượng vật phẩm trong túi đồ (nếu số lượng về 0 thì xóa)
     *
     * @return Trả về true nếu thành công
     */
    /** Character-serialized BAG mutation; coordinates with autosave and staged Treasure commit. */
    suspend fun removeFromBag(characterId: Long, slot: Int, quantity: Int): Boolean =
        CharacterPersistenceGuard.withLock(characterId) { removeFromBagUnfenced(characterId, slot, quantity) }

    private suspend fun removeFromBagUnfenced(characterId: Long, slot: Int, quantity: Int): Boolean {
        val bag = bags[characterId] ?: return false
        val thing = bag[slot] ?: return false

        if (thing.quantity <= quantity) {
            bag.remove(slot)
            inventoryRepo.saveItem(characterId, EThings.BAG, slot, null)
        } else {
            val updated = thing.copy(quantity = thing.quantity - quantity)
            bag[slot] = updated
            inventoryRepo.saveItem(characterId, EThings.BAG, slot, updated)
        }
        return true
    }

    /**
     * Di chuyển vật phẩm trong túi đồ (hoán đổi hoặc cộng dồn)
     */
    /** Character-serialized BAG mutation; coordinates with autosave and staged Treasure commit. */
    suspend fun moveBagItem(characterId: Long, fromSlot: Int, quantity: Int, toSlot: Int): Boolean =
        CharacterPersistenceGuard.withLock(characterId) { moveBagItemUnfenced(characterId, fromSlot, quantity, toSlot) }

    private suspend fun moveBagItemUnfenced(characterId: Long, fromSlot: Int, quantity: Int, toSlot: Int): Boolean {
        val bag = bags[characterId] ?: return false
        val fromThing = bag[fromSlot] ?: return false
        val toThing = bag[toSlot]

        if (toThing == null) {
            // Mục tiêu trống: Chuyển sang
            if (quantity >= fromThing.quantity) {
                bag[toSlot] = fromThing
                bag.remove(fromSlot)
            } else {
                bag[toSlot] = fromThing.copy(quantity = quantity)
                bag[fromSlot] = fromThing.copy(quantity = fromThing.quantity - quantity)
            }
        } else if (toThing.itemId == fromThing.itemId && itemDefs[fromThing.itemId]?.fitType == 0) {
            // Cùng vật phẩm có thể cộng dồn (fitType==0 là phi trang bị, giới hạn = MAX_STACK)
            val stackLimit = ItemConstants.MAX_STACK
            val total = toThing.quantity + minOf(quantity, fromThing.quantity)
            val newToQty = minOf(total, stackLimit)
            val remainder = fromThing.quantity - minOf(quantity, fromThing.quantity) + (total - newToQty)
            bag[toSlot] = toThing.copy(quantity = newToQty)
            if (remainder > 0) {
                bag[fromSlot] = fromThing.copy(quantity = remainder)
            } else {
                bag.remove(fromSlot)
            }
        } else {
            // Hoán đổi
            bag[toSlot] = fromThing
            bag[fromSlot] = toThing
        }

        // Lưu trữ liên tục cho cả hai ô
        inventoryRepo.saveItem(characterId, EThings.BAG, fromSlot, bag[fromSlot])
        inventoryRepo.saveItem(characterId, EThings.BAG, toSlot, bag[toSlot])
        return true
    }

    /**
     * Thiết lập trực tiếp vật phẩm ở một ô cụ thể trong túi đồ
     */
    /** Character-serialized BAG mutation; coordinates with autosave and staged Treasure commit. */
    suspend fun setBagItem(characterId: Long, slot: Int, thing: ThingData?): Unit =
        CharacterPersistenceGuard.withLock(characterId) { setBagItemUnfenced(characterId, slot, thing) }

    private suspend fun setBagItemUnfenced(characterId: Long, slot: Int, thing: ThingData?) {
        val bag = bags[characterId] ?: return
        if (thing == null || thing.isEmpty) {
            bag.remove(slot)
        } else {
            bag[slot] = thing
        }
        inventoryRepo.saveItem(characterId, EThings.BAG, slot, thing)
    }

    /**
     * Persist nhiều ô túi đồ atomically ở repository trước, chỉ cập nhật cache khi DB đã commit.
     * Dùng cho Myth Enchant để tránh trạng thái nửa cập nhật giữa trang bị và rune.
     */
    /** Character-serialized BAG mutation; coordinates with autosave and staged Treasure commit. */
    suspend fun setBagItemsAtomic(characterId: Long, changes: Map<Int, ThingData?>): Unit =
        CharacterPersistenceGuard.withLock(characterId) { setBagItemsAtomicUnfenced(characterId, changes) }

    private suspend fun setBagItemsAtomicUnfenced(characterId: Long, changes: Map<Int, ThingData?>) {
        val bag = bags[characterId] ?: throw IllegalStateException("Bag chưa được load: $characterId")
        inventoryRepo.saveItemsAtomic(characterId, EThings.BAG, changes)
        for ((slot, thing) in changes) {
            if (thing == null || thing.isEmpty) {
                bag.remove(slot)
            } else {
                bag[slot] = thing
            }
        }
    }

    /**
     * Exchange a consumed BlissBag for its complete rolled bundle in ONE DB transaction.
     * No item is consumed if the bag lacks space for any prize (including the clay pot
     * item 32043 in Hương Dũng bag 46285). Cache updates only after atomic persistence.
     */
    /** Character-serialized BAG mutation; coordinates with autosave and staged Treasure commit. */
    /**
     * DVT Change=9 item-only exchange. DB-first multi-slot atomic BAG write;
     * shared character mutex prevents double-spend and races with autosave.
     */
    suspend fun exchangeWelfareItemsAtomic(
        characterId: Long, costs: List<Pair<Int, Int>>,
        output: Pair<Int, Int>, count: Int, recipeDay: Int = 0,
    ): Map<Int, ThingData?>? = CharacterPersistenceGuard.withLock(characterId) {
        val existing = bags[characterId]?.toMap() ?: return@withLock null
        val changes = WelfareItemExchangePlanner.plan(
            existing, roleCountSystem.getBagCapacity(characterId),
            costs, output, count, itemDefs,
        ) ?: return@withLock null
        try {
            // The receipt and inventory changes MUST commit together.
            // Cache is only changed after persistence confirms success.
            inventoryRepo.saveWelfareExchangeAtomic(
                characterId, changes, recipeDay, count, output.first, output.second * count
            )
            val bag = bags[characterId] ?: error("Missing loaded BAG after exchange")
            for ((slot, thing) in changes) {
                if (thing == null || thing.isEmpty) bag.remove(slot)
                else bag[slot] = thing
            }
            changes
        } catch (e: Exception) {
            logger.error(e) { "Welfare BAG and receipt transaction rolled back" }
            null
        }
    }

    /**
     * DVT C087 EAward.Change=9: physical ingredient(s) + virtual gold 65351.
     * MoneySystem obtains CharacterPersistenceGuard + gold writer fence first.
     * BAG cache and money cache update only after their shared SQL commit.
     */
    suspend fun exchangeWelfareGoldItemsAtomic(
        characterId: Long, costs: List<Pair<Int, Int>>,
        output: Pair<Int, Int>, count: Int, recipeDay: Int,
        goldCost: Int, moneySystem: MoneySystem,
    ): Map<Int, ThingData?>? {
        if (goldCost <= 0 || count !in 1..50 ||
            costs.isEmpty() || costs.any { it.first >= 65300 }) return null
        val totalOutput = output.second.toLong() * count
        if (totalOutput !in 1L..Int.MAX_VALUE.toLong()) return null
        var committedBag: Map<Int, ThingData?>? = null
        return try {
            val paid = moneySystem.purchaseHouseExpansionGold(
                characterId, goldCost
            ) { expectedGold, expectedAuctionGold ->
                val existing = bags[characterId]?.toMap() ?: return@purchaseHouseExpansionGold false
                val changes = WelfareItemExchangePlanner.plan(
                    existing, roleCountSystem.getBagCapacity(characterId),
                    costs, output, count, itemDefs,
                ) ?: return@purchaseHouseExpansionGold false
                inventoryRepo.saveWelfareGoldExchangeAtomic(
                    characterId, changes, recipeDay, count, output.first,
                    totalOutput.toInt(), expectedGold, expectedAuctionGold, goldCost,
                )
                val bag = bags[characterId] ?: error("BAG cache missing after gold exchange")
                for ((slot, thing) in changes) {
                    if (thing == null || thing.isEmpty) bag.remove(slot)
                    else bag[slot] = thing
                }
                committedBag = changes
                true
            }
            if (paid) committedBag else null
        } catch (e: Exception) {
            logger.error(e) { "Welfare gold+BAG+receipt SQL transaction failed" }
            null
        }
    }

    suspend fun openGiftBagAtomic(characterId: Long, sourceSlot: Int, sourceItemId: Int, consumedQty: Int, rewards: List<Pair<Int, Int>>): Map<Int, ThingData?>? =
        CharacterPersistenceGuard.withLock(characterId) { openGiftBagAtomicUnfenced(characterId, sourceSlot, sourceItemId, consumedQty, rewards) }

    private suspend fun openGiftBagAtomicUnfenced(
        characterId: Long,
        sourceSlot: Int,
        sourceItemId: Int,
        consumedQty: Int,
        rewards: List<Pair<Int, Int>>,
    ): Map<Int, ThingData?>? {
        val before = bags[characterId]?.toMap() ?: return null
        val changes = GiftBagAtomicPlanner.plan(
            currentBag = before,
            capacity = roleCountSystem.getBagCapacity(characterId),
            sourceSlot = sourceSlot,
            sourceItemId = sourceItemId,
            consumedQty = consumedQty,
            rewards = rewards,
            itemDefs = itemDefs,
        ) ?: return null
        return try {
            setBagItemsAtomicUnfenced(characterId, changes)
            changes
        } catch (e: Exception) {
            logger.error(e) { "Failed atomic BlissBag exchange characterId=$characterId itemId=$sourceItemId" }
            null
        }
    }

    /** Character-serialized multi-container transfer; fenced with save/logout and Treasure settlement. */
    suspend fun commitBattlePassClaim(characterId: Long, seasonId: Int, changes: Map<Int, ThingData?>, expectedProgress: Map<Int, Int>, nextProgress: Map<Int, Pair<Int, Int>>): Unit =
        CharacterPersistenceGuard.withLock(characterId) { commitBattlePassClaimUnfenced(characterId, seasonId, changes, expectedProgress, nextProgress) }

    private suspend fun commitBattlePassClaimUnfenced(
        characterId: Long, seasonId: Int, changes: Map<Int, ThingData?>,
        expectedProgress: Map<Int, Int>, nextProgress: Map<Int, Pair<Int, Int>>,
    ) {
        val bag = bags[characterId] ?: error("Bag not loaded")
        inventoryRepo.saveBattlePassClaimAtomic(characterId, seasonId, changes, expectedProgress, nextProgress)
        changes.forEach { (slot, thing) ->
            if (thing == null || thing.isEmpty) bag.remove(slot) else bag[slot] = thing
        }
        nextProgress.forEach { (index, pair) -> roleCountSystem.setValue(characterId, index, pair.first, pair.second) }
    }

    suspend fun battlePassProgress(characterId: Long): tw.tsm.domain.data.BattlePassProgress? =
        inventoryRepo.loadBattlePassProgress(characterId)

    // ── Thao tác trang bị ──

    /**
     * Mặc trang bị (chuyển từ túi đồ sang ô trang bị)
     *
     * @return Thành công trả về (fitType, trang bị cũ bị thay thế hoặc null)
     */
    /** Character-serialized multi-container transfer; fenced with save/logout and Treasure settlement. */
    suspend fun equipItem(characterId: Long, bagSlot: Int, playerLevel: Int = 0): Pair<Int, ThingData?>? =
        CharacterPersistenceGuard.withLock(characterId) { equipItemUnfenced(characterId, bagSlot, playerLevel) }

    private suspend fun equipItemUnfenced(characterId: Long, bagSlot: Int, playerLevel: Int = 0): Pair<Int, ThingData?>? {
        val bag = bags[characterId] ?: return null
        val equip = equips[characterId] ?: return null
        val bagItem = bag[bagSlot] ?: return null

        val def = itemDefs[bagItem.itemId] ?: return null
        val fitType = def.fitType
        if (!EquipSlot.isBasicEquip(fitType)) return null

        // KERO: Xác thực giới hạn cấp độ trang bị
        if (def.needLv > 0 && playerLevel > 0 && playerLevel < def.needLv) {
            logger.warn { "Mặc trang bị thất bại: charId=$characterId Cấp độ $playerLevel < Yêu cầu trang bị ${def.needLv}" }
            return null
        }

        // Trang bị dành riêng cho võ tướng thì người chơi không được mặc (Item.lua:1724 restrict bit 128)
        if ((def.restrict and 128) != 0) {
            logger.warn { "Mặc trang bị thất bại: charId=$characterId itemId=${bagItem.itemId} là đồ chuyên dụng cho võ tướng (restrict=${def.restrict})" }
            return null
        }

        val oldEquip = equip[fitType]

        // Đưa trang bị mới vào ô trang bị
        equip[fitType] = bagItem
        inventoryRepo.saveItem(characterId, EThings.EQUIP, fitType, bagItem)

        // Trang bị cũ đưa về túi đồ (hoặc làm trống ô túi đồ)
        if (oldEquip != null) {
            bag[bagSlot] = oldEquip
            inventoryRepo.saveItem(characterId, EThings.BAG, bagSlot, oldEquip)
        } else {
            bag.remove(bagSlot)
            inventoryRepo.saveItem(characterId, EThings.BAG, bagSlot, null)
        }

        return Pair(fitType, oldEquip)
    }

    /**
     * Tháo trang bị (chuyển từ ô trang bị sang túi đồ)
     *
     * @return Thành công trả về ô đích trong túi đồ, nếu túi đầy trả về -1
     */
    /** Character-serialized multi-container transfer; fenced with save/logout and Treasure settlement. */
    suspend fun unequipItem(characterId: Long, fitType: Int): Int =
        CharacterPersistenceGuard.withLock(characterId) { unequipItemUnfenced(characterId, fitType) }

    private suspend fun unequipItemUnfenced(characterId: Long, fitType: Int): Int {
        val bag = bags[characterId] ?: return -1
        val equip = equips[characterId] ?: return -1
        val equipItem = equip[fitType] ?: return -1

        val emptySlot = findEmptyBagSlot(characterId) ?: return -1

        // Chuyển sang túi đồ
        bag[emptySlot] = equipItem
        inventoryRepo.saveItem(characterId, EThings.BAG, emptySlot, equipItem)

        // Xóa khỏi ô trang bị
        equip.remove(fitType)
        inventoryRepo.saveItem(characterId, EThings.EQUIP, fitType, null)

        return emptySlot
    }

    // ── Thao tác tiền trang ──

    /**
     * Gửi vào tiền trang (Túi đồ → Tiền trang), hỗ trợ cộng dồn vào ô đã có vật phẩm cùng loại
     *
     * Logic: Ưu tiên cộng dồn vào các ô trong tiền trang đã có cùng itemId, nếu không đủ chỗ mới xếp vào ô mới.
     * Tương ứng với UIBank.lua:447-474 kiểm tra cộng dồn phía client.
     *
     * @param maxSlots Số ô mở rộng hiện tại của tiền trang (tính toán động theo cấp người chơi)
     * @return Triple(Mã lỗi, Ô đích trong tiền trang, Vật phẩm trong tiền trang). Mã lỗi: 0=Thành công, 3=Túi đồ không có vật phẩm này, 13=Tiền trang đầy
     */
    /** Character-serialized multi-container transfer; fenced with save/logout and Treasure settlement. */
    suspend fun depositToBank(characterId: Long, bagSlot: Int, quantity: Int, maxSlots: Int = ItemConstants.INIT_BANK_SLOTS): Triple<Int, Int, ThingData?> =
        CharacterPersistenceGuard.withLock(characterId) { depositToBankUnfenced(characterId, bagSlot, quantity, maxSlots) }

    private suspend fun depositToBankUnfenced(
        characterId: Long, bagSlot: Int, quantity: Int,
        maxSlots: Int = ItemConstants.INIT_BANK_SLOTS
    ): Triple<Int, Int, ThingData?> {
        val bag = bags[characterId] ?: return Triple(BankResult.BAG_FULL, -1, null)
        val bank = banks[characterId] ?: return Triple(BankResult.BAG_FULL, -1, null)
        val bagItem = bag[bagSlot] ?: return Triple(BankResult.BAG_FULL, -1, null)

        val depositQty = minOf(quantity, bagItem.quantity)
        val def = itemDefs[bagItem.itemId]
        val isStackable = def != null && def.fitType == 0
        var remaining = depositQty
        var lastBankSlot = -1
        var lastBankThing: ThingData? = null

        // Giai đoạn một: Cộng dồn vào các ô trong tiền trang đã có cùng itemId
        if (isStackable) {
            for ((slot, thing) in bank) {
                if (remaining <= 0) break
                if (thing.itemId == bagItem.itemId && thing.quantity < ItemConstants.MAX_STACK) {
                    val canAdd = minOf(remaining, ItemConstants.MAX_STACK - thing.quantity)
                    val updated = thing.copy(quantity = thing.quantity + canAdd)
                    bank[slot] = updated
                    inventoryRepo.saveItem(characterId, EThings.BANK, slot, updated)
                    lastBankSlot = slot
                    lastBankThing = updated
                    remaining -= canAdd
                }
            }
        }

        // Giai đoạn hai: Phần dư đặt vào ô mới
        if (remaining > 0) {
            val bankSlot = findEmptySlot(bank, maxSlots)
                ?: return if (lastBankSlot >= 0) {
                    // Thành công một phần: Đã cộng dồn được một ít, khấu trừ túi đồ
                    deductFromBag(characterId, bag, bagSlot, bagItem, depositQty - remaining)
                    Triple(BankResult.SUCCESS, lastBankSlot, lastBankThing)
                } else {
                    Triple(BankResult.BANK_FULL, -1, null)
                }
            val newThing = bagItem.copy(quantity = remaining)
            bank[bankSlot] = newThing
            inventoryRepo.saveItem(characterId, EThings.BANK, bankSlot, newThing)
            lastBankSlot = bankSlot
            lastBankThing = newThing
        }

        // Khấu trừ túi đồ
        deductFromBag(characterId, bag, bagSlot, bagItem, depositQty)
        return Triple(BankResult.SUCCESS, lastBankSlot, lastBankThing)
    }

    /**
     * Rút từ tiền trang (Tiền trang → Túi đồ), hỗ trợ cộng dồn vào ô đã có vật phẩm cùng loại
     *
     * @return Triple(Mã lỗi, Ô đích trong túi đồ, Vật phẩm trong túi đồ). Mã lỗi: 0=Thành công, 3=Túi đồ đầy, 13=Tiền trang không có vật phẩm này
     */
    /** Character-serialized multi-container transfer; fenced with save/logout and Treasure settlement. */
    suspend fun withdrawFromBank(characterId: Long, bankSlot: Int, quantity: Int): Triple<Int, Int, ThingData?> =
        CharacterPersistenceGuard.withLock(characterId) { withdrawFromBankUnfenced(characterId, bankSlot, quantity) }

    private suspend fun withdrawFromBankUnfenced(characterId: Long, bankSlot: Int, quantity: Int): Triple<Int, Int, ThingData?> {
        val bag = bags[characterId] ?: return Triple(BankResult.BAG_FULL, -1, null)
        val bank = banks[characterId] ?: return Triple(BankResult.BANK_FULL, -1, null)
        val bankItem = bank[bankSlot] ?: return Triple(BankResult.BANK_FULL, -1, null)

        val withdrawQty = minOf(quantity, bankItem.quantity)
        val def = itemDefs[bankItem.itemId]
        val isStackable = def != null && def.fitType == 0
        var remaining = withdrawQty
        var lastBagSlot = -1
        var lastBagThing: ThingData? = null

        // Giai đoạn một: Cộng dồn vào các ô trong túi đồ đã có cùng itemId
        if (isStackable) {
            for ((slot, thing) in bag) {
                if (remaining <= 0) break
                if (thing.itemId == bankItem.itemId && thing.quantity < ItemConstants.MAX_STACK) {
                    val canAdd = minOf(remaining, ItemConstants.MAX_STACK - thing.quantity)
                    val updated = thing.copy(quantity = thing.quantity + canAdd)
                    bag[slot] = updated
                    inventoryRepo.saveItem(characterId, EThings.BAG, slot, updated)
                    lastBagSlot = slot
                    lastBagThing = updated
                    remaining -= canAdd
                }
            }
        }

        // Giai đoạn hai: Phần dư đặt vào ô túi đồ mới
        if (remaining > 0) {
            val emptySlot = findEmptyBagSlot(characterId)
                ?: return if (lastBagSlot >= 0) {
                    // Thành công một phần
                    deductFromBank(characterId, bank, bankSlot, bankItem, withdrawQty - remaining)
                    Triple(BankResult.SUCCESS, lastBagSlot, lastBagThing)
                } else {
                    Triple(BankResult.BAG_FULL, -1, null)
                }
            val newThing = bankItem.copy(quantity = remaining)
            bag[emptySlot] = newThing
            inventoryRepo.saveItem(characterId, EThings.BAG, emptySlot, newThing)
            lastBagSlot = emptySlot
            lastBagThing = newThing
        }

        // Khấu trừ tiền trang
        deductFromBank(characterId, bank, bankSlot, bankItem, withdrawQty)
        return Triple(BankResult.SUCCESS, lastBagSlot, lastBagThing)
    }

    // ── Thao tác bảo khố ──

    /**
     * Gửi vào bảo khố (Túi đồ → Bảo khố), hỗ trợ cộng dồn
     *
     * Giới hạn bổ sung của bảo khố (UIBank.lua:229-237):
     * - Cấm gửi vào nếu restrict bit 32
     * - Cấm gửi vào nếu là trang bị (fitType > 0)
     * - Cấm gửi vào nếu vật phẩm có deleteTime
     *
     * @param maxSlots Số ô mở rộng hiện tại của bảo khố
     * @return Triple(Mã lỗi, Ô đích trong bảo khố, Vật phẩm trong bảo khố)
     */
    /** Character-serialized multi-container transfer; fenced with save/logout and Treasure settlement. */
    suspend fun depositToStorage(characterId: Long, bagSlot: Int, quantity: Int, maxSlots: Int = ItemConstants.INIT_STORAGE_SLOTS): Triple<Int, Int, ThingData?> =
        CharacterPersistenceGuard.withLock(characterId) { depositToStorageUnfenced(characterId, bagSlot, quantity, maxSlots) }

    private suspend fun depositToStorageUnfenced(
        characterId: Long, bagSlot: Int, quantity: Int,
        maxSlots: Int = ItemConstants.INIT_STORAGE_SLOTS
    ): Triple<Int, Int, ThingData?> {
        val bag = bags[characterId] ?: return Triple(BankResult.BAG_FULL, -1, null)
        val storage = storages[characterId] ?: return Triple(BankResult.BAG_FULL, -1, null)
        val bagItem = bag[bagSlot] ?: return Triple(BankResult.BAG_FULL, -1, null)

        // Xác thực giới hạn bảo khố
        val storageCheck = canDepositToStorage(bagItem)
        if (storageCheck != BankResult.SUCCESS) return Triple(storageCheck, -1, null)

        val depositQty = minOf(quantity, bagItem.quantity)
        val def = itemDefs[bagItem.itemId]
        val isStackable = def != null && def.fitType == 0
        var remaining = depositQty
        var lastSlot = -1
        var lastThing: ThingData? = null

        // Giai đoạn một: Cộng dồn vào các ô trong bảo khố đã có cùng itemId
        if (isStackable) {
            for ((slot, thing) in storage) {
                if (remaining <= 0) break
                if (thing.itemId == bagItem.itemId && thing.quantity < ItemConstants.MAX_STACK) {
                    val canAdd = minOf(remaining, ItemConstants.MAX_STACK - thing.quantity)
                    val updated = thing.copy(quantity = thing.quantity + canAdd)
                    storage[slot] = updated
                    inventoryRepo.saveItem(characterId, EThings.STORAGE, slot, updated)
                    lastSlot = slot
                    lastThing = updated
                    remaining -= canAdd
                }
            }
        }

        // Giai đoạn hai: Phần dư đặt vào ô mới
        if (remaining > 0) {
            val emptySlot = findEmptySlot(storage, maxSlots)
                ?: return if (lastSlot >= 0) {
                    deductFromBag(characterId, bag, bagSlot, bagItem, depositQty - remaining)
                    Triple(BankResult.SUCCESS, lastSlot, lastThing)
                } else {
                    Triple(BankResult.BANK_FULL, -1, null)
                }
            val newThing = bagItem.copy(quantity = remaining)
            storage[emptySlot] = newThing
            inventoryRepo.saveItem(characterId, EThings.STORAGE, emptySlot, newThing)
            lastSlot = emptySlot
            lastThing = newThing
        }

        // Khấu trừ túi đồ
        deductFromBag(characterId, bag, bagSlot, bagItem, depositQty)
        return Triple(BankResult.SUCCESS, lastSlot, lastThing)
    }

    /**
     * Rút từ bảo khố (Bảo khố → Túi đồ), hỗ trợ cộng dồn
     *
     * @return Triple(Mã lỗi, Ô đích trong túi đồ, Vật phẩm trong túi đồ)
     */
    /** Character-serialized multi-container transfer; fenced with save/logout and Treasure settlement. */
    suspend fun withdrawFromStorage(characterId: Long, storageSlot: Int, quantity: Int): Triple<Int, Int, ThingData?> =
        CharacterPersistenceGuard.withLock(characterId) { withdrawFromStorageUnfenced(characterId, storageSlot, quantity) }

    private suspend fun withdrawFromStorageUnfenced(characterId: Long, storageSlot: Int, quantity: Int): Triple<Int, Int, ThingData?> {
        val bag = bags[characterId] ?: return Triple(BankResult.BAG_FULL, -1, null)
        val storage = storages[characterId] ?: return Triple(BankResult.BANK_FULL, -1, null)
        val storageItem = storage[storageSlot] ?: return Triple(BankResult.BANK_FULL, -1, null)

        val withdrawQty = minOf(quantity, storageItem.quantity)
        val def = itemDefs[storageItem.itemId]
        val isStackable = def != null && def.fitType == 0
        var remaining = withdrawQty
        var lastBagSlot = -1
        var lastBagThing: ThingData? = null

        // Giai đoạn một: Cộng dồn vào các ô trong túi đồ đã có cùng itemId
        if (isStackable) {
            for ((slot, thing) in bag) {
                if (remaining <= 0) break
                if (thing.itemId == storageItem.itemId && thing.quantity < ItemConstants.MAX_STACK) {
                    val canAdd = minOf(remaining, ItemConstants.MAX_STACK - thing.quantity)
                    val updated = thing.copy(quantity = thing.quantity + canAdd)
                    bag[slot] = updated
                    inventoryRepo.saveItem(characterId, EThings.BAG, slot, updated)
                    lastBagSlot = slot
                    lastBagThing = updated
                    remaining -= canAdd
                }
            }
        }

        // Giai đoạn hai: Phần dư đặt vào ô túi đồ mới
        if (remaining > 0) {
            val emptySlot = findEmptyBagSlot(characterId)
                ?: return if (lastBagSlot >= 0) {
                    deductFromStorage(characterId, storage, storageSlot, storageItem, withdrawQty - remaining)
                    Triple(BankResult.SUCCESS, lastBagSlot, lastBagThing)
                } else {
                    Triple(BankResult.BAG_FULL, -1, null)
                }
            val newThing = storageItem.copy(quantity = remaining)
            bag[emptySlot] = newThing
            inventoryRepo.saveItem(characterId, EThings.BAG, emptySlot, newThing)
            lastBagSlot = emptySlot
            lastBagThing = newThing
        }

        // Khấu trừ bảo khố
        deductFromStorage(characterId, storage, storageSlot, storageItem, withdrawQty)
        return Triple(BankResult.SUCCESS, lastBagSlot, lastBagThing)
    }

    // ── Xác thực giới hạn ──

    /**
     * Kiểm tra xem vật phẩm có thể gửi vào tiền trang hay không
     *
     * Tương ứng với UIBank.lua:165 — cấm gửi vào nếu restrict bit 32
     */
    fun canDepositToBank(itemId: Int): Boolean {
        val def = itemDefs[itemId] ?: return false
        return (def.restrict and 32) == 0
    }

    /**
     * Kiểm tra xem vật phẩm có thể gửi vào bảo khố hay không
     *
     * Tương ứng với UIBank.lua:229-237
     * - Cấm nếu restrict bit 32
     * - Cấm nếu là trang bị (fitType > 0)
     * - Cấm nếu vật phẩm có deleteTime
     *
     * @return Mã lỗi BankResult, SUCCESS biểu thị có thể gửi vào
     */
    fun canDepositToStorage(thing: ThingData): Int {
        val def = itemDefs[thing.itemId] ?: return BankResult.BAG_FULL
        if ((def.restrict and 32) != 0) return BankResult.BAG_FULL
        if (def.fitType > 0) return BankResult.NOT_STACKABLE
        if (thing.deleteTime > 0.0) return BankResult.NOT_STACKABLE
        return BankResult.SUCCESS
    }

    // ── Thao tác đồng bộ dùng cho giao dịch (chỉ cập nhật bộ nhớ, DB do saveAndUnload xử lý lưu trữ thống nhất) ──

    /**
     * Đồng bộ thiết lập ô túi đồ (không truy cập DB)
     *
     * Dùng cho hệ thống giao dịch thao tác với túi đồ trong khối synchronized.
     * DB sẽ được lưu trữ thống nhất khi nhân vật đăng xuất qua saveAndUnload() hoặc ở thao tác suspend tiếp theo.
     */
    fun setBagItemSync(characterId: Long, slot: Int, thing: ThingData?) {
        val bag = bags[characterId] ?: return
        if (thing == null || thing.isEmpty) {
            bag.remove(slot)
        } else {
            bag[slot] = thing
        }
    }

    /**
     * Đồng bộ thêm toàn bộ vật phẩm vào túi đồ (không truy cập DB)
     *
     * Tìm ô trống đầu tiên để thêm vào, dùng cho hệ thống giao dịch.
     *
     * @return Trả về vị trí ô thêm thành công, nếu túi đầy trả về -1
     */
    fun addItemDirectSync(characterId: Long, thing: ThingData): Int {
        val bag = bags[characterId] ?: return -1
        for (i in 0 until roleCountSystem.getBagCapacity(characterId)) {
            if (!bag.containsKey(i)) {
                bag[i] = thing
                return i
            }
        }
        return -1
    }

    // ── Hỗ trợ ──

    /** Tìm ô trống đầu tiên trong túi đồ (hệ cơ số 0 / 0-based, giới hạn = số ô mở rộng động) */
    fun findEmptyBagSlot(characterId: Long): Int? {
        val bag = bags[characterId] ?: return null
        for (i in 0 until roleCountSystem.getBagCapacity(characterId)) {
            if (!bag.containsKey(i)) return i
        }
        return null
    }

    /**
     * Tìm ô trống đầu tiên trong bộ chứa được chỉ định
     *
     * @param container Bộ chứa (Tiền trang/Bảo khố)
     * @param maxSlots Giới hạn số ô mở tối đa hiện tại
     */
    private fun findEmptySlot(container: ConcurrentHashMap<Int, ThingData>, maxSlots: Int): Int? {
        for (i in 0 until maxSlots) {
            if (!container.containsKey(i)) return i
        }
        return null
    }

    /** Khấu trừ vật phẩm trong túi đồ (logic dùng chung) */
    private suspend fun deductFromBag(
        characterId: Long,
        bag: ConcurrentHashMap<Int, ThingData>,
        slot: Int, item: ThingData, amount: Int
    ) {
        if (item.quantity <= amount) {
            bag.remove(slot)
            inventoryRepo.saveItem(characterId, EThings.BAG, slot, null)
        } else {
            val updated = item.copy(quantity = item.quantity - amount)
            bag[slot] = updated
            inventoryRepo.saveItem(characterId, EThings.BAG, slot, updated)
        }
    }

    /** Khấu trừ vật phẩm trong tiền trang (logic dùng chung) */
    private suspend fun deductFromBank(
        characterId: Long,
        bank: ConcurrentHashMap<Int, ThingData>,
        slot: Int, item: ThingData, amount: Int
    ) {
        if (item.quantity <= amount) {
            bank.remove(slot)
            inventoryRepo.saveItem(characterId, EThings.BANK, slot, null)
        } else {
            val updated = item.copy(quantity = item.quantity - amount)
            bank[slot] = updated
            inventoryRepo.saveItem(characterId, EThings.BANK, slot, updated)
        }
    }

    /** Khấu trừ vật phẩm trong bảo khố (logic dùng chung) */
    private suspend fun deductFromStorage(
        characterId: Long,
        storage: ConcurrentHashMap<Int, ThingData>,
        slot: Int, item: ThingData, amount: Int
    ) {
        if (item.quantity <= amount) {
            storage.remove(slot)
            inventoryRepo.saveItem(characterId, EThings.STORAGE, slot, null)
        } else {
            val updated = item.copy(quantity = item.quantity - amount)
            storage[slot] = updated
            inventoryRepo.saveItem(characterId, EThings.STORAGE, slot, updated)
        }
    }

    companion object {
        private const val NPC_SECOND_REINCARNATION_ITEM_KIND = 51
        private const val NPC_SECOND_REINCARNATION_ATTR_KIND = 67
    }
}