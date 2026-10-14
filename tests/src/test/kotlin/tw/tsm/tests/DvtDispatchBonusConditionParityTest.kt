package tw.tsm.tests

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.data.loaders.DispatchDatLoader
import tw.tsm.domain.data.DispatchBonusDef
import tw.tsm.gamelogic.systems.DispatchBonusConditionPolicy

/** DVT Lua DispatchBonusData:IsNpcMeetCondition, tested against bundled source data. */
class DvtDispatchBonusConditionParityTest {
    private fun sourceData(): Map<Int, DispatchBonusDef> {
        val root = generateSequence(Path.of("").toAbsolutePath()) { it.parent }
            .first { Files.exists(it.resolve("File.dat/Data-ios/IOS_DispatchBonus_C.dat")) }
        return DispatchDatLoader.loadBonus(
            Files.readAllBytes(root.resolve("File.dat/Data-ios/IOS_DispatchBonus_C.dat"))
        )
    }

    @Test fun allNinetyNineNativeBonusRulesUseValidConditions() {
        val defs = sourceData()
        assertEquals((1..99).toSet(), defs.keys)
        assertTrue(defs.values.any { it.conditionKind == 1 })
        assertTrue(defs.values.any { it.conditionKind == 2 })
        defs.forEach { (_, def) ->
            when (def.conditionKind) {
                1 -> assertTrue(def.conditionValue in 1..5)
                2 -> assertTrue(def.conditionValue in 1..4)
                else -> fail<Unit>("Unknown original DVT bonus condition kind ${def.conditionKind}")
            }
        }
    }

    @Test fun eachSourceRuleMatchesExactNpcMoralOrElementOnly() {
        for ((_, def) in sourceData()) {
            val moral = 2
            val element = 4
            val expected = when (def.conditionKind) {
                1 -> def.conditionValue == moral
                2 -> def.conditionValue == element
                else -> false
            }
            assertEquals(
                expected,
                DispatchBonusConditionPolicy.isMet(def, moral, element),
                "native effect index=${def.effectIndex} kind=${def.conditionKind} value=${def.conditionValue}"
            )
        }
    }

    @Test fun fireAndWindBonusCannotBothMatchSameNpcButNeitherBlocksDispatch() {
        val defs = sourceData()
        val fire = defs.values.first { it.conditionKind == 2 && it.conditionValue == 3 }
        val wind = defs.values.first { it.conditionKind == 2 && it.conditionValue == 4 }
        assertEquals(
            true to false,
            DispatchBonusConditionPolicy.matchingEffects(fire, wind, npcMoral = 1, npcElement = 3)
        )
        assertEquals(
            false to true,
            DispatchBonusConditionPolicy.matchingEffects(fire, wind, npcMoral = 1, npcElement = 4)
        )
    }

    @Test fun unknownOrAbsentBonusNeverCountsAsMet() {
        assertFalse(DispatchBonusConditionPolicy.isMet(null, 1, 1))
        val malformed = DispatchBonusDef(99, 1, 2, 3, 3500, 20658)
        assertFalse(DispatchBonusConditionPolicy.isMet(malformed, 1, 1))
        assertTrue(DispatchBonusConditionPolicy.isMet(
            DispatchBonusDef(1, 4, 3, 1, 3500, 20658), 4, 2
        ))
        assertTrue(DispatchBonusConditionPolicy.isMet(
            DispatchBonusDef(2, 1, 3, 1, 3500, 20658), 4, 1
        ))
    }
}
