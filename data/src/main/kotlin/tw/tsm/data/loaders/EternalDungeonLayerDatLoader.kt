package tw.tsm.data.loaders

import tw.tsm.domain.data.EternalDungeonLayerDef

/** DVT Data/EternalDungeonLayerData.lua contract: count(Int32LE) + 13-byte records. */
object EternalDungeonLayerDatLoader {
    private const val RECORD_SIZE = 13

    fun load(data: ByteArray): Map<Int, EternalDungeonLayerDef> {
        require(data.size >= 4) { "IOS_EternalDungeonLayer_C.dat quá ngắn: ${data.size}" }
        val count = readIntLE(data, 0)
        require(count >= 0)
        val expected = 4 + count * RECORD_SIZE
        require(data.size == expected) {
            "IOS_EternalDungeonLayer_C.dat size=${data.size}, expected=$expected, count=$count"
        }

        val result = linkedMapOf<Int, EternalDungeonLayerDef>()
        var off = 4
        repeat(count) {
            val id = readUInt16LE(data, off)
            val effects = listOf(
                readUInt16LE(data, off + 2),
                readUInt16LE(data, off + 4),
                readUInt16LE(data, off + 6),
            )
            val compare = data[off + 8].toInt() and 0xFF
            val needs = listOf(
                readUInt16LE(data, off + 9),
                readUInt16LE(data, off + 11),
            )
            require(result.put(id, EternalDungeonLayerDef(id, effects, compare, needs)) == null) {
                "EternalDungeon layer id trùng: $id"
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
