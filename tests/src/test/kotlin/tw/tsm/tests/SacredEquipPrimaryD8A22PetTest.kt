package tw.tsm.tests

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tw.tsm.domain.constants.EAttribute
import tw.tsm.domain.constants.EItemKind
import tw.tsm.domain.data.ItemDef
import tw.tsm.domain.data.SacredEquipPrimaryDef
import tw.tsm.domain.interfaces.repositories.PetRepository
import tw.tsm.domain.valueobjects.FollowNpcData
import tw.tsm.domain.valueobjects.ThingData
import tw.tsm.gamelogic.systems.PetSystem
import tw.tsm.gamelogic.systems.StatSystem

class SacredEquipPrimaryD8A22PetTest {
    @Test
    fun mythMainHpPercentIsAppliedWhenPetRuntimeIsLoaded() = runTest {
        val repo = mockk<PetRepository>(relaxed = true)
        val stat = mockk<StatSystem>(relaxed = true) {
            every { calculateNeedTotalExp(any(), any()) } returns 0L
            every { calculateMaxHp(any(), any(), any(), any(), any(), any(), any()) } returns 1000L
            every { calculateMaxSp(any(), any(), any(), any(), any(), any(), any()) } returns 100
        }
        val mythDef = mockk<ItemDef> {
            every { kind } returns EItemKind.MYTH
            every { attr1Kind } returns 97
            every { attr1Value } returns 0
            every { attr2Kind } returns 0
            every { attr2Value } returns 0
        }
        val sacred = mapOf(
            97 to SacredEquipPrimaryDef(
                no = 97,
                attributeKind = EAttribute.HP_PERCENT,
                upgradeLv = List(16) { 10 }
            )
        )
        coEvery { repo.findByCharacterId(77L) } returns listOf(
            FollowNpcData(
                slotIndex = 1,
                npcId = 100,
                level = 1,
                exp = 0,
                hp = 1050L,
                sp = 50,
                seedFixed = true,
                equips = mapOf(
                    1 to ThingData(itemId = 70001, quantity = 1, mythLevel = 0)
                )
            )
        )
        val system = PetSystem(
            petRepository = repo,
            statCalculator = stat,
            itemDefs = mapOf(70001 to mythDef),
            sacredEquipPrimaryDefs = sacred
        )

        val pet = system.load(77L).follow.getValue(1)

        // Base MaxHP=1000; Myth main HP%=10 => 1100.
        // 1050 must stay intact; without Sacred main support it would clamp to 1000.
        assertEquals(1050L, pet.hp)
    }
}
