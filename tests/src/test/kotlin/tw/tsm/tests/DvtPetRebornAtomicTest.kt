package tw.tsm.tests

import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.domain.data.ItemDef
import tw.tsm.domain.data.NpcDef
import tw.tsm.domain.interfaces.StatCalculator
import tw.tsm.domain.interfaces.repositories.InventoryRepository
import tw.tsm.domain.interfaces.repositories.PetRebirthCommit
import tw.tsm.domain.valueobjects.FollowNpcData
import tw.tsm.domain.valueobjects.ThingData
import tw.tsm.gamelogic.systems.InventorySystem
import tw.tsm.gamelogic.systems.PetRebornSystem
import tw.tsm.gamelogic.systems.PetSystem
import tw.tsm.gamelogic.systems.RebornResult

class DvtPetRebornAtomicTest {
    private val charId = 6123L

    private fun npc(turn: Int, name: String): NpcDef = mockk(relaxed = true) {
        every { this@mockk.turn } returns turn
        every { this@mockk.name } returns name
        every { hpx } returns 10
        every { spx } returns 8
        every { atk } returns 20
        every { def } returns 18
        every { intelligence } returns 12
        every { agi } returns 14
        every { skills } returns intArrayOf(101, 0, 0)
    }

    private fun token(kind: Int, attr: Int, source: Int, target: Int): ItemDef =
        mockk(relaxed = true) {
            every { this@mockk.kind } returns kind
            every { attr1Kind } returns attr
            every { attr1Value } returns source
            every { attr2Kind } returns attr
            every { attr2Value } returns target
        }

    private fun setup(
        pet: FollowNpcData,
        bag: Map<Int, ThingData>,
        npcDefs: Map<Int, NpcDef>,
        itemDefs: Map<Int, ItemDef>,
        commit: PetRebirthCommit?,
    ): Setup {
        val pets = mockk<PetSystem>()
        every { pets.getFollowNpc(charId, pet.slotIndex) } returns pet
        every { pets.hasNpcOnBody(charId, any()) } returns false
        every { pets.hasNpcInInn(charId, any()) } returns false
        every { pets.rebornFollowNpc(charId, pet.slotIndex, any()) } answers {
            thirdArg<FollowNpcData>()
        }
        val inv = mockk<InventorySystem>()
        every { inv.getBag(charId) } returns bag
        every { inv.publishRebirthBagAfterCommit(charId, any(), any()) } just Runs
        val sql = mockk<InventoryRepository>()
        coEvery { sql.commitPetRebirthAtomic(charId, any(), any(), any(), any()) } returns commit
        val stats = mockk<StatCalculator>()
        every { stats.calculateMaxHp(any(), any(), any(), any(), any(), any(), any()) } returns 150L
        every { stats.calculateMaxSp(any(), any(), any(), any(), any(), any(), any()) } returns 80
        return Setup(
            PetRebornSystem(pets, inv, stats, npcDefs, itemDefs, sql),
            pets, inv, sql
        )
    }

    private data class Setup(
        val system: PetRebornSystem,
        val pets: PetSystem,
        val inventory: InventorySystem,
        val sql: InventoryRepository,
    )

    @Test
    fun secondRebirthCommitsCorrectTargetAndPreservesInstanceId() = runTest {
        val pet = FollowNpcData(
            slotIndex = 1, npcId = 41003, turnKind = 1,
            level = 60, loyalty = 100,
        )
        val f = setup(
            pet,
            mapOf(2 to ThingData(itemId = 51001, quantity = 2)),
            mapOf(41003 to npc(1, "First turn"), 45001 to npc(2, "Second turn")),
            mapOf(51001 to token(51, 67, 41003, 45001)),
            PetRebirthCommit(ThingData(itemId = 51001, quantity = 1)),
        )
        val result = f.system.reborn(charId, 1, 2)
        assertInstanceOf(RebornResult.Ok::class.java, result)
        val after = (result as RebornResult.Ok).pet
        assertEquals(45001, after.npcId)
        assertEquals(2, after.turnKind)
        assertEquals(pet.instanceId, after.instanceId)
        assertEquals(1, after.level)
        coVerify(exactly = 1) {
            f.sql.commitPetRebirthAtomic(charId, 2, 51001, pet, match {
                it.instanceId == pet.instanceId && it.npcId == 45001
            })
        }
        verify(exactly = 1) { f.inventory.publishRebirthBagAfterCommit(charId, 2, any()) }
    }

    @Test
    fun firstRebirthSelectsActuallyOwnedAlternativeToken() = runTest {
        val pet = FollowNpcData(slotIndex = 1, npcId = 10001, turnKind = 0, level = 30, loyalty = 60)
        val f = setup(
            pet,
            mapOf(5 to ThingData(itemId = 47387, quantity = 1)),
            mapOf(10001 to npc(0, "Base"), 41003 to npc(1, "Choice A"), 41386 to npc(1, "Choice B")),
            mapOf(47003 to token(37, 65, 10001, 41003), 47387 to token(37, 65, 10001, 41386)),
            PetRebirthCommit(null),
        )
        val result = f.system.reborn(charId, 1, 1) as RebornResult.Ok
        assertEquals(41386, result.pet.npcId)
        coVerify(exactly = 1) {
            f.sql.commitPetRebirthAtomic(charId, 5, 47387, pet, any())
        }
    }

    @Test
    fun failedSqlCommitLeavesPetAndBagUnchanged() = runTest {
        val pet = FollowNpcData(slotIndex = 1, npcId = 41003, turnKind = 1, level = 60, loyalty = 100)
        val f = setup(
            pet,
            mapOf(3 to ThingData(itemId = 51001, quantity = 1)),
            mapOf(41003 to npc(1, "First"), 45001 to npc(2, "Second")),
            mapOf(51001 to token(51, 67, 41003, 45001)),
            null,
        )
        val result = f.system.reborn(charId, 1, 2)
        assertEquals(PetRebornSystem.MSG_EXCEPTION, (result as RebornResult.Fail).msgKind)
        verify(exactly = 0) { f.pets.rebornFollowNpc(any(), any(), any()) }
        verify(exactly = 0) { f.inventory.publishRebirthBagAfterCommit(any(), any(), any()) }
    }

    @Test
    fun secondRebirthRejectsIncorrectGenerationBeforeDebit() = runTest {
        val pet = FollowNpcData(slotIndex = 1, npcId = 41003, turnKind = 0, level = 60, loyalty = 100)
        val f = setup(
            pet,
            mapOf(3 to ThingData(itemId = 51001, quantity = 1)),
            mapOf(41003 to npc(1, "First"), 45001 to npc(2, "Second")),
            mapOf(51001 to token(51, 67, 41003, 45001)),
            PetRebirthCommit(null),
        )
        assertInstanceOf(RebornResult.Fail::class.java, f.system.reborn(charId, 1, 2))
        coVerify(exactly = 0) { f.sql.commitPetRebirthAtomic(any(), any(), any(), any(), any()) }
    }
}
