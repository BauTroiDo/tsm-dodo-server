package tw.tsm.tests

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.data.loaders.DungeonComboDatLoader
import tw.tsm.domain.data.DungeonComboDef
import tw.tsm.domain.data.DungeonComboReward

class DungeonF3BDataTest {
    private val bytes = Files.readAllBytes(Path.of("..", "File.dat", "Data-ios", "IOS_DungeonCombo_C.dat"))

    @Test fun `real DVT table preserves all rows and their ordering`() {
        val tables = DungeonComboDatLoader.load(bytes)
        assertEquals(180, tables.values.sumOf { it.size })
        assertEquals(listOf(600L, 900L, 1200L), tables.getValue(1).map { it.kindValue })
        assertEquals(listOf(10L, 50L, 99L, 100L), tables.getValue(2).map { it.kindValue })
        assertEquals(DungeonComboReward(45665, 1), tables.getValue(2).last().rewardForTurn(0))
        assertEquals(DungeonComboReward(46376, 1), tables.getValue(2).last().rewardForTurn(3))
        assertEquals(listOf(1L, 2L, 3L), tables.getValue(4).map { it.kindValue })
        assertTrue(tables.getValue(4).all { it.kind == 4 })
    }

    @Test fun `rebirth reward falls back through absent lower slots exactly as Lua`() {
        val row = DungeonComboDef(1, 2, 100, listOf(
            DungeonComboReward(10, 5), DungeonComboReward(20, 8),
            DungeonComboReward(0, 0), DungeonComboReward(0, 0)))
        assertEquals(DungeonComboReward(20, 8), row.rewardForTurn(3))
        assertEquals(DungeonComboReward(10, 5), row.rewardForTurn(0))
        assertNull(row.rewardForTurn(-1))
        assertNull(row.rewardForTurn(4))
        assertNull(row.copy(items = List(4) { DungeonComboReward(0, 0) }).rewardForTurn(3))
    }

    @Test fun `UInt32 values and quantities remain unsigned`() {
        val raw = ByteBuffer.allocate(35).order(ByteOrder.LITTLE_ENDIAN)
        raw.putInt(1).putShort(1).put(2).putInt(-1)
        repeat(4) { raw.putShort(10).putInt(-1) }
        val row = DungeonComboDatLoader.load(raw.array()).getValue(1).single()
        assertEquals(4294967295L, row.kindValue)
        assertEquals(4294967295L, row.items[0].quantity)
    }

    @Test fun `invalid header record length and trailing data are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { DungeonComboDatLoader.load(byteArrayOf()) }
        assertThrows(IllegalArgumentException::class.java) { DungeonComboDatLoader.load(bytes.copyOf(bytes.size - 1)) }
        assertThrows(IllegalArgumentException::class.java) { DungeonComboDatLoader.load(bytes + byteArrayOf(0)) }
        val bad = bytes.copyOf()
        ByteBuffer.wrap(bad).order(ByteOrder.LITTLE_ENDIAN).putInt(-1)
        assertThrows(IllegalArgumentException::class.java) { DungeonComboDatLoader.load(bad) }
    }
}
