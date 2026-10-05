package tw.tsm.data.loaders

import tw.tsm.domain.data.EternalDungeonPrizeDef
import tw.tsm.domain.data.EternalDungeonPrizeEntry

/** DVT Data/EternalDungeonPrizeData.lua contract: count(Int32LE) + 19-byte records. */
object EternalDungeonPrizeDatLoader {
    private const val RECORD_SIZE = 19

    fun load(data: ByteArray): Map<Int, EternalDungeonPrizeDef> {
        require(data.size >= 4) { "IOS_EternalDungeonPrize_C.dat quá ngắn: ${data.size}" }
        val count = readIntLE(data, 0)
        require(count >= 0)
        val expected = 4 + count * RECORD_SIZE
        require(data.size == expected) {
            "IOS_EternalDungeonPrize_C.dat size=${data.size}, expected=$expected, count=$count"
        }

        val result = linkedMapOf<Int, EternalDungeonPrizeDef>()
        var off = 4
        repeat(count) {
            val id = readIntLE(data, off)
            var p = off + 4
            val prizes = ArrayList<EternalDungeonPrizeEntry>(5)
            repeat(5) {
                prizes += EternalDungeonPrizeEntry(
                    itemId = readUInt16LE(data, p),
                    count = data[p + 2].toInt() and 0xFF,
                )
                p += 3
            }
            require(result.put(id, EternalDungeonPrizeDef(id, prizes)) == null) {
                "EternalDungeon prize id trùng: $id"
            }
            off += RECORD_SIZE
        }
        return result
    }

    private fun readUInt16LE(data: ByteArray, off: Int): Int =
        (data[off].toInt() and 0xFF) or ((data[off + 1].toInt() and 0xFF) shl 8)

    private fun readIntLE(data: ByteArray, off: Int): Int =
        readUInt16LE(data, off) or (readUInt16LE(data, off + 2) shl 16)
}
