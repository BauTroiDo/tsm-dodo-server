package tw.tsm.gamelogic.systems

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tw.tsm.domain.data.CompoundRecipeDef
import tw.tsm.domain.data.ItemDef
import tw.tsm.domain.valueobjects.ThingData
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt
import kotlin.random.Random

data class CompoundInput(val bagIndex: Int, val quantity: Int)

sealed class CompoundResult {
    data class Success(
        val consumedBySlot: Map<Int, Int>,
        val resultSlot: Int,
        val result: ThingData,
    ) : CompoundResult()

    data object InvalidRequest : CompoundResult()
    data object BagFull : CompoundResult()
    data object UnsupportedKind : CompoundResult()
}

/**
 * Backend Chế tạo DVT C:023-014.
 *
 * Recipe/material/result-kind come directly from DVT CompoundData. Generic result grade uses the
 * conservative legacy-compatible baseline documented by TS crafting behavior: round the average
 * material level, then allow one grade of improvement when a crafting skill is learned.
 * The original per-skill probability table is still unavailable, so this resolver deliberately
 * avoids inventing percentage weights; it can be replaced later without changing wire/transaction code.
 */
class CompoundSystem(
    private val inventorySystem: InventorySystem,
    private val itemDefs: Map<Int, ItemDef>,
    private val recipes: List<CompoundRecipeDef>,
    private val roleCountSystem: RoleCountSystem,
    private val skillSystem: SkillSystem? = null,
    private val randomIndex: (Int) -> Int = { bound -> Random.nextInt(bound) },
) {
    companion object {
        const val COMPOUND_KIND = 1
        const val FEATHER_KIND = 15
        const val ALCHEMY_SKILL_ID = 14005
        const val FORGING_SKILL_ID = 14011
        const val CREATION_SKILL_ID = 14019
        private const val RESTRICT_NO_COMPOUND = 4
        private val CRAFTING_SKILL_IDS = listOf(ALCHEMY_SKILL_ID, FORGING_SKILL_ID, CREATION_SKILL_ID)
        private val STANDARD_FEATHER_ELEMENTS = 1..4
    }

    private val locks = ConcurrentHashMap<Long, Mutex>()

    suspend fun compound(
        characterId: Long,
        kind: Int,
        inputs: List<CompoundInput>,
    ): CompoundResult = locks.computeIfAbsent(characterId) { Mutex() }.withLock {
        if (kind != COMPOUND_KIND) return@withLock CompoundResult.UnsupportedKind
        if (inputs.size !in 1..3) return@withLock CompoundResult.InvalidRequest
        if (inputs.any { it.bagIndex <= 0 || it.quantity <= 0 }) {
            return@withLock CompoundResult.InvalidRequest
        }

        val bag = inventorySystem.getBag(characterId).toMap()
        val consumed = linkedMapOf<Int, Int>()
        val logicalThings = ArrayList<ThingData>(inputs.size)
        val logicalDefs = ArrayList<ItemDef>(inputs.size)

        for (input in inputs) {
            val thing = bag[input.bagIndex] ?: return@withLock CompoundResult.InvalidRequest
            val def = itemDefs[thing.itemId] ?: return@withLock CompoundResult.InvalidRequest
            if (thing.isLocked || (def.restrict and RESTRICT_NO_COMPOUND) != 0) {
                return@withLock CompoundResult.InvalidRequest
            }
            consumed[input.bagIndex] = (consumed[input.bagIndex] ?: 0) + input.quantity
            logicalThings += thing
            logicalDefs += def
        }

        for ((slot, qty) in consumed) {
            val thing = bag[slot] ?: return@withLock CompoundResult.InvalidRequest
            if (qty <= 0 || qty > thing.quantity) return@withLock CompoundResult.InvalidRequest
        }

        val featherCount = logicalDefs.count { it.kind == FEATHER_KIND }
        if (featherCount > 0) {
            if (featherCount != 3 || inputs.size != 3) return@withLock CompoundResult.InvalidRequest
            return@withLock compoundFeather(characterId, bag, inputs, consumed, logicalDefs)
        }

        val materials = logicalDefs.map { it.material }
        val matched = recipes.filter { it.matches(materials) }
        if (matched.isEmpty()) return@withLock CompoundResult.InvalidRequest

        // DVT/legacy CompoundData recipes are 1:1 or 1:1:1. The client can submit stack counts,
        // therefore all selected slots must contribute the same amount to preserve that ratio.
        if (inputs.map { it.quantity }.distinct().size != 1) {
            return@withLock CompoundResult.InvalidRequest
        }

        return@withLock compoundGeneric(
            characterId = characterId,
            bag = bag,
            inputs = inputs,
            consumed = consumed,
            defs = logicalDefs,
            matched = matched,
        )
    }

    private suspend fun compoundGeneric(
        characterId: Long,
        bag: Map<Int, ThingData>,
        inputs: List<CompoundInput>,
        consumed: Map<Int, Int>,
        defs: List<ItemDef>,
        matched: List<CompoundRecipeDef>,
    ): CompoundResult {
        if (matched.isEmpty() || defs.isEmpty()) return CompoundResult.InvalidRequest
        val resultQty = inputs.firstOrNull()?.quantity ?: return CompoundResult.InvalidRequest
        if (resultQty <= 0) return CompoundResult.InvalidRequest

        // CompoundData can map the same material tuple to multiple output categories.
        // Keep that multiplicity and choose one matching row, rather than collapsing resultKind.
        val recipe = matched[randomIndex(matched.size).floorMod(matched.size)]

        val averageLevel = defs.map { it.level }.average()
        val baseLevel = averageLevel.roundToInt().coerceAtLeast(0)
        val hasCraftingSkill = skillSystem?.getCache(characterId)?.skills
            ?.let { skills -> CRAFTING_SKILL_IDS.any { (skills[it] ?: 0) > 0 } }
            ?: false
        val requestedLevel = baseLevel + if (hasCraftingSkill) 1 else 0

        val craftableCandidates = itemDefs.entries.asSequence()
            .filter { (_, def) ->
                def.kind == recipe.resultKind && (def.restrict and RESTRICT_NO_COMPOUND) == 0
            }
            .toList()
        if (craftableCandidates.isEmpty()) return CompoundResult.InvalidRequest

        // Not every kind has every grade (for example special/consumable kinds). Resolve to the
        // nearest real DVT grade; on an equal-distance tie prefer the lower grade to avoid inventing
        // an upgrade that the data does not contain.
        val availableLevels = craftableCandidates.map { it.value.level }.distinct()
        val resolvedLevel = availableLevels.minWithOrNull(
            compareBy<Int> { kotlin.math.abs(it - requestedLevel) }
                .thenBy { if (it > requestedLevel) 1 else 0 }
                .thenBy { it }
        ) ?: return CompoundResult.InvalidRequest

        val levelCandidates = craftableCandidates.filter { it.value.level == resolvedLevel }
        if (levelCandidates.isEmpty()) return CompoundResult.InvalidRequest
        val resultEntry = levelCandidates[randomIndex(levelCandidates.size).floorMod(levelCandidates.size)]

        val capacity = roleCountSystem.getBagCapacity(characterId)
        val resultSlot = (1..capacity).firstOrNull { it !in bag }
            ?: return CompoundResult.BagFull

        val changes = linkedMapOf<Int, ThingData?>()
        for ((slot, qty) in consumed) {
            val before = bag[slot] ?: return CompoundResult.InvalidRequest
            changes[slot] = if (before.quantity == qty) null
            else before.copy(quantity = before.quantity - qty)
        }
        val resultThing = ThingData(itemId = resultEntry.key, quantity = resultQty)
        changes[resultSlot] = resultThing

        inventorySystem.setBagItemsAtomic(characterId, changes)
        return CompoundResult.Success(
            consumedBySlot = consumed.toMap(),
            resultSlot = resultSlot,
            result = resultThing,
        )
    }

    private suspend fun compoundFeather(
        characterId: Long,
        bag: Map<Int, ThingData>,
        inputs: List<CompoundInput>,
        consumed: Map<Int, Int>,
        defs: List<ItemDef>,
    ): CompoundResult {
        val levels = defs.map { it.level }
        if (levels.distinct().size != 1) return CompoundResult.InvalidRequest

        val quantities = inputs.map { it.quantity }
        if (quantities.distinct().size != 1) return CompoundResult.InvalidRequest
        val resultQty = quantities.first()
        if (resultQty <= 0) return CompoundResult.InvalidRequest

        val elements = defs.map { it.element }
        if (elements.any { it !in STANDARD_FEATHER_ELEMENTS }) {
            return CompoundResult.InvalidRequest
        }
        val chosenElement = elements[randomIndex(elements.size).floorMod(elements.size)]
        val targetLevel = levels.first() + 1

        // DVT ItemData can contain duplicate cosmetic families at the same rank/element.
        // Base synthesis chain is the lowest itemId with the exact kind+rank+element.
        val resultEntry = itemDefs.entries
            .asSequence()
            .filter { (_, def) ->
                def.kind == FEATHER_KIND && def.level == targetLevel && def.element == chosenElement
            }
            .minByOrNull { (itemId, _) -> itemId }
            ?: return CompoundResult.InvalidRequest
        val resultItemId = resultEntry.key

        val capacity = roleCountSystem.getBagCapacity(characterId)
        val resultSlot = (1..capacity).firstOrNull { it !in bag }
            ?: return CompoundResult.BagFull

        val changes = linkedMapOf<Int, ThingData?>()
        for ((slot, qty) in consumed) {
            val before = bag[slot] ?: return CompoundResult.InvalidRequest
            changes[slot] = if (before.quantity == qty) null
            else before.copy(quantity = before.quantity - qty)
        }
        val resultThing = ThingData(itemId = resultItemId, quantity = resultQty)
        changes[resultSlot] = resultThing

        inventorySystem.setBagItemsAtomic(characterId, changes)
        return CompoundResult.Success(
            consumedBySlot = consumed.toMap(),
            resultSlot = resultSlot,
            result = resultThing,
        )
    }

    private fun Int.floorMod(bound: Int): Int {
        if (bound <= 0) return 0
        val mod = this % bound
        return if (mod >= 0) mod else mod + bound
    }
}
