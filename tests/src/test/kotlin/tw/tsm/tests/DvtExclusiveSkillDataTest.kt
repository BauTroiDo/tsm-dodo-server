package tw.tsm.tests

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tw.tsm.data.loaders.ExclusiveSkillDatLoader
import java.nio.file.Files
import java.nio.file.Path

class DvtExclusiveSkillDataTest {
    private fun root(): Path =
        generateSequence(Path.of("").toAbsolutePath()) { it.parent }
            .first { Files.exists(it.resolve("File.dat/Data-ios/IOS_ExclusiveSkill_C.dat")) }

 private fun data(): ByteArray =
        Files.readAllBytes(root().resolve("File.dat/Data-ios/IOS_ExclusiveSkill_C.dat"))

 @Test
 fun exclusiveSkillLoaderPreservesAllDvtRecords() {
 val defs = ExclusiveSkillDatLoader.load(data())
 val total = defs.values.sumOf { it.size }

 assertEquals(574, total)
 assertEquals(574, defs.values.sumOf { levels -> levels.keys.size })
 }

 @Test
 fun knownPassiveAndBossRecordsStayByteSemanticRaw() {
 val defs = ExclusiveSkillDatLoader.load(data())

 val stat = requireNotNull(defs[14066]?.get(1))
 assertEquals(listOf(0, 0, 0, 0, 0, 0), stat.headerRaw)
 assertEquals(1, stat.effects[0].kind)
 assertEquals(1, stat.effects[0].arg1)
 assertEquals(0, stat.effects[0].arg2)
 assertEquals(1, stat.effects[0].parameter)
 assertEquals(10L, stat.effects[0].value)

 val revive = requireNotNull(defs[14080]?.get(3))
 assertEquals(4, revive.effects[0].kind)
 assertEquals(1, revive.effects[0].arg1)
 assertEquals(1, revive.effects[0].arg2)
 assertEquals(0, revive.effects[0].parameter)
 assertEquals(70L, revive.effects[0].value)

 val shield = requireNotNull(defs[40001]?.get(1))
 assertEquals(2, shield.effects[0].kind)
 assertEquals(10L, shield.effects[0].value)
 }

 @Test
 fun fengshenAndWushuangPassivesCarryIdenticalRawAttributePayloads() {
 val defs = ExclusiveSkillDatLoader.load(data())
 val fengshen = requireNotNull(defs[40000]?.get(1))
 val wushuang = requireNotNull(defs[45000]?.get(1))

 assertEquals(fengshen.headerRaw, wushuang.headerRaw)
 assertEquals(fengshen.effects, wushuang.effects)
 assertEquals(59, fengshen.effects[0].kind)
 assertEquals(90, fengshen.effects[0].parameter)
 assertEquals(1400L, fengshen.effects[0].value)
 assertEquals(59, fengshen.effects[1].kind)
 assertEquals(114, fengshen.effects[1].parameter)
 assertEquals(20L, fengshen.effects[1].value)
 }

 @Test
 fun phoenixRebirthLevelsMapToDvtHpPercentValues() {
 val defs = ExclusiveSkillDatLoader.load(data())
 val expected = mapOf(1 to 50L, 2 to 50L, 3 to 70L, 4 to 70L, 5 to 100L, 6 to 100L)

 for ((level, value) in expected) {
 val record = requireNotNull(defs[14080]?.get(level))
 assertEquals(4, record.effects[0].kind)
 assertEquals(value, record.effects[0].value)
 }
 }
}
