package tw.tsm.gamelogic.systems

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tw.tsm.domain.data.CompoundRecipeDef
import tw.tsm.domain.data.ItemDef
import tw.tsm.domain.valueobjects.ThingData
import java.util.concurrent.ConcurrentHashMap
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
 * Data-backed contract:
 * - recipe kind/material/ratio: IOS_Compound.dat / CompoundData.lua
 * - tối đa thành phẩm: floor(phẩm cấp trung bình có trọng số) + 2
 * - điểm kỹ năng hợp thành càng cao thì xác suất ra phẩm cấp cao càng tăng
 * - input restrict bit 4 = không phải nguyên liệu; output restrict bit 8 = không được hợp thành
 * - client Craft tab loại Pearl/Gem/Remove/Ice/Item_22/Reinforced/ExclusiveWeapon.
 *
 * Bảng phần trăm RNG gốc nằm server-side và không còn trong Lua/client/native/source PC đã audit.
 * Phần duy nhất là offline fallback nằm trong rollOutputGrade(): không cộng thẳng level,
 * không vượt trần +2 và vẫn cho phép tụt sâu tới phẩm thấp như hành vi TS M đã ghi nhận.
 * Tách riêng policy này để khi có bảng RNG gốc chỉ thay đúng một hàm, không đổi protocol/data/transaction.
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
        const val SMELTING_SKILL_ID = 14011
        const val FORGING_SKILL_ID = 14019

        private const val RESTRICT_NO_COMPOUND_MATERIAL = 4
        private const val RESTRICT_CANNOT_BE_COMPOUNDED = 8

        // Offline probability fallback only. Đây không phải bảng % gốc của DVT.
        private const val SKILL_LEVELS_PER_QUALITY_ROLL = 5

        // EItemKind từ Lua UICompound.UpdateCompound().
        private val FORBIDDEN_INPUT_KINDS = setOf(
            55, // Pearl
            67, // Gem
            86, // Remove
            36, // Ice
            22, // Item_22
            88, // Reinforced
            87, // ExclusiveWeapon
        )

        private val STANDARD_FEATHER_ELEMENTS = 1..4
    }

    private data class SubmittedPart(
        val material: Int,
        val quantity: Int,
        val level: Int,
    )

    private data class RecipeMatch(
        val recipe: CompoundRecipeDef,
        val batchCount: Int,
        val baseGrade: Int,
    )

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
        val logicalDefs = ArrayList<ItemDef>(inputs.size)

        for (input in inputs) {
            val thing = bag[input.bagIndex] ?: return@withLock CompoundResult.InvalidRequest
            val def = itemDefs[thing.itemId] ?: return@withLock CompoundResult.InvalidRequest
            if (
                thing.isLocked ||
                def.kind in FORBIDDEN_INPUT_KINDS ||
                (def.restrict and RESTRICT_NO_COMPOUND_MATERIAL) != 0
            ) {
                return@withLock CompoundResult.InvalidRequest
            }
            consumed[input.bagIndex] = (consumed[input.bagIndex] ?: 0) + input.quantity
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

        val inputMaterials = logicalDefs.map { it.material }
        val matched = recipes.asSequence()
            .filter { it.matches(inputMaterials) }
            .mapNotNull { recipe -> matchRecipe(recipe, logicalDefs, inputs) }
            .toList()
        if (matched.isEmpty()) return@withLock CompoundResult.InvalidRequest

        return@withLock compoundGeneric(
            characterId = characterId,
            bag = bag,
            consumed = consumed,
            matched = matched,
        )
    }

    /**
     * Ghép input vào recipe không phụ thuộc thứ tự slot.
     * Degree là tỷ lệ recipe; batchCount là số lần chạy recipe song song.
     *
     * DVT hiện tại có degree hoạt động đều bằng 1. Ba record legacy có material thật nhưng
     * degreeC=0 đã được CompoundRecipeDef normalize thành 1; material=0 luôn bị bỏ qua.
     */
    private fun matchRecipe(
        recipe: CompoundRecipeDef,
        defs: List<ItemDef>,
        inputs: List<CompoundInput>,
    ): RecipeMatch? {
        if (recipe.arity != defs.size || defs.size != inputs.size) return null

        val recipeParts = recipe.parts.sortedWith(
            compareBy<CompoundRecipeDef.MaterialPart> { it.material }.thenBy { it.degree }
        )
        val submitted = defs.indices.map { index ->
            SubmittedPart(
                material = defs[index].material,
                quantity = inputs[index].quantity,
                level = defs[index].level,
            )
        }.sortedWith(compareBy<SubmittedPart> { it.material }.thenBy { it.quantity })

        var batchCount = 0
        var weightedLevel = 0L
        var totalDegree = 0L

        for (index in recipeParts.indices) {
            val part = recipeParts[index]
            val input = submitted[index]
            if (part.material != input.material || part.degree <= 0) return null
            if (input.quantity % part.degree != 0) return null

            val batches = input.quantity / part.degree
            if (batches <= 0) return null
            if (batchCount == 0) batchCount = batches
            else if (batchCount != batches) return null

            weightedLevel += input.level.toLong() * part.degree
            totalDegree += part.degree
        }

        if (batchCount <= 0 || totalDegree <= 0L) return null
        // Integer division is floor for positive DVT item grades.
        val baseGrade = (weightedLevel / totalDegree).toInt()
        return RecipeMatch(recipe, batchCount, baseGrade)
    }

    private suspend fun compoundGeneric(
        characterId: Long,
        bag: Map<Int, ThingData>,
        consumed: Map<Int, Int>,
        matched: List<RecipeMatch>,
    ): CompoundResult {
        if (matched.isEmpty()) return CompoundResult.InvalidRequest

        // CompoundData có thể cho cùng tuple material ra nhiều loại thành phẩm.
        // Giữ multiplicity của bảng DVT thay vì tự collapse resultKind.
        val match = matched[randomIndex(matched.size).floorMod(matched.size)]
        val recipe = match.recipe
        val resultQty = match.batchCount
        if (resultQty <= 0) return CompoundResult.InvalidRequest

        val maxGrade = match.baseGrade + 2
        val craftableCandidates = itemDefs.entries.asSequence()
            .filter { (_, def) ->
                def.kind == recipe.resultKind &&
                    (def.restrict and RESTRICT_CANNOT_BE_COMPOUNDED) == 0 &&
                    def.level <= maxGrade
            }
            .toList()
        if (craftableCandidates.isEmpty()) return CompoundResult.InvalidRequest

        val availableLevels = craftableCandidates
            .map { it.value.level }
            .distinct()
            .sorted()
        if (availableLevels.isEmpty()) return CompoundResult.InvalidRequest

        val skillPoints = effectiveCraftingSkillPoints(characterId)
        val selectedLevel = rollOutputGrade(availableLevels, skillPoints)

        val levelCandidates = craftableCandidates.filter { it.value.level == selectedLevel }
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

    /**
     * Offline fallback duy nhất cho bảng probability server-side đã thất lạc.
     *
     * Một quality roll chọn một grade thật của resultKind từ toàn bộ dải <= floor(avg)+2.
     * Hướng dẫn M xác nhận trần +2 và đáy có thể rơi tới phẩm 1; tài liệu chính thức xác nhận
     * điểm của ba skill 14005/14011/14019 chỉ làm tăng xác suất ra phẩm cao.
     *
     * Vì không có bảng % gốc, fallback dùng best-of-rolls theo tổng level ba skill.
     * Hệ số 5 được cô lập tại SKILL_LEVELS_PER_QUALITY_ROLL và KHÔNG được coi là contract DVT.
     */
    private fun rollOutputGrade(
        availableLevels: List<Int>,
        skillPoints: Int,
    ): Int {
        require(availableLevels.isNotEmpty())
        val points = skillPoints.coerceIn(0, 25)
        var rollCount = 1 + points / SKILL_LEVELS_PER_QUALITY_ROLL
        val remainder = points % SKILL_LEVELS_PER_QUALITY_ROLL
        if (
            remainder > 0 &&
            randomIndex(SKILL_LEVELS_PER_QUALITY_ROLL).floorMod(SKILL_LEVELS_PER_QUALITY_ROLL) < remainder
        ) {
            rollCount += 1
        }

        fun oneRoll(): Int =
            availableLevels[randomIndex(availableLevels.size).floorMod(availableLevels.size)]

        var best = oneRoll()
        repeat(rollCount - 1) {
            best = maxOf(best, oneRoll())
        }
        return best
    }

    /**
     * Generic Compound dùng đúng ba skill hợp thành được tài liệu chính thức liệt kê:
     * 14005 煉丹術 (10), 14011 鎔鑄術 (10), 14019 鍛造術 (5).
     * 14032 là 精煉術/Refining riêng, không cộng vào generic compound quality.
     *
     * Không tự gate level ở đây: điều kiện học skill thuộc SkillSystem/quest; resolver chỉ đọc
     * số level thật đang có và tổng hợp đúng phát biểu "điểm skill càng nhiều => chance càng cao".
     */
    private fun effectiveCraftingSkillPoints(characterId: Long): Int {
        val skills = skillSystem?.getCache(characterId)?.skills ?: return 0
        val alchemy = (skills[ALCHEMY_SKILL_ID] ?: 0).coerceIn(0, 10)
        val smelting = (skills[SMELTING_SKILL_ID] ?: 0).coerceIn(0, 10)
        val forging = (skills[FORGING_SKILL_ID] ?: 0).coerceIn(0, 5)
        return alchemy + smelting + forging
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

        // DVT ItemData có thể chứa nhiều cosmetic family cùng rank/element.
        // Base chain là itemId thấp nhất có exact kind+rank+element và được phép hợp thành.
        val resultEntry = itemDefs.entries
            .asSequence()
            .filter { (_, def) ->
                def.kind == FEATHER_KIND &&
                    def.level == targetLevel &&
                    def.element == chosenElement
            }
            .minByOrNull { (itemId, _) -> itemId }
            ?: return CompoundResult.InvalidRequest

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

    private fun Int.floorMod(bound: Int): Int {
        if (bound <= 0) return 0
        val mod = this % bound
        return if (mod >= 0) mod else mod + bound
    }
}
