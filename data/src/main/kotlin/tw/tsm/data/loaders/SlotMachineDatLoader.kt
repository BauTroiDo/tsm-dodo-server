package tw.tsm.data.loaders

import tw.tsm.domain.data.SlotMachinePools
import tw.tsm.domain.data.SlotPoolEntry

/**
 * Decoder IOS_Slot.dat của Mobile DVT.
 *
 * File gồm đúng 7 block × 307 byte. Bốn pool plaintext được obfuscate bằng XOR:
 * A = block1 xor block6; B = block2 xor block5;
 * C = block3 xor block6; D = block4 xor block5.
 *
 * Mỗi plaintext: count(U8) + reserved(6) +
 * count × [sourceIndex(U8), itemId(U16LE), qty(U8), weight(U16LE)] + zero padding.
 */
object SlotMachineDatLoader {
    private const val BLOCK_SIZE = 307
    private const val BLOCK_COUNT = 7
    private const val HEADER_SIZE = 7
    private const val RECORD_SIZE = 6

    fun load(data: ByteArray): SlotMachinePools {
        require(data.size == BLOCK_SIZE * BLOCK_COUNT) {
            "IOS_Slot.dat sai kích thước: " + data.size + ", expected=" + (BLOCK_SIZE * BLOCK_COUNT)
        }
        val blocks = List(BLOCK_COUNT) { index ->
            data.copyOfRange(index * BLOCK_SIZE, (index + 1) * BLOCK_SIZE)
        }
        return SlotMachinePools(
            poolA = decodePool(xor(blocks[1], blocks[6]), "A"),
            poolB = decodePool(xor(blocks[2], blocks[5]), "B"),
            poolC = decodePool(xor(blocks[3], blocks[6]), "C"),
            poolD = decodePool(xor(blocks[4], blocks[5]), "D"),
        )
    }

    private fun xor(a: ByteArray, b: ByteArray): ByteArray =
        ByteArray(BLOCK_SIZE) { i -> (a[i].toInt() xor b[i].toInt()).toByte() }

    private fun decodePool(bytes: ByteArray, name: String): List<SlotPoolEntry> {
        val count = bytes[0].toInt() and 0xFF
        require(count > 0) { "Slot pool " + name + " rỗng" }
        require(HEADER_SIZE + count * RECORD_SIZE <= BLOCK_SIZE) {
            "Slot pool " + name + " count=" + count + " vượt block"
        }
        for (i in 1 until HEADER_SIZE) {
            require(bytes[i].toInt() == 0) { "Slot pool " + name + " reserved[" + i + "] != 0" }
        }

        val result = ArrayList<SlotPoolEntry>(count)
        repeat(count) { n ->
            val off = HEADER_SIZE + n * RECORD_SIZE
            val sourceIndex = bytes[off].toInt() and 0xFF
            val itemId = (bytes[off + 1].toInt() and 0xFF) or
                ((bytes[off + 2].toInt() and 0xFF) shl 8)
            val qty = bytes[off + 3].toInt() and 0xFF
            val weight = (bytes[off + 4].toInt() and 0xFF) or
                ((bytes[off + 5].toInt() and 0xFF) shl 8)

            require(sourceIndex == n + 1) {
                "Slot pool " + name + " index=" + sourceIndex + ", expected=" + (n + 1)
            }
            require(itemId > 0 && qty > 0 && weight > 0) {
                "Slot pool " + name + " record " + (n + 1) +
                    " không hợp lệ: item=" + itemId + " qty=" + qty + " weight=" + weight
            }
            result += SlotPoolEntry(sourceIndex, itemId, qty, weight)
        }

        val tailStart = HEADER_SIZE + count * RECORD_SIZE
        require(bytes.copyOfRange(tailStart, bytes.size).all { it.toInt() == 0 }) {
            "Slot pool " + name + " có dữ liệu lạ sau record cuối"
        }
        return result
    }
}
