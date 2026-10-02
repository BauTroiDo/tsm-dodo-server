package tw.tsm.data.loaders

import tw.tsm.domain.data.CompoundRecipeDef

/**
 * Decoder File.dat/Data-ios/IOS_Compound.dat của DVT Mobile.
 *
 * Wrapper hiện tại: 8 byte [02 00 00 00 00 00 00 00], sau đó record 8 byte.
 * DataManager.OnLoadCompoundData / DatReader.New(file, 3, 211, 64444, 168229221):
 * resultKind(U16) + material A/B/C(U8) + degree A/B/C(U8).
 */
object CompoundDatLoader {
    private const val HEADER_SIZE = 8
    private const val RECORD_SIZE = 8
    private const val NUMBER_OFFSET = 3
    private const val XOR_U8 = 211
    private const val XOR_U16 = 64444

    private val EXPECTED_HEADER = byteArrayOf(2, 0, 0, 0, 0, 0, 0, 0)

    fun load(data: ByteArray): List<CompoundRecipeDef> {
        require(data.size >= HEADER_SIZE + RECORD_SIZE) {
            "IOS_Compound.dat quá ngắn: ${data.size}"
        }
        require(data.copyOfRange(0, HEADER_SIZE).contentEquals(EXPECTED_HEADER)) {
            "IOS_Compound.dat header không khớp DVT Mobile"
        }
        require((data.size - HEADER_SIZE) % RECORD_SIZE == 0) {
            "IOS_Compound.dat payload lệch record: size=${data.size}"
        }

        val result = ArrayList<CompoundRecipeDef>((data.size - HEADER_SIZE) / RECORD_SIZE)
        var off = HEADER_SIZE
        while (off < data.size) {
            val def = CompoundRecipeDef(
                resultKind = readU16(data, off),
                materialA = readU8(data, off + 2),
                materialB = readU8(data, off + 3),
                materialC = readU8(data, off + 4),
                degreeA = readU8(data, off + 5),
                degreeB = readU8(data, off + 6),
                degreeC = readU8(data, off + 7),
            )
            require(def.resultKind > 0 && def.arity in 2..3) {
                "IOS_Compound.dat record không hợp lệ tại offset=$off: $def"
            }
            require(listOf(def.degreeA, def.degreeB, def.degreeC).all { it in 0..5 }) {
                "IOS_Compound.dat degree không hợp lệ tại offset=$off: $def"
            }
            result += def
            off += RECORD_SIZE
        }
        require(result.size == 1374) {
            "IOS_Compound.dat recipe count=${result.size}, expected=1374"
        }
        return result
    }

    private fun readU8(data: ByteArray, off: Int): Int =
        (((data[off].toInt() and 0xFF) xor XOR_U8) - NUMBER_OFFSET) and 0xFF

    private fun readU16(data: ByteArray, off: Int): Int {
        val raw = (data[off].toInt() and 0xFF) or
            ((data[off + 1].toInt() and 0xFF) shl 8)
        return ((raw xor XOR_U16) - NUMBER_OFFSET) and 0xFFFF
    }
}
