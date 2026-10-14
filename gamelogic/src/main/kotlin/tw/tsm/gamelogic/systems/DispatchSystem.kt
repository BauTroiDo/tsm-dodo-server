package tw.tsm.gamelogic.systems

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap
import tw.tsm.domain.constants.DispatchCompleteResult
import tw.tsm.domain.constants.DispatchStartResult
import tw.tsm.domain.data.CommonAwardEntry
import tw.tsm.domain.data.DispatchBonusDef
import tw.tsm.domain.data.DispatchDailyUsage
import tw.tsm.domain.data.DispatchDef
import tw.tsm.domain.data.DispatchSaveData
import tw.tsm.domain.data.LuckyBagDef
import tw.tsm.domain.data.NpcDef
import tw.tsm.domain.interfaces.repositories.DispatchRepository
import tw.tsm.domain.valueobjects.ThingData

private val logger = KotlinLogging.logger {}

/**
 * Hệ thống phái khiển — Quản lý nhiệm vụ phái khiển võ tướng ở khách sạn
 *
 * Nguồn: Dispatch.lua
 * Tối đa 4 thanh tiến trình diễn ra đồng thời (MaxSliderCount=4)
 */
class DispatchSystem(
    private val dispatchRepository: DispatchRepository,
    private val petSystem: PetSystem,
    private val dispatchDefs: Map<Int, Map<Int, List<DispatchDef>>>,
    private val dispatchBonusDefs: Map<Int, DispatchBonusDef>,
    private val commonAwardDefs: Map<Int, List<CommonAwardEntry>>,
    private val luckyBagDefs: Map<Int, LuckyBagDef>,
    private val inventorySystem: InventorySystem,
    private val moneySystem: MoneySystem,
    private val pointSystem: PointSystem,
    private val roleCountSystem: RoleCountSystem,
    private val npcDefs: Map<Int, NpcDef> = emptyMap(),
) {
    private val characterLocks = ConcurrentHashMap<Long, Mutex>()

    companion object {
        /** Số lượng thanh tiến trình tối đa (Dispatch.lua MaxSilderCount=4). */
        const val MAX_SLIDER_COUNT = 4

        /** Rule offline đã chốt: 4 lượt đầu mỗi ngày miễn phí. */
        const val FREE_DAILY_USES = 4
        /** Lượt 5..8: bốn lượt trả tiền game. */
        const val GOLD_EXTRA_USES = 4
        const val GOLD_EXTRA_COST = 5_000
        /** Từ lượt thứ 9: 50 KNB/lượt. */
        const val POINT_EXTRA_COST = 50
        /** BuyDispatch cần một max > mọi nhu cầu thực tế để client tiếp tục cho mua. */
        const val BUY_DISPATCH_MAX = Int.MAX_VALUE

        /**
         * 4 giờ/lượt: fallback offline dựa trên tài liệu lịch sử của game.
         * Chưa tìm thấy cấu hình server chính thức trong Lua/data DVT nên không ghi là
         * giá trị server gốc.
         */
        const val DEFAULT_DISPATCH_DURATION_SECONDS = 4.0 * 60.0 * 60.0

        /** Giá trị của một giây trong định dạng OLE Automation Date. */
        private const val OA_SECOND = 1.0 / 86400.0

        const val QUICK_COMPLETE_SUCCESS = 0
        const val QUICK_COMPLETE_INVALID = 1
        const val QUICK_COMPLETE_NOT_ENOUGH_POINT = 2
        const val QUICK_COMPLETE_PERSIST_FAILED = 3

        private val GAME_ZONE: ZoneId = ZoneId.of("Asia/Taipei")

        fun currentDayKey(): Int {
            val d = LocalDate.now(GAME_ZONE)
            return d.year * 10000 + d.monthValue * 100 + d.dayOfMonth
        }
    }

    private data class DispatchFee(
        val gold: Int = 0,
        val points: Int = 0,
    ) {
        val isFree: Boolean get() = gold == 0 && points == 0
    }

    private fun feeFor(usage: DispatchDailyUsage): DispatchFee = when {
        usage.freeUsed < FREE_DAILY_USES -> DispatchFee()
        usage.paidUsed < GOLD_EXTRA_USES -> DispatchFee(gold = GOLD_EXTRA_COST)
        else -> DispatchFee(points = POINT_EXTRA_COST)
    }

    private fun applyUsageToRoleCount(characterId: Long, usage: DispatchDailyUsage) {
        roleCountSystem.setValue(
            characterId,
            RoleCountSystem.DISPATCH,
            usage.freeUsed.coerceIn(0, FREE_DAILY_USES),
            FREE_DAILY_USES,
        )
        roleCountSystem.setValue(
            characterId,
            RoleCountSystem.BUY_DISPATCH,
            usage.paidUsed.coerceAtLeast(0),
            BUY_DISPATCH_MAX,
        )
    }

    private suspend fun loadDailyUsageUnlocked(
        characterId: Long,
        dayKey: Int,
    ): DispatchDailyUsage {
        val stored = dispatchRepository.getDailyUsage(characterId)
        val usage = if (stored == null || stored.dayKey != dayKey) {
            DispatchDailyUsage(dayKey, 0, 0).also {
                dispatchRepository.saveDailyUsage(characterId, it)
            }
        } else {
            stored.copy(
                freeUsed = stored.freeUsed.coerceIn(0, FREE_DAILY_USES),
                paidUsed = stored.paidUsed.coerceAtLeast(0),
            )
        }
        applyUsageToRoleCount(characterId, usage)
        return usage
    }

    /** Sync quota ngày và RoleCount 8/9 trước khi mở UIDispatch. */
    suspend fun syncDailyUsage(
        characterId: Long,
        dayKey: Int = currentDayKey(),
    ): DispatchDailyUsage =
        characterLocks.computeIfAbsent(characterId) { Mutex() }.withLock {
            val usage = loadDailyUsageUnlocked(characterId, dayKey)
            roleCountSystem.saveCounts(characterId)
            usage
        }

    fun getDispatchRoleCountSnapshot(characterId: Long): Map<Int, Pair<Int, Int>> =
        mapOf(
            RoleCountSystem.DISPATCH to (
                roleCountSystem.getValue(characterId, RoleCountSystem.DISPATCH) to
                    roleCountSystem.getMaxValue(characterId, RoleCountSystem.DISPATCH)
            ),
            RoleCountSystem.BUY_DISPATCH to (
                roleCountSystem.getValue(characterId, RoleCountSystem.BUY_DISPATCH) to
                    roleCountSystem.getMaxValue(characterId, RoleCountSystem.BUY_DISPATCH)
            ),
        )

    /**
     * Truy vấn tất cả các nhiệm vụ phái khiển đang diễn ra của nhân vật
     */
    suspend fun queryDispatches(characterId: Long): List<DispatchSaveData> {
        return dispatchRepository.findByCharacterId(characterId).filter { it.slotIndex in 1..MAX_SLIDER_COUNT }
    }

    /**
     * Hoàn thành nhanh một slot Điều phái đang chạy bằng KNB.
     *
     * UIDispatch truyền slot index qua UISell sellId=9. Toàn bộ validate + trừ KNB
     * + persist endTime dùng cùng mutex với start/claim để double-click không thể
     * trừ tiền hai lần cho cùng một slot.
     */
    suspend fun quickCompleteDispatch(
        characterId: Long,
        slotIndex: Int,
        currentTime: Double,
        pointCost: Int,
    ): Pair<Int, DispatchSaveData?> =
        characterLocks.computeIfAbsent(characterId) { Mutex() }.withLock {
            if (slotIndex !in 1..MAX_SLIDER_COUNT ||
                !currentTime.isFinite() ||
                pointCost <= 0
            ) {
                return@withLock QUICK_COMPLETE_INVALID to null
            }

            val current = dispatchRepository.findByCharacterId(characterId)
                .firstOrNull { it.slotIndex == slotIndex }
                ?: return@withLock QUICK_COMPLETE_INVALID to null

            if (current.innPetIndex <= 0 || currentTime >= current.endTime) {
                return@withLock QUICK_COMPLETE_INVALID to current
            }

            if (!pointSystem.subtractRealPoint(characterId, pointCost)) {
                return@withLock QUICK_COMPLETE_NOT_ENOUGH_POINT to current
            }

            val updated = current.copy(endTime = currentTime)
            try {
                dispatchRepository.deleteBySlot(characterId, slotIndex)
                dispatchRepository.insert(characterId, updated)
                pointSystem.savePoints(characterId)
            } catch (e: Exception) {
                logger.error(e) {
                    "Hoàn thành nhanh Điều phái thất bại, rollback: charId=$characterId slot=$slotIndex"
                }
                try {
                    dispatchRepository.deleteBySlot(characterId, slotIndex)
                    dispatchRepository.insert(characterId, current)
                } catch (rollbackDispatchError: Exception) {
                    logger.error(rollbackDispatchError) {
                        "Rollback slot Điều phái nhanh thất bại: charId=$characterId slot=$slotIndex"
                    }
                }

                pointSystem.addRealPoint(characterId, pointCost)
                try {
                    pointSystem.savePoints(characterId)
                } catch (rollbackPointError: Exception) {
                    logger.error(rollbackPointError) {
                        "Rollback KNB Điều phái nhanh thất bại: charId=$characterId"
                    }
                }
                return@withLock QUICK_COMPLETE_PERSIST_FAILED to null
            }

            logger.info {
                "Hoàn thành nhanh Điều phái: charId=$characterId slot=$slotIndex cost=$pointCost"
            }
            QUICK_COMPLETE_SUCCESS to updated
        }

    /** Inn mutations and dispatch starts share the same lock, including after relog. */
    suspend fun <T> withAvailableInnPet(characterId: Long, innSlot: Int, action: () -> T): T? =
        characterLocks.computeIfAbsent(characterId) { Mutex() }.withLock {
            if (dispatchRepository.findByCharacterId(characterId).any {
                    it.slotIndex in 1..MAX_SLIDER_COUNT && it.innPetIndex == innSlot
                }) return@withLock null
            action()
        }

    fun getInnPets(characterId: Long) = petSystem.getInnPets(characterId)

    /** Slot zero stores the server offer; it is never sent as a progress bar. */
    suspend fun queryOffer(characterId: Long): DispatchSaveData =
        characterLocks.computeIfAbsent(characterId) { Mutex() }.withLock {
            getOrCreateOffer(characterId, dispatchRepository.findByCharacterId(characterId))
        }

    private suspend fun getOrCreateOffer(characterId: Long, records: List<DispatchSaveData>): DispatchSaveData {
        records.firstOrNull { it.slotIndex == 0 && it.dispatchKind in dispatchDefs[1].orEmpty() }?.let { return it }
        val busyKinds = records.filter { it.slotIndex > 0 }.map { it.dispatchKind }.toSet()
        val kind = dispatchDefs[1].orEmpty().keys.firstOrNull { it !in busyKinds } ?: 0
        val (effect1, effect2) = randomEffectIndices()
        val offer = DispatchSaveData(0, 0.0, 0.0, kind, 0, effect1, effect2)
        dispatchRepository.deleteBySlot(characterId, 0)
        dispatchRepository.insert(characterId, offer)
        return offer
    }

    /**
     * Bắt đầu phái khiển võ tướng
     *
     * @param characterId ID nhân vật
     * @param innPetIndex Chỉ số (index) võ tướng trong khách sạn
     * @param currentTime Thời gian hiện tại của máy chủ (Định dạng OLE Automation Date)
     * @param maxSlots Số lượng ô phái khiển tối đa
     * @param effectIndex1 Chỉ số hiệu ứng 1
     * @param effectIndex2 Chỉ số hiệu ứng 2
     * @return Pair<Mã kết quả, Dữ liệu phái khiển mới tạo (khi thành công)>
     */
    suspend fun startDispatch(
        characterId: Long,
        innPetIndex: Int,
        currentTime: Double,
        maxSlots: Int = MAX_SLIDER_COUNT,
        effectIndex1: Int = 0,
        effectIndex2: Int = 0,
        dayKey: Int = currentDayKey(),
    ): Pair<Int, DispatchSaveData?> = characterLocks.computeIfAbsent(characterId) { Mutex() }.withLock {
        if (innPetIndex !in 1..255 || !currentTime.isFinite()) {
            return@withLock DispatchStartResult.INVALID_NPC_INDEX to null
        }

        val allRecords = dispatchRepository.findByCharacterId(characterId)
        val currentDispatches = allRecords.filter { it.slotIndex in 1..MAX_SLIDER_COUNT }

        val effectiveMax = maxSlots.coerceIn(0, MAX_SLIDER_COUNT)
        if (currentDispatches.size >= effectiveMax) {
            return@withLock DispatchStartResult.SLOT_FULL to null
        }

        val innNpc = petSystem.getInnNpc(characterId, innPetIndex)
            ?: return@withLock DispatchStartResult.INVALID_NPC_INDEX to null

        if (currentDispatches.any { it.innPetIndex == innPetIndex }) {
            return@withLock DispatchStartResult.NPC_BUSY to null
        }

        val usedSlots = currentDispatches.map { it.slotIndex }.toSet()
        val emptySlot = (1..effectiveMax).firstOrNull { it !in usedSlots }
            ?: return@withLock DispatchStartResult.SLOT_FULL to null

        val offer = getOrCreateOffer(characterId, allRecords)
        val dispatchKind = offer.dispatchKind
        if (dispatchKind !in dispatchDefs[1].orEmpty()) {
            return@withLock DispatchStartResult.INVALID_KIND to null
        }
        if (currentDispatches.any { it.dispatchKind == dispatchKind }) {
            return@withLock DispatchStartResult.KIND_DUPLICATE to null
        }

        // Sync ngày chỉ sau khi toàn bộ điều kiện gameplay đã hợp lệ.
        val oldUsage = loadDailyUsageUnlocked(characterId, dayKey)
        val fee = feeFor(oldUsage)

        val charged = when {
            fee.gold > 0 -> moneySystem.subtractGold(characterId, fee.gold)
            fee.points > 0 -> pointSystem.subtractRealPoint(characterId, fee.points)
            else -> true
        }
        if (!charged) {
            return@withLock DispatchStartResult.COUNT_FULL to null
        }

        val durationOa = DEFAULT_DISPATCH_DURATION_SECONDS * OA_SECOND
        val saveData = DispatchSaveData(
            slotIndex = emptySlot,
            startTime = currentTime,
            endTime = currentTime + durationOa,
            dispatchKind = dispatchKind,
            innPetIndex = innPetIndex,
            effectIndex1 = offer.effectIndex1,
            effectIndex2 = offer.effectIndex2,
        )

        val newUsage = if (oldUsage.freeUsed < FREE_DAILY_USES) {
            oldUsage.copy(freeUsed = oldUsage.freeUsed + 1)
        } else {
            oldUsage.copy(paidUsed = oldUsage.paidUsed + 1)
        }

        val oldDispatchCount = roleCountSystem.getValue(characterId, RoleCountSystem.DISPATCH)
        val oldDispatchMax = roleCountSystem.getMaxValue(characterId, RoleCountSystem.DISPATCH)
        val oldBuyCount = roleCountSystem.getValue(characterId, RoleCountSystem.BUY_DISPATCH)
        val oldBuyMax = roleCountSystem.getMaxValue(characterId, RoleCountSystem.BUY_DISPATCH)
        val oldTotal = roleCountSystem.getValue(characterId, RoleCountSystem.TOTAL_DISPATCH)
        val oldTotalMax = roleCountSystem.getMaxValue(characterId, RoleCountSystem.TOTAL_DISPATCH)

        try {
            dispatchRepository.insert(characterId, saveData)
            dispatchRepository.saveDailyUsage(characterId, newUsage)

            applyUsageToRoleCount(characterId, newUsage)
            roleCountSystem.setValue(
                characterId,
                RoleCountSystem.TOTAL_DISPATCH,
                oldTotal + 1,
                oldTotalMax,
            )
            roleCountSystem.saveCounts(characterId)

            if (fee.gold > 0) moneySystem.saveMoney(characterId)
            if (fee.points > 0) pointSystem.savePoints(characterId)

            // Offer mới không được phép làm hỏng một lượt đã start thành công.
            dispatchRepository.deleteBySlot(characterId, 0)
            try {
                getOrCreateOffer(characterId, currentDispatches + saveData)
            } catch (offerError: Exception) {
                logger.error(offerError) {
                    "Tạo offer Điều phái kế tiếp thất bại: charId=$characterId"
                }
            }
        } catch (e: Exception) {
            logger.error(e) {
                "Persist start Điều phái thất bại, rollback: charId=$characterId slot=$emptySlot"
            }
            try {
                dispatchRepository.deleteBySlot(characterId, emptySlot)
                dispatchRepository.saveDailyUsage(characterId, oldUsage)
            } catch (rollbackDbError: Exception) {
                logger.error(rollbackDbError) {
                    "Rollback DB Điều phái thất bại: charId=$characterId slot=$emptySlot"
                }
            }

            roleCountSystem.setValue(characterId, RoleCountSystem.DISPATCH, oldDispatchCount, oldDispatchMax)
            roleCountSystem.setValue(characterId, RoleCountSystem.BUY_DISPATCH, oldBuyCount, oldBuyMax)
            roleCountSystem.setValue(characterId, RoleCountSystem.TOTAL_DISPATCH, oldTotal, oldTotalMax)
            try {
                roleCountSystem.saveCounts(characterId)
            } catch (rollbackCountError: Exception) {
                logger.error(rollbackCountError) {
                    "Rollback RoleCount Điều phái thất bại: charId=$characterId"
                }
            }

            if (fee.gold > 0) {
                moneySystem.addGold(characterId, fee.gold)
                try { moneySystem.saveMoney(characterId) } catch (_: Exception) {}
            }
            if (fee.points > 0) {
                pointSystem.addRealPoint(characterId, fee.points)
                try { pointSystem.savePoints(characterId) } catch (_: Exception) {}
            }
            return@withLock DispatchStartResult.COUNT_FULL to null
        }

        val template = npcDefs[innNpc.npcId]
        val bonusMatches = DispatchBonusConditionPolicy.matchingEffects(
            dispatchBonusDefs[saveData.effectIndex1], dispatchBonusDefs[saveData.effectIndex2],
            template?.moral ?: -1, template?.element ?: -1,
        )
        logger.info {
            "Bắt đầu Điều phái: charId=$characterId slot=$emptySlot innPet=$innPetIndex " +
                "kind=$dispatchKind freeUsed=${newUsage.freeUsed} paidUsed=${newUsage.paidUsed} " +
                "feeGold=${fee.gold} feePoint=${fee.points} " +
                "bonusIndices=${saveData.effectIndex1},${saveData.effectIndex2} " +
                "conditionMet=${bonusMatches.first},${bonusMatches.second} " +
                "bonusRewardParity=pending"
        }
        return@withLock DispatchStartResult.SUCCESS to saveData
    }

    /**
     * Hoàn thành phái khiển (Nhận phần thưởng)
     *
     * @param characterId ID nhân vật
     * @param slotIndex Chỉ số thanh tiến trình
     * @param currentTime Thời gian hiện tại của máy chủ (Định dạng OLE Automation Date)
     * @return Pair<Mã kết quả, Dữ liệu phái khiển đã hoàn thành (khi thành công)>
     */
    data class CompletionOutcome(
        val resultCode: Int,
        val dispatch: DispatchSaveData? = null,
        val addedItems: List<Pair<Int, ThingData>> = emptyList(),
        val petExpGain: PetSystem.PetExpGain? = null,
    )

    /**
     * API tương thích cũ cho các call site chỉ cần result + dispatch.
     */
    suspend fun completeDispatch(
        characterId: Long,
        slotIndex: Int,
        currentTime: Double,
    ): Pair<Int, DispatchSaveData?> {
        val outcome = completeDispatchWithRewards(characterId, slotIndex, currentTime)
        return outcome.resultCode to outcome.dispatch
    }

    /**
     * Nhận thưởng Điều phái theo dữ liệu DVT.
     *
     * CommonAward kind=1 được cấp trực tiếp. kind=2 mở LuckyBag.
     * IOS_LuckyBag2 của các bag Điều phái không có xác suất/trọng số phân biệt
     * (mọi entry đang có block=1), nên server offline chọn đều giữa các entry
     * hợp lệ cho mỗi lần mở. Không áp DispatchBonus.effectValue vì Lua/data
     * không đủ bằng chứng để xác định công thức effectKind.
     */
    suspend fun completeDispatchWithRewards(
        characterId: Long,
        slotIndex: Int,
        currentTime: Double,
    ): CompletionOutcome =
        characterLocks.computeIfAbsent(characterId) { Mutex() }.withLock {
            val dispatch = dispatchRepository.findByCharacterId(characterId)
                .firstOrNull { it.slotIndex == slotIndex && it.slotIndex in 1..MAX_SLIDER_COUNT }
                ?: return@withLock CompletionOutcome(DispatchCompleteResult.INVALID_KIND)

            if (currentTime < dispatch.endTime) {
                return@withLock CompletionOutcome(
                    DispatchCompleteResult.NOT_FINISHED,
                    dispatch,
                )
            }

            if (dispatch.dispatchKind !in dispatchDefs[1].orEmpty()) {
                return@withLock CompletionOutcome(
                    DispatchCompleteResult.ITEM_DISABLED,
                    dispatch,
                )
            }

            val innNpc = petSystem.getInnNpc(characterId, dispatch.innPetIndex)
                ?: return@withLock CompletionOutcome(
                    DispatchCompleteResult.INVALID_NPC_INDEX,
                    dispatch,
                )

            val awardDef = getAwardDef(dispatch.dispatchKind, innNpc.level)
                ?: return@withLock CompletionOutcome(
                    DispatchCompleteResult.ITEM_DISABLED,
                    dispatch,
                )
            val rewards = resolveBaseRewards(awardDef.awardId)
                ?: return@withLock CompletionOutcome(
                    DispatchCompleteResult.ITEM_DISABLED,
                    dispatch,
                )

            // All-or-nothing bag write: bag full must leave the claim untouched.
            val addedItems = inventorySystem.addItemBundleToBagExact(characterId, rewards)
                ?: return@withLock CompletionOutcome(
                    DispatchCompleteResult.BAG_FULL,
                    dispatch,
                )

            val petGain = petSystem.gainInnDispatchExp(
                characterId,
                dispatch.innPetIndex,
                awardDef.expKind,
                awardDef.exp,
            )

            // Chỉ consume claim sau khi vật phẩm đã được ghi thành công.
            dispatchRepository.deleteBySlot(characterId, slotIndex)

            // EXP đã nằm trong cache pet và được đánh dirty; cố lưu ngay để giảm cửa sổ
            // mất EXP khi server dừng đột ngột. Lỗi save không biến claim thành nhận lại:
            // AutoSaveService vẫn còn cache dirty để thử lại.
            try {
                petSystem.save(characterId)
            } catch (saveError: Exception) {
                logger.error(saveError) {
                    "Lưu EXP Điều phái tức thời thất bại, chờ autosave: charId=$characterId"
                }
            }

            logger.info {
                "Nhận thưởng Điều phái: charId=$characterId slot=$slotIndex " +
                    "kind=${dispatch.dispatchKind} npc=${innNpc.npcId} rewards=$rewards " +
                    "expKind=${awardDef.expKind} exp=${awardDef.exp}"
            }
            CompletionOutcome(
                resultCode = DispatchCompleteResult.SUCCESS,
                dispatch = dispatch,
                addedItems = addedItems,
                petExpGain = petGain,
            )
        }

    private fun resolveBaseRewards(awardId: Int): List<Pair<Int, Int>>? {
        val common = commonAwardDefs[awardId] ?: return null
        val rewards = ArrayList<Pair<Int, Int>>()
        for (entry in common) {
            if (entry.itemId <= 0 || entry.count <= 0) continue
            when (entry.kind) {
                1 -> {
                    if (entry.count > Int.MAX_VALUE) return null
                    rewards += entry.itemId to entry.count.toInt()
                }
                2 -> {
                    if (entry.count > 100) return null
                    val bag = luckyBagDefs[entry.itemId] ?: return null
                    val candidates = bag.entries.filter { it.itemId > 0 && it.count > 0 }
                    if (candidates.isEmpty()) return null

                    // Dispatch bags hiện tại đều block=1. Nếu một data mới mang nhiều
                    // block khác nhau thì chưa đủ contract để suy ra xác suất.
                    if (candidates.map { it.block }.distinct().size > 1) {
                        logger.warn {
                            "LuckyBag Điều phái có block không đồng nhất, chưa biết xác suất: " +
                                "bagId=${entry.itemId}"
                        }
                        return null
                    }

                    repeat(entry.count.toInt()) {
                        val selected = candidates.random()
                        if (selected.count > Int.MAX_VALUE) return null
                        rewards += selected.itemId to selected.count.toInt()
                    }
                }
                else -> return null
            }
        }
        return rewards
    }

    /**
     * Tạo ngẫu nhiên chỉ số hiệu ứng phái khiển (Chọn 2 cái từ DispatchBonusDefs)
     */
    fun randomEffectIndices(): Pair<Int, Int> {
        if (dispatchBonusDefs.isEmpty()) return 0 to 0
        val keys = dispatchBonusDefs.keys.toList()
        val idx1 = if (keys.isNotEmpty()) keys.random() else 0
        val idx2 = if (keys.size > 1) keys.filter { it != idx1 }.randomOrNull() ?: 0 else 0
        return idx1 to idx2
    }

    /**
     * Lấy định nghĩa phần thưởng phái khiển
     *
     * @param dispatchKind Loại phái khiển
     * @param npcLevel Cấp độ võ tướng
     * @return Định nghĩa phần thưởng phù hợp với cấp độ nhất
     */
    fun getAwardDef(dispatchKind: Int, npcLevel: Int): DispatchDef? {
        val tiers = dispatchDefs[1]?.get(dispatchKind) ?: return null
        // Dispatch.lua iterates in data order and uses a strict lower bound.
        var award: DispatchDef? = null
        for (tier in tiers) {
            if (npcLevel <= tier.minLv) break
            award = tier
        }
        return award
    }
}