package tw.tsm.gamelogic.systems

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.data.CollectCardDef
import tw.tsm.domain.data.CollectCardSortDef
import tw.tsm.domain.data.CollectNftDef
import tw.tsm.domain.data.CollectStyleDef
import tw.tsm.domain.data.CollectStyleValueDef
import tw.tsm.domain.data.CollectWarriorDef
import tw.tsm.domain.data.MountDef
import tw.tsm.domain.data.CollectionCardState
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.domain.interfaces.SessionSender
import tw.tsm.domain.interfaces.repositories.CollectionBookRepository
import tw.tsm.domain.valueobjects.ThingData
import tw.tsm.domain.util.DvtPercentMath
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val logger = KotlinLogging.logger {}

class CollectionSystem(
    private val bitFlagSystem: BitFlagSystem,
    private val collectWarriorDefs: Map<Int, CollectWarriorDef>,
    private val collectStyleDefs: Map<Int, CollectStyleDef>,
    private val collectStyleValueDefs: List<CollectStyleValueDef>,
    private val collectCardDefs: Map<Int, CollectCardDef>,
    private val collectCardSortDefs: Map<Int, CollectCardSortDef>,
    private val collectNftDefs: Map<Int, CollectNftDef>,
    private val mountDefs: Map<Int, MountDef>,
    private val inventorySystem: InventorySystem,
    private val roleCountSystem: RoleCountSystem,
    private val playerStateManager: PlayerStateManager,
    private val statSystem: StatSystem,
    private val repository: CollectionBookRepository,
    private val responseSender: ResponseSender,
) {
    data class ClaimResult(
        val success: Boolean,
        val bagFull: Boolean = false,
        val addedItems: List<Pair<Int, ThingData>> = emptyList(),
        val rewardFlags: List<Int> = emptyList(),
    )

    data class Snapshot(
        val styleFlags: Map<Int, Int>,
        val cardLevels: Map<Int, Int>,
        val equippedCards: List<Int>,
        val maxEquipCards: Int,
    )

    data class StyleActionResult(
        val success: Boolean,
        val bagFull: Boolean = false,
        val styleId: Int = 0,
        val flag: Int = 0,
        val itemId: Int = 0,
        val removedBagSlot: Int = -1,
        val addedItems: List<Pair<Int, ThingData>> = emptyList(),
    )

    data class CardUpgradeResult(
        val success: Boolean,
        val cardId: Int = 0,
        val level: Int = 0,
        val changedCounts: Map<Int, Pair<Int, Int>> = emptyMap(),
    )

    data class CardEquipResult(
        val success: Boolean,
        val cardId: Int = 0,
    )

    data class NftActionResult(
        val success: Boolean,
        val bagFull: Boolean = false,
        /** DVT response key: card display key or mount NPC ID. */
        val collectionId: Int = 0,
        val getId: Int = 0,
        val flagId: Int = 0,
        val addedItems: List<Pair<Int, ThingData>> = emptyList(),
        val shine: Int = 0,
    )

    // Keep stable lock identities across unload/relog and overlapping packet coroutines.
    private val warriorClaimLocks = ConcurrentHashMap<Long, Mutex>()

    private val styleFlags = ConcurrentHashMap<Long, MutableMap<Int, Int>>()
    private val cardStates = ConcurrentHashMap<Long, MutableMap<Int, CollectionCardState>>()
    private val appliedHpDelta = ConcurrentHashMap<Long, Long>()
    private val appliedSpDelta = ConcurrentHashMap<Long, Int>()
    /** Authoritative NFT shine cached from permanent flags + NFT style storage. */
    private val nftShine = ConcurrentHashMap<Long, Int>()

    suspend fun loadCharacter(charId: Long): Snapshot {
        styleFlags[charId] = ConcurrentHashMap(repository.loadStyleFlags(charId))
        cardStates[charId] = ConcurrentHashMap(repository.loadCardStates(charId))
        syncOwnedCardFlags(charId)
        appliedHpDelta[charId] = 0L
        appliedSpDelta[charId] = 0
        refreshNftShine(charId)
        refreshRuntimeMax(charId)

        val styleItemIds = collectStyleDefs.values.flatMap { it.itemIds }.filter { it > 0 }.toSet()
        val styleBagItems = inventorySystem.getBag(charId).values
            .filter { it.itemId in styleItemIds }
            .map { it.itemId }
            .distinct()
        val ownedCardSorts = collectCardSortDefs.values
            .filter { roleCountSystem.getValue(charId, it.roleCount) > 0 }
            .map { it.id }
        logger.info {
            "Sưu Tập state: charId=$charId styleSaved=${styleFlags[charId]?.size ?: 0} " +
                "styleItemsInBag=$styleBagItems cardSortsOwned=$ownedCardSorts " +
                "combos=${cardStates[charId]?.size ?: 0}"
        }
        return snapshot(charId)
    }

    fun unload(charId: Long) {
        styleFlags.remove(charId)
        cardStates.remove(charId)
        appliedHpDelta.remove(charId)
        appliedSpDelta.remove(charId)
        nftShine.remove(charId)
    }

    fun snapshot(charId: Long): Snapshot {
        val styles = styleFlags[charId]?.toMap() ?: emptyMap()
        val cards = cardStates[charId]?.toMap() ?: emptyMap()
        val maxEquip = roleCountSystem.getValue(
            charId,
            RoleCountSystem.COLLECTION_CARD_EQUIP_MAX,
        ).coerceAtLeast(1)
        return Snapshot(
            styleFlags = styles,
            cardLevels = cards.mapValues { it.value.level },
            equippedCards = cards.values
                .filter { it.level > 0 && it.equipSlot > 0 }
                .sortedBy { it.equipSlot }
                .map { it.cardId },
            maxEquipCards = maxEquip,
        )
    }

    suspend fun markWarriorOwned(
        charId: Long,
        npcId: Int,
        session: SessionSender? = null,
    ): Int? {
        val def = collectWarriorDefs[npcId] ?: return null
        if (def.getFlag <= 0) return null
        val current = bitFlagSystem.getSetFlagIds(charId)
        if (def.getFlag in current) return null

        bitFlagSystem.setFlag(charId, def.getFlag)
        refreshNftAndRuntime(charId)
        if (session != null) {
            responseSender.sendBitFlagChanges(session, listOf(def.getFlag to true))
            responseSender.sendCollectionNftShine(session, getNftShine(charId))
        }
        logger.info { "Sưu Tập nhận võ tướng: charId=$charId npcId=$npcId getFlag=${def.getFlag}" }
        return def.getFlag
    }

    suspend fun syncOwnedWarriors(charId: Long, npcIds: Iterable<Int>): List<Int> {
        val current = bitFlagSystem.getSetFlagIds(charId).toMutableSet()
        val added = mutableListOf<Int>()
        for (npcId in npcIds.distinct()) {
            val flag = collectWarriorDefs[npcId]?.getFlag ?: continue
            if (flag <= 0 || !current.add(flag)) continue
            bitFlagSystem.setFlag(charId, flag)
            added += flag
        }
        if (added.isNotEmpty()) {
            refreshNftAndRuntime(charId)
            logger.info { "Backfill Sưu Tập: charId=$charId thêm ${added.size} getFlag" }
        }
        return added
    }

    suspend fun syncOwnedCardFlags(charId: Long): List<Int> {
        val flags = bitFlagSystem.getSetFlagIds(charId).toMutableSet()
        val added = mutableListOf<Int>()
        for (def in collectCardSortDefs.values) {
            if (roleCountSystem.getValue(charId, def.roleCount) <= 0) continue
            if (def.getFlag <= 0 || !flags.add(def.getFlag)) continue
            bitFlagSystem.setFlag(charId, def.getFlag)
            added += def.getFlag
        }
        if (added.isNotEmpty()) {
            logger.info { "Backfill Thẻ Sưu Tập: charId=$charId thêm ${added.size} getFlag" }
        }
        return added
    }

    suspend fun claimWarriorRewards(charId: Long, npcIds: List<Int>): ClaimResult =
        warriorClaimLocks.computeIfAbsent(charId) { Mutex() }.withLock {
            claimWarriorRewardsLocked(charId, npcIds)
        }

    private suspend fun claimWarriorRewardsLocked(charId: Long, npcIds: List<Int>): ClaimResult {
        if (npcIds.isEmpty() || npcIds.distinct().size != npcIds.size) {
            return ClaimResult(success = false)
        }
        val flags = bitFlagSystem.getSetFlagIds(charId)
        val defs = ArrayList<CollectWarriorDef>(npcIds.size)
        for (npcId in npcIds) {
            val def = collectWarriorDefs[npcId] ?: return ClaimResult(success = false)
            if (def.getFlag <= 0 || def.rewardFlag <= 0 ||
                def.getFlag !in flags || def.rewardFlag in flags
            ) return ClaimResult(success = false)
            defs += def
        }

        val rewards = defs.flatMap { it.awards.filter { (id, qty) -> id > 0 && qty > 0 } }
        val addedItems = inventorySystem.addItemBundleToBagExact(charId, rewards)
            ?: return ClaimResult(success = false, bagFull = true)
        val rewardFlags = defs.map { it.rewardFlag }
        for (flag in rewardFlags) bitFlagSystem.setFlag(charId, flag)
        logger.info { "Sưu Tập nhận thưởng: charId=$charId npcIds=$npcIds rewards=$rewards" }
        return ClaimResult(true, addedItems = addedItems, rewardFlags = rewardFlags)
    }

    suspend fun storeStyle(charId: Long, styleId: Int, part: Int): StyleActionResult {
        val def = collectStyleDefs[styleId] ?: return StyleActionResult(false)
        if (part !in 1..5) return StyleActionResult(false)
        val itemId = def.itemIds[part - 1]
        if (itemId <= 0) return StyleActionResult(false)

        val states = styleFlags.getOrPut(charId) { ConcurrentHashMap() }
        val oldFlag = states[styleId] ?: 0
        val mask = 1 shl (part - 1)
        if ((oldFlag and mask) != 0) return StyleActionResult(false)

        val bagEntry = inventorySystem.getBag(charId).entries
            .firstOrNull { it.value.itemId == itemId && it.value.quantity > 0 }
            ?: return StyleActionResult(false)
        if (!inventorySystem.removeFromBag(charId, bagEntry.key, 1)) {
            return StyleActionResult(false)
        }

        val newFlag = oldFlag or mask
        try {
            repository.saveStyleFlag(charId, styleId, newFlag)
            states[styleId] = newFlag
            refreshNftShine(charId)
            refreshRuntimeMax(charId)
        } catch (e: Exception) {
            inventorySystem.addItemsToBagExact(charId, itemId, 1)
            logger.error(e) { "Rollback cất Tạo hình: charId=$charId style=$styleId part=$part" }
            return StyleActionResult(false)
        }

        return StyleActionResult(
            success = true,
            styleId = styleId,
            flag = newFlag,
            itemId = itemId,
            removedBagSlot = bagEntry.key,
        )
    }

    suspend fun takeStyle(charId: Long, styleId: Int, part: Int): StyleActionResult {
        val def = collectStyleDefs[styleId] ?: return StyleActionResult(false)
        if (part !in 1..5) return StyleActionResult(false)
        val itemId = def.itemIds[part - 1]
        if (itemId <= 0) return StyleActionResult(false)

        val states = styleFlags.getOrPut(charId) { ConcurrentHashMap() }
        val oldFlag = states[styleId] ?: 0
        val mask = 1 shl (part - 1)
        if ((oldFlag and mask) == 0) return StyleActionResult(false)

        val added = inventorySystem.addItemsToBagExact(charId, itemId, 1)
            ?: return StyleActionResult(false, bagFull = true)

        val newFlag = oldFlag and mask.inv()
        try {
            repository.saveStyleFlag(charId, styleId, newFlag)
            if (newFlag == 0) states.remove(styleId) else states[styleId] = newFlag
            refreshNftShine(charId)
            refreshRuntimeMax(charId)
        } catch (e: Exception) {
            val rollbackSlot = added.firstOrNull { it.second.itemId == itemId }?.first
            if (rollbackSlot != null) inventorySystem.removeFromBag(charId, rollbackSlot, 1)
            logger.error(e) { "Rollback lấy Tạo hình: charId=$charId style=$styleId part=$part" }
            return StyleActionResult(false)
        }

        return StyleActionResult(
            success = true,
            styleId = styleId,
            flag = newFlag,
            itemId = itemId,
            addedItems = added,
        )
    }

    suspend fun upgradeCard(charId: Long, cardId: Int): CardUpgradeResult {
        val def = collectCardDefs[cardId] ?: return CardUpgradeResult(false)
        val states = cardStates.getOrPut(charId) { ConcurrentHashMap() }
        val old = states[cardId] ?: CollectionCardState(cardId, 0, 0)
        if (old.level >= def.maxLevel) return CardUpgradeResult(false)

        val need = ((old.level + def.levelUpBase) * def.levelUpMultiplier).coerceAtLeast(1)
        val requirements = linkedMapOf<Int, Int>()
        for (sortId in def.cardIds.filter { it > 0 }) {
            val sort = collectCardSortDefs[sortId] ?: return CardUpgradeResult(false)
            requirements[sort.roleCount] = (requirements[sort.roleCount] ?: 0) + need
        }
        if (requirements.any { (idx, qty) -> roleCountSystem.getValue(charId, idx) < qty }) {
            return CardUpgradeResult(false)
        }

        val before = requirements.mapValues { (idx, _) -> roleCountSystem.getValue(charId, idx) }
        for ((idx, qty) in requirements) {
            roleCountSystem.setValue(charId, idx, before.getValue(idx) - qty)
        }

        val next = old.copy(level = old.level + 1)
        try {
            // Persist consumed counters first. Do not expose/persist the new card level
            // until every required counter has been safely written.
            roleCountSystem.saveCounts(charId)
            repository.saveCardState(charId, next)
            states[cardId] = next
            refreshRuntimeMax(charId)
        } catch (e: Exception) {
            for ((idx, value) in before) roleCountSystem.setValue(charId, idx, value)
            try {
                roleCountSystem.saveCounts(charId)
            } catch (rollbackError: Exception) {
                logger.error(rollbackError) {
                    "Rollback RoleCount nâng Tổ hợp thẻ thất bại: charId=$charId card=$cardId"
                }
            }
            logger.error(e) { "Rollback nâng Tổ hợp thẻ: charId=$charId card=$cardId" }
            return CardUpgradeResult(false)
        }

        val changed = requirements.keys.associateWith {
            roleCountSystem.getValue(charId, it) to roleCountSystem.getMaxValue(charId, it)
        }
        return CardUpgradeResult(true, cardId, next.level, changed)
    }

    suspend fun equipCard(charId: Long, cardId: Int): CardEquipResult {
        val def = collectCardDefs[cardId] ?: return CardEquipResult(false)
        val states = cardStates.getOrPut(charId) { ConcurrentHashMap() }
        val old = states[cardId] ?: return CardEquipResult(false)
        if (old.level <= 0 || old.equipSlot > 0) return CardEquipResult(false)

        val maxEquip = roleCountSystem.getValue(
            charId, RoleCountSystem.COLLECTION_CARD_EQUIP_MAX
        ).coerceAtLeast(1)
        val used = states.values.filter { it.equipSlot > 0 }.map { it.equipSlot }.toSet()
        val free = (1..maxEquip).firstOrNull { it !in used } ?: return CardEquipResult(false)
        val next = old.copy(equipSlot = free)
        repository.saveCardState(charId, next)
        states[def.id] = next
        refreshRuntimeMax(charId)
        return CardEquipResult(true, cardId)
    }

    suspend fun unequipCard(charId: Long, cardId: Int): CardEquipResult {
        val states = cardStates.getOrPut(charId) { ConcurrentHashMap() }
        val old = states[cardId] ?: return CardEquipResult(false)
        if (old.equipSlot <= 0) return CardEquipResult(false)
        val next = old.copy(equipSlot = 0)
        repository.saveCardState(charId, next)
        states[cardId] = next
        refreshRuntimeMax(charId)
        return CardEquipResult(true, cardId)
    }

    fun findNftByGetId(getId: Int, kind: Int? = null): CollectNftDef? =
        collectNftDefs.values.firstOrNull { it.getId == getId && (kind == null || it.kind == kind) }

    fun findNftByCollectionId(collectionId: Int, kind: Int? = null): CollectNftDef? {
        val def = collectNftDefs[collectionId] ?: return null
        return if (kind == null || def.kind == kind) def else null
    }

    fun getNftShine(charId: Long): Int = nftShine[charId] ?: 0

    private fun getNftStyleShine(charId: Long): Int {
        val states = styleFlags[charId] ?: return 0
        var total = 0
        for ((styleId, flag) in states) {
            val def = collectStyleDefs[styleId] ?: continue
            if (def.nft != 1) continue
            var collectedParts = 0
            for (part in 1..5) {
                if (def.itemIds[part - 1] == 0) continue
                if ((flag and (1 shl (part - 1))) != 0) {
                    total += def.itemScores[part - 1]
                    collectedParts++
                }
            }
            if (collectedParts >= 5) total += def.itemScores[5]
        }
        return total
    }

    suspend fun refreshNftShine(charId: Long): Int {
        val flags = bitFlagSystem.getSetFlagIds(charId)
        var total = 0

        for (def in collectWarriorDefs.values) {
            if (def.shineValue > 0 && def.getFlag > 0 && def.getFlag in flags) {
                total += def.shineValue
            }
        }

        total += getNftStyleShine(charId)

        for (def in collectNftDefs.values) {
            if (def.shineValue <= 0) continue
            when (def.kind) {
                1 -> if (def.flag > 0 && def.flag in flags) total += def.shineValue
                2 -> {
                    val mountFlag = mountDefs[def.warriorId]?.flagId ?: 0
                    if (mountFlag > 0 && mountFlag in flags) total += def.shineValue
                }
            }
        }

        nftShine[charId] = total
        return total
    }

    suspend fun refreshNftAndRuntime(charId: Long): Int {
        val shine = refreshNftShine(charId)
        refreshRuntimeMax(charId)
        return shine
    }

    suspend fun putNftCard(charId: Long, getId: Int): NftActionResult {
        val def = findNftByGetId(getId, 1) ?: return NftActionResult(false)
        if (def.flag <= 0) return NftActionResult(false)
        val flags = bitFlagSystem.getSetFlagIds(charId)
        if (def.flag in flags) return NftActionResult(false)
        val bagEntry = inventorySystem.getBag(charId).entries
            .firstOrNull { it.value.itemId == getId && it.value.quantity > 0 }
            ?: return NftActionResult(false)

        bitFlagSystem.setFlag(charId, def.flag)
        if (!inventorySystem.removeFromBag(charId, bagEntry.key, 1)) {
            bitFlagSystem.clearFlag(charId, def.flag)
            return NftActionResult(false)
        }
        val shine = refreshNftAndRuntime(charId)
        return NftActionResult(true, collectionId = def.warriorId, getId = getId, flagId = def.flag, shine = shine)
    }

    suspend fun takeNftCard(charId: Long, getId: Int): NftActionResult {
        val def = findNftByGetId(getId, 1) ?: return NftActionResult(false)
        if (def.canGet <= 0) return NftActionResult(false)
        val flags = bitFlagSystem.getSetFlagIds(charId)
        if (def.flag <= 0 || def.flag !in flags) return NftActionResult(false)
        val added = inventorySystem.addItemsToBagExact(charId, getId, 1)
            ?: return NftActionResult(false, bagFull = true)
        try {
            bitFlagSystem.clearFlag(charId, def.flag)
        } catch (e: Exception) {
            val slot = added.firstOrNull { it.second.itemId == getId }?.first
            if (slot != null) inventorySystem.removeFromBag(charId, slot, 1)
            return NftActionResult(false)
        }
        val shine = refreshNftAndRuntime(charId)
        return NftActionResult(true, collectionId = def.warriorId, getId = getId, flagId = def.flag, addedItems = added, shine = shine)
    }

    suspend fun onNftMountPut(charId: Long, npcId: Int): NftActionResult {
        val def = findNftByCollectionId(npcId, 2) ?: return NftActionResult(false)
        val mountFlag = mountDefs[npcId]?.flagId ?: return NftActionResult(false)
        if (mountFlag !in bitFlagSystem.getSetFlagIds(charId)) return NftActionResult(false)
        val shine = refreshNftAndRuntime(charId)
        return NftActionResult(true, collectionId = npcId, getId = def.getId, flagId = mountFlag, shine = shine)
    }

    suspend fun takeNftMount(charId: Long, npcId: Int): NftActionResult {
        val def = findNftByCollectionId(npcId, 2) ?: return NftActionResult(false)
        if (def.canGet <= 0) return NftActionResult(false)
        val mountFlag = mountDefs[npcId]?.flagId ?: return NftActionResult(false)
        if (mountFlag !in bitFlagSystem.getSetFlagIds(charId)) return NftActionResult(false)
        val added = inventorySystem.addItemsToBagExact(charId, def.getId, 1)
            ?: return NftActionResult(false, bagFull = true)
        try {
            bitFlagSystem.clearFlag(charId, mountFlag)
        } catch (e: Exception) {
            val slot = added.firstOrNull { it.second.itemId == def.getId }?.first
            if (slot != null) inventorySystem.removeFromBag(charId, slot, 1)
            return NftActionResult(false)
        }
        val shine = refreshNftAndRuntime(charId)
        return NftActionResult(true, collectionId = npcId, getId = def.getId, flagId = mountFlag, addedItems = added, shine = shine)
    }

    fun getStyleDataCount(): Int = collectStyleDefs.keys.maxOrNull() ?: 0

    /**
     * DVT Collection changes MaxHP/MaxSP without healing current HP/SP.
     * Apply only the collection-derived delta so unrelated max bonuses remain intact.
     */
    fun refreshRuntimeMax(charId: Long) {
        val state = playerStateManager.getState(charId) ?: return
        val turn3Lv = if (state.turn >= 3) (state.level - 200).coerceAtLeast(0) else 0
        val calcLevel = if (state.turn >= 3) 200 else state.level
        val baseHpx = state.hpx + state.equipHpx +
            (if (state.turn >= 3) turn3Lv.coerceAtMost(70) + state.turn3Hpx else 0) +
            state.astrolabeHpx
        val baseSpx = state.spx + state.equipSpx +
            (if (state.turn >= 3) turn3Lv + state.turn3Spx else 0) +
            state.astrolabeSpx

        val baseHpWithoutCollection = statSystem.calculateMaxHp(
            level = calcLevel,
            hpx = baseHpx,
            equipMaxHp = state.equipMaxHp,
            extraHp = 0,
            turn = state.turn,
            career = state.career,
            turn3Lv = turn3Lv,
        )
        val baseHpWithCollection = statSystem.calculateMaxHp(
            level = calcLevel,
            hpx = baseHpx + getPlayerCollectionAttribute(charId, 31),
            equipMaxHp = state.equipMaxHp,
            extraHp = 0,
            turn = state.turn,
            career = state.career,
            turn3Lv = turn3Lv,
        )
        val hpWithoutCollection =
            DvtPercentMath.applyFinalPercentFloor(baseHpWithoutCollection, state.hpPercent).coerceAtLeast(1L)
        val hpWithCollection =
            DvtPercentMath.applyFinalPercentFloor(baseHpWithCollection, state.hpPercent).coerceAtLeast(1L)
        val spWithoutCollection = statSystem.calculateMaxSp(
            level = calcLevel,
            spx = baseSpx,
            equipMaxSp = state.equipMaxSp,
            extraSp = 0,
            turn = state.turn,
            career = state.career,
            turn3Lv = turn3Lv,
        )
        val spWithCollection = statSystem.calculateMaxSp(
            level = calcLevel,
            spx = baseSpx + getPlayerCollectionAttribute(charId, 32),
            equipMaxSp = state.equipMaxSp,
            extraSp = 0,
            turn = state.turn,
            career = state.career,
            turn3Lv = turn3Lv,
        )

        val newHpDelta = hpWithCollection - hpWithoutCollection
        val newSpDelta = spWithCollection - spWithoutCollection
        val oldHpDelta = appliedHpDelta[charId] ?: 0L
        val oldSpDelta = appliedSpDelta[charId] ?: 0

        playerStateManager.setMaxPreserveCurrent(
            charId,
            state.maxHp - oldHpDelta + newHpDelta,
            state.maxSp - oldSpDelta + newSpDelta,
        )
        appliedHpDelta[charId] = newHpDelta
        appliedSpDelta[charId] = newSpDelta
    }

    fun getStyleScore(charId: Long): Int {
        val states = styleFlags[charId] ?: return 0
        var score = 0
        for ((styleId, flag) in states) {
            val def = collectStyleDefs[styleId] ?: continue
            if (def.nft != 0) continue
            var all = true
            for (part in 1..5) {
                val itemId = def.itemIds[part - 1]
                if (itemId == 0) continue
                if ((flag and (1 shl (part - 1))) != 0) {
                    score += def.itemScores[part - 1]
                } else {
                    all = false
                }
            }
            if (all) score += def.itemScores[5]
        }
        return score
    }

    /**
     * DVT Calculator.lua:
     * - Style + Card bonuses apply to both Player and FollowNpc.
     * - NFT Shine threshold bonuses apply to Player only.
     */
    private fun getBaseCollectionAttribute(charId: Long, kind: Int): Int {
        var total = 0
        val styleScore = getStyleScore(charId)
        for (row in collectStyleValueDefs) {
            if (row.nft != 0 || row.score > styleScore) continue
            total += row.attributes.filter { it.first == kind }.sumOf { it.second }
        }

        val states = cardStates[charId] ?: emptyMap()
        for (state in states.values) {
            if (state.level <= 0 || state.equipSlot <= 0) continue
            val def = collectCardDefs[state.cardId] ?: continue
            total += def.values
                .filter { it.kind == kind }
                .sumOf { it.value + it.grow * (state.level - 1) }
        }
        return total
    }

    fun getFollowerCollectionAttribute(charId: Long, kind: Int): Int =
        getBaseCollectionAttribute(charId, kind)

    fun getPlayerCollectionAttribute(charId: Long, kind: Int): Int {
        var total = getBaseCollectionAttribute(charId, kind)
        val shine = nftShine[charId] ?: 0
        for (row in collectStyleValueDefs) {
            if (row.nft != 1 || row.score > shine) continue
            total += row.attributes.filter { it.first == kind }.sumOf { it.second }
        }
        return total
    }

    fun getWarriorDef(npcId: Int): CollectWarriorDef? = collectWarriorDefs[npcId]
}
