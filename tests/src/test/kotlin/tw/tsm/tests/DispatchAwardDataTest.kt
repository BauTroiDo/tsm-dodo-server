package tw.tsm.tests

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.data.loaders.CommonAwardDatLoader
import java.nio.file.*

class DispatchAwardDataTest {
    private fun data(name: String): ByteArray {
        val root = generateSequence(Path.of("").toAbsolutePath()) { it.parent }
            .first { Files.exists(it.resolve("File.dat/Data-ios/IOS_CommonAward_C.dat")) }
        return Files.readAllBytes(root.resolve("File.dat/Data-ios/IOS_${name}_C.dat"))
    }
    @Test fun commonAwardsKeepBothItemAndLuckyBagKinds() {
        val defs = CommonAwardDatLoader.load(data("CommonAward"))
        assertEquals((1..5).toSet(), defs.keys)
        for (award in defs.values) {
            assertEquals(6, award.size)
            assertEquals(listOf(1, 1, 2, 2, 2, 0), award.map { it.kind })
            assertEquals(65301, award[0].itemId)
            assertEquals(10L, award[0].count)
        }
    }
    @Test fun everyReferencedDispatchBagExistsWithItsOriginalBlocks() {
        val awards = CommonAwardDatLoader.load(data("CommonAward"))
        val bags = CommonAwardDatLoader.loadLuckyBags(data("LuckyBag2"))
        for (entry in awards.values.flatten().filter { it.kind == 2 }) {
            val bag = bags[entry.itemId]!!
            assertEquals(30, bag.entries.size)
            assertTrue(bag.entries.any { it.itemId > 0 && it.count > 0 })
        }
    }
    @Test fun malformedTablesFailRatherThanInventingMissingEntries() {
        assertThrows(IllegalArgumentException::class.java) { CommonAwardDatLoader.load(byteArrayOf(1,0,0,0)) }
        assertThrows(IllegalArgumentException::class.java) { CommonAwardDatLoader.loadLuckyBags(byteArrayOf(1,0,0,0)) }
        assertThrows(IllegalArgumentException::class.java) { CommonAwardDatLoader.load(data("CommonAward") + byteArrayOf(0)) }
    }
}
