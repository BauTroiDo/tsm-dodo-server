package tw.tsm.tests

import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.data.loaders.CompoundDatLoader
import tw.tsm.data.loaders.ItemDatLoader
import tw.tsm.data.loaders.SkillDatLoader
import tw.tsm.domain.data.CompoundRecipeDef
import tw.tsm.domain.data.ItemDef
import tw.tsm.domain.valueobjects.ThingData
import tw.tsm.gamelogic.systems.CompoundInput
import tw.tsm.gamelogic.systems.CompoundResult
import tw.tsm.gamelogic.systems.CompoundSystem
import tw.tsm.gamelogic.systems.InventorySystem
import tw.tsm.gamelogic.systems.RoleCountSystem
import tw.tsm.gamelogic.systems.SkillSystem
import java.nio.file.Files
import java.nio.file.Path

class DvtCompoundTest {
    private data class CraftIngredient(
        val itemId: Int,
        val def: ItemDef,
    )

    private data class GradeCase(
        val recipe: CompoundRecipeDef,
        val source: List<CraftIngredient>,
        val availableLevels: List<Int>,
        val maxGrade: Int,
    )

    private fun existing(vararg parts: String): Path {
        val direct = Path.of(parts.first(), *parts.drop(1).toTypedArray())
        if (Files.exists(direct)) return direct
        val parent = Path.of("..", *parts)
        check(Files.exists(parent)) { "Missing fixture: " + parts.joinToString("/") }
        return parent
    }
    private val itemDefs by lazy {
        ItemDatLoader.load(Files.readAllBytes(existing("File.dat", "Data-ios", "IOS_Item_C.dat")))
    }

    private val recipes by lazy {
        CompoundDatLoader.load(Files.readAllBytes(existing("File.dat", "Data-ios", "IOS_Compound.dat")))
    }

    private val skillDefs by lazy {
        SkillDatLoader.load(Files.readAllBytes(existing("File.dat", "Data-ios", "IOS_Skill_C.dat")))
    }

    @Test
    fun `IOS Compound decoder reproduces DVT table`() {
        assertEquals(1374, recipes.size)
        assertTrue(recipes.all { listOf(it.degreeA, it.degreeB, it.degreeC).all { degree -> degree in 0..5 } })
        assertEquals(210, recipes.count { it.degreeC == 0 })
        assertEquals(3, recipes.count { it.materialC > 0 && it.degreeC == 0 })
        assertEquals(12, recipes.count { it.materialC == 0 && it.degreeC > 0 })
        assertTrue(recipes.all { it.materials.size in 2..3 })
        assertTrue(recipes.all { recipe -> recipe.parts.all { it.degree > 0 } })
    }
    @Test
    fun `DVT data keeps refining after the three generic crafting skills`() {
        val alchemy = skillDefs.getValue(14005)
        val smelting = skillDefs.getValue(14011)
        val forging = skillDefs.getValue(14019)
        val refining = skillDefs.getValue(14032)

        assertEquals(10, alchemy.maxLv)
        assertEquals(10, smelting.maxLv)
        assertEquals(listOf(14005), smelting.preSkills)
        assertEquals(5, forging.maxLv)
        assertEquals(listOf(14011), forging.preSkills)
        assertEquals(10, refining.maxLv)
        assertEquals(listOf(14019), refining.preSkills)
    }

    @Test
    fun `three equal rank feathers compound atomically into next rank feather`() = runTest {
        val byLevel = itemDefs.entries
            .filter { it.value.kind == CompoundSystem.FEATHER_KIND && it.value.element in 1..4 }
            .groupBy { it.value.level }

        val source = byLevel.entries
            .asSequence()
            .filter { (_, rows) -> rows.size >= 3 }
            .mapNotNull { (level, rows) ->
                val first = rows.first()
                val targetExists = itemDefs.any { (_, def) ->
                    def.kind == CompoundSystem.FEATHER_KIND &&
                        def.level == level + 1 &&
                        def.element == first.value.element
                }
                val sourceRows = rows.filter { (it.value.restrict and 4) == 0 }
                if (targetExists && sourceRows.size >= 3) sourceRows.take(3) else null
            }
            .first()

        val inventory = mockk<InventorySystem>()
        val roleCount = mockk<RoleCountSystem>()
        val bag = linkedMapOf(
            1 to ThingData(itemId = source[0].key, quantity = 2),
            2 to ThingData(itemId = source[1].key, quantity = 2),
            3 to ThingData(itemId = source[2].key, quantity = 2),
        )
        every { inventory.getBag(42L) } returns bag
        coEvery { roleCount.getBagCapacity(42L) } returns 250
        coEvery { inventory.setBagItemsAtomic(42L, any()) } just Runs

        val system = CompoundSystem(inventory, itemDefs, recipes, roleCount, randomIndex = { 0 })
        val result = system.compound(
            42L, CompoundSystem.COMPOUND_KIND,
            listOf(CompoundInput(1, 2), CompoundInput(2, 2), CompoundInput(3, 2))
        )

        assertTrue(result is CompoundResult.Success)
        result as CompoundResult.Success
        assertEquals(4, result.resultSlot)
        assertEquals(2, result.result.quantity)
        val outDef = itemDefs.getValue(result.result.itemId)
        assertEquals(source[0].value.level + 1, outDef.level)
        assertEquals(source[0].value.element, outDef.element)

        val changes = slot<Map<Int, ThingData?>>()
        coVerify(exactly = 1) { inventory.setBagItemsAtomic(42L, capture(changes)) }
        assertNull(changes.captured[1])
        assertNull(changes.captured[2])
        assertNull(changes.captured[3])
        assertEquals(result.result, changes.captured[4])
    }
    @Test
    fun `generic DVT recipe consumes inputs and creates a real result atomically`() = runTest {
        val forbiddenInputKinds = setOf(22, 36, 55, 67, 86, 87, 88)
        val defsByMaterial = itemDefs.entries.groupBy { it.value.material }
        val recipe = recipes.first { candidate ->
            candidate.materials.all { material ->
                defsByMaterial[material].orEmpty().any {
                    it.value.kind !in forbiddenInputKinds &&
                        it.value.kind != CompoundSystem.FEATHER_KIND &&
                        (it.value.restrict and 4) == 0
                }
            } && itemDefs.values.any {
                it.kind == candidate.resultKind && (it.restrict and 8) == 0
            }
        }
        val materials = recipe.materials
        val source = materials.map { material ->
            defsByMaterial.getValue(material).first {
                it.value.kind !in forbiddenInputKinds &&
                    it.value.kind != CompoundSystem.FEATHER_KIND &&
                    (it.value.restrict and 4) == 0
            }
        }

        val inventory = mockk<InventorySystem>()
        val roleCount = mockk<RoleCountSystem>()
        val bag = source.mapIndexed { idx, row ->
            idx + 1 to ThingData(itemId = row.key, quantity = 1)
        }.toMap()
        every { inventory.getBag(42L) } returns bag
        coEvery { roleCount.getBagCapacity(42L) } returns 250
        coEvery { inventory.setBagItemsAtomic(42L, any()) } just Runs

        val matching = recipes.filter { it.matches(materials) }
        val expectedKind = matching.first().resultKind
        val system = CompoundSystem(inventory, itemDefs, recipes, roleCount, randomIndex = { 0 })
        val result = system.compound(
            42L, CompoundSystem.COMPOUND_KIND,
            source.indices.map { CompoundInput(it + 1, 1) }
        )

        assertTrue(result is CompoundResult.Success)
        result as CompoundResult.Success
        assertEquals(source.size + 1, result.resultSlot)
        assertEquals(1, result.result.quantity)
        assertEquals(expectedKind, itemDefs.getValue(result.result.itemId).kind)

        val changes = slot<Map<Int, ThingData?>>()
        coVerify(exactly = 1) { inventory.setBagItemsAtomic(42L, capture(changes)) }
        source.indices.forEach { assertNull(changes.captured[it + 1]) }
        assertEquals(result.result, changes.captured[source.size + 1])
    }

    @Test
    fun `generic grade is capped at floor average plus two and skill rolls only improve probability`() = runTest {
        val forbiddenInputKinds = setOf(22, 36, 55, 67, 86, 87, 88)
        val defsByMaterial = itemDefs.entries.groupBy { it.value.material }

        val case = recipes.asSequence().mapNotNull { recipe ->
            val source = recipe.materials.map { material ->
                defsByMaterial[material].orEmpty().firstOrNull {
                    it.value.kind !in forbiddenInputKinds &&
                        it.value.kind != CompoundSystem.FEATHER_KIND &&
                        (it.value.restrict and 4) == 0
                }
            }
            if (source.any { it == null }) return@mapNotNull null
            val concrete = source.filterNotNull().map { CraftIngredient(it.key, it.value) }
            val maxGrade = concrete.map { it.def.level }.average().toInt() + 2
            val levels = itemDefs.values.asSequence()
                .filter {
                    it.kind == recipe.resultKind &&
                        (it.restrict and 8) == 0 &&
                        it.level <= maxGrade
                }
                .map { it.level }
                .distinct()
                .sorted()
                .toList()
            if (levels.size >= 2) GradeCase(recipe, concrete, levels, maxGrade) else null
        }.first()

        val bag = case.source.mapIndexed { idx, row ->
            idx + 1 to ThingData(itemId = row.itemId, quantity = 1)
        }.toMap()

        suspend fun runWith(
            skills: SkillSystem?,
            randomIndex: (Int) -> Int,
        ): CompoundResult.Success {
            val inventory = mockk<InventorySystem>()
            val roleCount = mockk<RoleCountSystem>()
            every { inventory.getBag(42L) } returns bag
            coEvery { roleCount.getBagCapacity(42L) } returns 250
            coEvery { inventory.setBagItemsAtomic(42L, any()) } just Runs

            val system = CompoundSystem(
                inventorySystem = inventory,
                itemDefs = itemDefs,
                recipes = listOf(case.recipe),
                roleCountSystem = roleCount,
                skillSystem = skills,
                randomIndex = randomIndex,
            )
            val result = system.compound(
                42L,
                CompoundSystem.COMPOUND_KIND,
                case.source.indices.map { CompoundInput(it + 1, 1) },
            )
            assertTrue(result is CompoundResult.Success)
            return result as CompoundResult.Success
        }

        val noSkill = runWith(null) { 0 }
        val noSkillLevel = itemDefs.getValue(noSkill.result.itemId).level
        assertEquals(case.availableLevels.first(), noSkillLevel)
        assertTrue(noSkillLevel <= case.maxGrade)

        val skills = mockk<SkillSystem>()
        every { skills.getCache(42L) } returns SkillSystem.SkillCache(
            element = 1,
            skillPoint = 0,
            skills = mutableMapOf(
                CompoundSystem.ALCHEMY_SKILL_ID to 10,
                CompoundSystem.SMELTING_SKILL_ID to 10,
                CompoundSystem.FORGING_SKILL_ID to 5,
            ),
        )

        val maxSkill = runWith(skills) { bound -> bound - 1 }
        val maxSkillLevel = itemDefs.getValue(maxSkill.result.itemId).level
        assertEquals(case.availableLevels.last(), maxSkillLevel)
        assertTrue(maxSkillLevel >= noSkillLevel)
        assertTrue(maxSkillLevel <= case.maxGrade)
    }


    @Test
    fun `generic batch consumes N recipe sets and creates N results`() = runTest {
        val forbiddenInputKinds = setOf(22, 36, 55, 67, 86, 87, 88)
        val defsByMaterial = itemDefs.entries.groupBy { it.value.material }
        val recipe = recipes.first { candidate ->
            candidate.parts.all { it.degree == 1 } &&
                candidate.materials.all { material ->
                    defsByMaterial[material].orEmpty().any {
                        it.value.kind !in forbiddenInputKinds &&
                            it.value.kind != CompoundSystem.FEATHER_KIND &&
                            (it.value.restrict and 4) == 0
                    }
                } &&
                itemDefs.values.any {
                    it.kind == candidate.resultKind && (it.restrict and 8) == 0
                }
        }
        val source = recipe.materials.map { material ->
            defsByMaterial.getValue(material).first {
                it.value.kind !in forbiddenInputKinds &&
                    it.value.kind != CompoundSystem.FEATHER_KIND &&
                    (it.value.restrict and 4) == 0
            }
        }

        val inventory = mockk<InventorySystem>()
        val roleCount = mockk<RoleCountSystem>()
        val bag = source.mapIndexed { idx, row ->
            idx + 1 to ThingData(itemId = row.key, quantity = 5)
        }.toMap()
        every { inventory.getBag(42L) } returns bag
        coEvery { roleCount.getBagCapacity(42L) } returns 250
        coEvery { inventory.setBagItemsAtomic(42L, any()) } just Runs

        val system = CompoundSystem(inventory, itemDefs, listOf(recipe), roleCount, randomIndex = { 0 })
        val result = system.compound(
            42L,
            CompoundSystem.COMPOUND_KIND,
            source.indices.map { CompoundInput(it + 1, 3) },
        )

        assertTrue(result is CompoundResult.Success)
        result as CompoundResult.Success
        assertEquals(3, result.result.quantity)
        assertEquals(source.indices.associate { it + 1 to 3 }, result.consumedBySlot)

        val changes = slot<Map<Int, ThingData?>>()
        coVerify(exactly = 1) { inventory.setBagItemsAtomic(42L, capture(changes)) }
        source.indices.forEach { index ->
            assertEquals(2, changes.captured.getValue(index + 1)!!.quantity)
        }
    }

    @Test
    fun `generic recipe rejects unequal stack ratios without mutation`() = runTest {
        val forbiddenInputKinds = setOf(22, 36, 55, 67, 86, 87, 88)
        val defsByMaterial = itemDefs.entries.groupBy { it.value.material }
        val recipe = recipes.first { candidate ->
            candidate.arity == 2 && candidate.materials.all { material ->
                defsByMaterial[material].orEmpty().any {
                    it.value.kind !in forbiddenInputKinds &&
                        it.value.kind != CompoundSystem.FEATHER_KIND &&
                        (it.value.restrict and 4) == 0
                }
            }
        }
        val source = recipe.materials.map { material ->
            defsByMaterial.getValue(material).first {
                it.value.kind !in forbiddenInputKinds &&
                    it.value.kind != CompoundSystem.FEATHER_KIND &&
                    (it.value.restrict and 4) == 0
            }
        }
        val inventory = mockk<InventorySystem>()
        val roleCount = mockk<RoleCountSystem>()
        every { inventory.getBag(42L) } returns mapOf(
            1 to ThingData(itemId = source[0].key, quantity = 2),
            2 to ThingData(itemId = source[1].key, quantity = 2),
        )

        val system = CompoundSystem(inventory, itemDefs, recipes, roleCount, randomIndex = { 0 })
        val result = system.compound(
            42L, CompoundSystem.COMPOUND_KIND,
            listOf(CompoundInput(1, 1), CompoundInput(2, 2)),
        )

        assertEquals(CompoundResult.InvalidRequest, result)
        coVerify(exactly = 0) { inventory.setBagItemsAtomic(any(), any()) }
    }
}
