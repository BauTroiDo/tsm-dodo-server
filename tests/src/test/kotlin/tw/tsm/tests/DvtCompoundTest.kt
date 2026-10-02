package tw.tsm.tests

import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.data.loaders.CompoundDatLoader
import tw.tsm.data.loaders.ItemDatLoader
import tw.tsm.data.loaders.SkillDatLoader
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
        assertTrue(recipes.all { it.materials.size in 2..3 })
    }
    @Test
    fun `DVT keeps classic three-stage crafting skill chain`() {
        val alchemy = skillDefs.getValue(14005)
        val forging = skillDefs.getValue(14011)
        val creation = skillDefs.getValue(14019)

        assertEquals(10, alchemy.maxLv)
        assertEquals(10, forging.maxLv)
        assertEquals(listOf(14005), forging.preSkills)
        assertEquals(5, creation.maxLv)
        assertEquals(listOf(14011), creation.preSkills)
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
                if (targetExists) rows.take(3) else null
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
        val defsByMaterial = itemDefs.entries.groupBy { it.value.material }
        val recipe = recipes.first { candidate ->
            candidate.materials.all { material ->
                defsByMaterial[material].orEmpty().any {
                    it.value.kind != CompoundSystem.FEATHER_KIND && (it.value.restrict and 4) == 0
                }
            } && itemDefs.values.any {
                it.kind == candidate.resultKind && (it.restrict and 4) == 0
            }
        }
        val materials = recipe.materials
        val source = materials.map { material ->
            defsByMaterial.getValue(material).first {
                it.value.kind != CompoundSystem.FEATHER_KIND && (it.value.restrict and 4) == 0
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
    fun `learned crafting skill raises baseline generic grade by one`() = runTest {
        val defsByMaterial = itemDefs.entries.groupBy { it.value.material }
        val case = recipes.asSequence().flatMap { recipe ->
            if (recipes.first { it.matches(recipe.materials) } != recipe) return@flatMap emptySequence()
            (1..40).asSequence().mapNotNull { level ->
                val source = recipe.materials.map { material ->
                    defsByMaterial[material].orEmpty().firstOrNull {
                        it.value.level == level &&
                            it.value.kind != CompoundSystem.FEATHER_KIND &&
                            (it.value.restrict and 4) == 0
                    }
                }
                if (source.any { it == null }) return@mapNotNull null
                val hasBase = itemDefs.values.any {
                    it.kind == recipe.resultKind && it.level == level && (it.restrict and 4) == 0
                }
                val hasUpgrade = itemDefs.values.any {
                    it.kind == recipe.resultKind && it.level == level + 1 && (it.restrict and 4) == 0
                }
                if (hasBase && hasUpgrade) Triple(recipe, level, source.filterNotNull()) else null
            }
        }.first()

        val (_, level, source) = case
        val inventory = mockk<InventorySystem>()
        val roleCount = mockk<RoleCountSystem>()
        val skills = mockk<SkillSystem>()
        val bag = source.mapIndexed { idx, row ->
            idx + 1 to ThingData(itemId = row.key, quantity = 1)
        }.toMap()
        every { inventory.getBag(42L) } returns bag
        coEvery { roleCount.getBagCapacity(42L) } returns 250
        coEvery { inventory.setBagItemsAtomic(42L, any()) } just Runs
        every { skills.getCache(42L) } returns SkillSystem.SkillCache(
            element = 1,
            skillPoint = 0,
            skills = mutableMapOf(CompoundSystem.ALCHEMY_SKILL_ID to 1),
        )

        val system = CompoundSystem(
            inventory, itemDefs, recipes, roleCount, skills, randomIndex = { 0 }
        )
        val result = system.compound(
            42L, CompoundSystem.COMPOUND_KIND,
            source.indices.map { CompoundInput(it + 1, 1) }
        )

        assertTrue(result is CompoundResult.Success)
        result as CompoundResult.Success
        assertEquals(level + 1, itemDefs.getValue(result.result.itemId).level)
    }

    @Test
    fun `generic recipe rejects unequal stack ratios without mutation`() = runTest {
        val defsByMaterial = itemDefs.entries.groupBy { it.value.material }
        val recipe = recipes.first { candidate ->
            candidate.arity == 2 && candidate.materials.all { material ->
                defsByMaterial[material].orEmpty().any {
                    it.value.kind != CompoundSystem.FEATHER_KIND && (it.value.restrict and 4) == 0
                }
            }
        }
        val source = recipe.materials.map { material ->
            defsByMaterial.getValue(material).first {
                it.value.kind != CompoundSystem.FEATHER_KIND && (it.value.restrict and 4) == 0
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
