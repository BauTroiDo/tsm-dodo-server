package tw.tsm.tests

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tw.tsm.data.loaders.FNpcSlotDatLoader

class FNpcSlotDatLoaderTest {
    @Test
    fun loadsDvtNineByteRecordsExactly() {
        val b = ByteBuffer.allocate(4 + 2 * 9).order(ByteOrder.LITTLE_ENDIAN)
        b.putInt(2)
        b.putShort(1).putInt(5_000_000).put(1).put(2).put(3)
        b.putShort(2).putInt(123_456).put(4).put(5).put(6)

        val defs = FNpcSlotDatLoader.load(b.array())
        assertEquals(2, defs.size)
        assertEquals(5_000_000, defs.getValue(1).exp)
        assertEquals(listOf(1, 2, 3), defs.getValue(1).icons)
        assertEquals(123_456, defs.getValue(2).exp)
        assertEquals(listOf(4, 5, 6), defs.getValue(2).icons)
    }
}
