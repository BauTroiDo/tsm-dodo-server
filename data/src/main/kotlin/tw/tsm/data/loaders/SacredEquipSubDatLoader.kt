package tw.tsm.data.loaders

import tw.tsm.domain.data.SacredEquipSubDef
import tw.tsm.domain.data.SacredEquipSubDomainDef

object SacredEquipSubDatLoader {
    private const val RECORD_SIZE = 23

    fun load(data: ByteArray): Map<Int, SacredEquipSubDef> {
        require(data.size >= 4)
        val count = readIntLE(data, 0)
        val expected = 4 + count * RECORD_SIZE
        require(count >= 0 && data.size == expected)

        val result = LinkedHashMap<Int, SacredEquipSubDef>(count)
        var off = 4
        repeat(count) {
            val no = readUInt16LE(data, off)
            val att = data[off + 2].toInt() and 0xFF
            val values = ArrayList<Int>(10)
            var p = off + 3
            repeat(10) {
                values += readUInt16LE(data, p)
                p += 2
            }
            require(no !in result)
            result[no] = SacredEquipSubDef(no, att, values)
            off += RECORD_SIZE
        }
        return result
    }

    private fun readUInt16LE(data: ByteArray, off: Int): Int =
        (data[off].toInt() and 0xFF) or ((data[off + 1].toInt() and 0xFF) shl 8)

    private fun readIntLE(data: ByteArray, off: Int): Int =
        readUInt16LE(data, off) or (readUInt16LE(data, off + 2) shl 16)
}

object SacredEquipSubDomainDatLoader {
    private const val RECORD_SIZE = 2

    fun load(data: ByteArray): List<SacredEquipSubDomainDef> {
        require(data.size >= 4)
        val count = readIntLE(data, 0)
        val expected = 4 + count * RECORD_SIZE
        require(count >= 0 && data.size == expected)

        val result = ArrayList<SacredEquipSubDomainDef>(count)
        var off = 4
        repeat(count) {
            result += SacredEquipSubDomainDef(
                threshold = data[off].toInt() and 0xFF,
                attributeKind = data[off + 1].toInt() and 0xFF,
            )
            off += RECORD_SIZE
        }
        return result
    }

    private fun readUInt16LE(data: ByteArray, off: Int): Int =
        (data[off].toInt() and 0xFF) or ((data[off + 1].toInt() and 0xFF) shl 8)

    private fun readIntLE(data: ByteArray, off: Int): Int =
        readUInt16LE(data, off) or (readUInt16LE(data, off + 2) shl 16)
}
