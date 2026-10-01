package tw.tsm.data.loaders

import tw.tsm.domain.data.AstrolabeDef
import tw.tsm.domain.data.AstrolabeLevelDef

/**
 * Decoder Data/IOS_Astrolabe.dat của DVT.
 *
 * DatReader.lua:DecodeAll():
 * [recordSize U32LE][count U32LE]
 * [block0][encoded record 1..count][xorKey0][xorKey1]
 * record i uses xorKey(i % 2). AstrolabeData.New consumes 21 bytes:
 * index(U8) + 10 × [value(U8), needPoint(U8)].
 */
object AstrolabeDatLoader {
    private const val RECORD_SIZE = 21
    private const val RECORD_COUNT = 7
    private const val HEADER_SIZE = 8
    private const val PHYSICAL_BLOCK_COUNT = RECORD_COUNT + 3

    fun load(data: ByteArray): Map<Int, AstrolabeDef> {
        require(data.size >= HEADER_SIZE) { "IOS_Astrolabe.dat quá ngắn: \${data.size}" }
        val recordSize = readIntLE(data, 0)
        val count = readIntLE(data, 4)
        require(recordSize == RECORD_SIZE) {
            "IOS_Astrolabe.dat recordSize=\$recordSize, expected=\$RECORD_SIZE"
        }
        require(count == RECORD_COUNT) {
            "IOS_Astrolabe.dat count=\$count, expected=\$RECORD_COUNT"
        }
        val expectedSize = HEADER_SIZE + RECORD_SIZE * PHYSICAL_BLOCK_COUNT
        require(data.size == expectedSize) {
            "IOS_Astrolabe.dat size=\${data.size}, expected=\$expectedSize"
        }

        fun block(index: Int): ByteArray {
            val start = HEADER_SIZE + index * RECORD_SIZE
            return data.copyOfRange(start, start + RECORD_SIZE)
        }

        val key0 = block(RECORD_COUNT + 1)
        val key1 = block(RECORD_COUNT + 2)
        val result = linkedMapOf<Int, AstrolabeDef>()

        for (i in 1..RECORD_COUNT) {
            val encoded = block(i)
            val key = if (i % 2 == 1) key1 else key0
            val decoded = ByteArray(RECORD_SIZE) { n ->
                (encoded[n].toInt() xor key[n].toInt()).toByte()
            }

            val kind = decoded[0].toInt() and 0xFF
            require(kind in 1..7 && kind !in result) {
                "IOS_Astrolabe.dat kind không hợp lệ/trùng: \$kind"
            }
            val levels = ArrayList<AstrolabeLevelDef>(10)
            for (level in 0 until 10) {
                val value = decoded[1 + level * 2].toInt() and 0xFF
                val needPoint = decoded[2 + level * 2].toInt() and 0xFF
                levels += AstrolabeLevelDef(value, needPoint)
            }
            result[kind] = AstrolabeDef(kind, levels)
        }

        require(result.size == 7) { "IOS_Astrolabe.dat thiếu sao: \${result.keys}" }
        return result
    }

    private fun readIntLE(data: ByteArray, off: Int): Int =
        (data[off].toInt() and 0xFF) or
            ((data[off + 1].toInt() and 0xFF) shl 8) or
            ((data[off + 2].toInt() and 0xFF) shl 16) or
            ((data[off + 3].toInt() and 0xFF) shl 24)
}
