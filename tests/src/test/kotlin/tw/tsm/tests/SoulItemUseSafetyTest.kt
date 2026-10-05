package tw.tsm.tests

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import tw.tsm.data.loaders.BlissBagDatLoader
import tw.tsm.data.loaders.ItemDatLoader
import tw.tsm.domain.constants.EItemUseKind
import tw.tsm.domain.data.FormulaParams
import tw.tsm.domain.interfaces.repositories.PetRepository
import tw.tsm.domain.valueobjects.FollowNpcData
import tw.tsm.gamelogic.systems.ItemUseProcessor
import tw.tsm.gamelogic.systems.PetSystem
import tw.tsm.gamelogic.systems.PlayerStateManager
import tw.tsm.gamelogic.systems.StatSystem
import java.nio.file.Files
import java.nio.file.Path

class SoulItemUseSafetyTest {
    private fun data(name: String): ByteArray {
        val root = generateSequence(Path.of("").toAbsolutePath()) { it.parent }
            .first { Files.exists(it.resolve("File.dat/Data-ios/IOS_Item_C.dat")) }
        return Files.readAllBytes(root.resolve("File.dat/Data-ios/$name"))
    }

    private val zeroFormula = FormulaParams(
        w1 = 0.0, w2 = 0.0, w3 = 0.0, w4 = 0.0, w5 = 0.0, w6 = 0.0, w7 = 0.0,
        w9 = 0.0, w10 = 0.0, w13 = 0.0, w14 = 0.0, w15 = 0.0, w16 = 0.0,
        w20 = 0.0, w21 = 0.0, w25 = 0.0, w28 = 0.0, w30 = 0.0, w31 = 0.0,
        w35 = 0.0, w36 = 0.0, extraExp = 0, baseHp = 0, baseSp = 0,
        w37 = 0.0, w40 = 0.0, w38 = 0.0, w39 = 0.0, w45 = 0.0,
        w50 = 0.0, w51 = 0.0, attAgiScope = 0, ran = 0
    )

    private val fakePetRepository = object : PetRepository {
        override suspend fun findByCharacterId(characterId: Long): List<FollowNpcData> = emptyList()
        override suspend fun saveAll(characterId: Long, pets: List<FollowNpcData>) = Unit
        override suspend fun delete(characterId: Long, storageType: Int, slotIndex: Int) = Unit
    }

    @Test
    fun dvtSoulBoxesResolveToSoulItemsOfTheExpectedQuality() {
        val itemDefs = ItemDatLoader.load(data("IOS_Item_C.dat"))
        val bliss = BlissBagDatLoader.load(data("IOS_BlissBag_C.dat"))
        val expectedQuality = mapOf(
            32994 to 0,
            32995 to 1,
            34073 to 2,
            34074 to 3
        )

        for ((bagId, quality) in expectedQuality) {
            val bag = requireNotNull(bliss[bagId])
            assertEquals(1, bag.kindCount, "bagId=" + bagId)
            assertEquals(10_000, bag.items.sumOf { it.pr }, "bagId=" + bagId)
            for (reward in bag.items) {
                val soulItem = requireNotNull(itemDefs[reward.itemId])
                assertEquals(EItemUseKind.SOUL, soulItem.specialAbility, "reward=" + reward.itemId)
                assertEquals(quality, soulItem.quality, "reward=" + reward.itemId)
            }
        }
    }

    @Test
    fun soulItem240IsNeverConsumedAsGenericNoOp() {
        val itemDefs = ItemDatLoader.load(data("IOS_Item_C.dat"))
        val soulItem = requireNotNull(itemDefs[59501])
        assertEquals(EItemUseKind.SOUL, soulItem.specialAbility)
        assertEquals(240, EItemUseKind.SOUL)

        val state = PlayerStateManager()
        state.initPlayer(
            characterId = 1L,
            hp = 100,
            sp = 100,
            maxHp = 100,
            maxSp = 100,
            level = 1,
            atk = 1,
            def = 1,
            agi = 1,
            intelligence = 1,
            hpx = 1,
            spx = 1,
            element = 1
        )
        val petSystem = PetSystem(fakePetRepository, StatSystem(zeroFormula))
        val processor = ItemUseProcessor(
            playerStateManager = state,
            petSystem = petSystem,
            itemDefs = itemDefs
        )

        val result = processor.processUseItem(
            characterId = 1L,
            itemId = 59501,
            quantity = 1,
            followIndex = 0,
            useType = 0
        )

        assertFalse(result.success)
        assertFalse(result.consumed)
        assertEquals(emptyList<Any>(), result.rewardItems)
        assertEquals(emptyList<Any>(), result.attrChanges)
    }
}
