package tw.tsm.tests

import io.mockk.mockk
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.data.loaders.DispatchDatLoader
import tw.tsm.domain.data.DispatchDef
import tw.tsm.gamelogic.systems.DispatchSystem
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path

class DispatchAwardTierTest {
    private fun bytes(rows: List<Triple<Int, Int, Int>>): ByteArray {
        val b = ByteBuffer.allocate(4 + rows.size * 10).order(ByteOrder.LITTLE_ENDIAN).putInt(rows.size)
        for ((kind, level, exp) in rows) {
            b.put(1.toByte()).put(kind.toByte()).put(level.toByte()).putShort(kind.toShort())
                .put(2.toByte()).putInt(exp)
        }
        return b.array()
    }
    private fun system(defs: Map<Int, Map<Int, List<DispatchDef>>>) =
        dispatchSystemForTest(mockk(), mockk(), defs, emptyMap())

    @Test fun repeatedKindsPreserveEveryTierInDataOrder() {
        val defs = DispatchDatLoader.loadDispatch(bytes(listOf(Triple(1, 0, 70000), Triple(1, 40, 80000), Triple(2, 0, 90000))))
        assertEquals(listOf(0, 40), defs[1]!![1]!!.map { it.minLv })
        assertEquals(90000L, defs[1]!![2]!!.single().exp)
    }
    @Test fun exactLevelBoundaryUsesPreviousTierLikeLua() {
        val s = system(DispatchDatLoader.loadDispatch(bytes(listOf(Triple(1, 0, 70000), Triple(1, 40, 80000), Triple(1, 60, 90000)))))
        assertNull(s.getAwardDef(1, 0))
        assertEquals(70000L, s.getAwardDef(1, 1)!!.exp)
        assertEquals(70000L, s.getAwardDef(1, 40)!!.exp)
        assertEquals(80000L, s.getAwardDef(1, 41)!!.exp)
        assertEquals(80000L, s.getAwardDef(1, 60)!!.exp)
        assertEquals(90000L, s.getAwardDef(1, 61)!!.exp)
        assertNull(s.getAwardDef(6, 61))
    }
    @Test fun stopsAtFirstIneligibleTierRatherThanSortingData() {
        val s = system(DispatchDatLoader.loadDispatch(bytes(listOf(Triple(1, 0, 1), Triple(1, 60, 2), Triple(1, 40, 3)))))
        assertEquals(1L, s.getAwardDef(1, 50)!!.exp)
    }
    @Test fun bundledDvtDataRetainsAllFortyFiveRowsAndExpKinds() {
        val root = generateSequence(Path.of("").toAbsolutePath()) { it.parent }
            .first { Files.exists(it.resolve("File.dat/Data-ios/IOS_Dispatch_C.dat")) }
        val defs = DispatchDatLoader.loadDispatch(Files.readAllBytes(root.resolve("File.dat/Data-ios/IOS_Dispatch_C.dat")))
        assertEquals(45, defs.values.sumOf { kinds -> kinds.values.sumOf { it.size } })
        assertEquals((1..5).toSet(), defs[1]!!.keys)
        val s = system(defs)
        for (kind in 1..5) {
            assertEquals(9, defs[1]!![kind]!!.size)
            assertEquals(2, s.getAwardDef(kind, 99)!!.expKind)
            assertEquals(1, s.getAwardDef(kind, 100)!!.expKind)
            assertEquals(100L, s.getAwardDef(kind, 100)!!.exp)
            assertEquals(60L, s.getAwardDef(kind, 150)!!.exp)
        }
    }
}
