package tw.tsm.data.loaders

import tw.tsm.domain.data.SacredEquipPrimaryDef

/**
 * DVT Data/IOS_SacredEquipPrimaryStatus_C.dat.
 *
 * Lua SacredEquipPrimaryData.New(reader):
 * no(U16) + attNo(U8) + upgradeLv[0..15](U16).
 * File starts with record count Int32LE and is otherwise plain data.
 */
object SacredEquipPrimaryDatLoader {
    private const val RECORD_SIZE = 35

    fun load(data: ByteArray): Map<Int, SacredEquipPrimaryDef> {
        require(data.size >= 4) { "IOS_SacredEquipPrimaryStatus_C.dat quá ngắn: ${data.size}" }
        val count = readIntLE(data, 0)
        require(count >= 0) { "Số record SacredEquipPrimary âm: $count" }

        val expectedSize = 4 + count * RECORD_SIZE
        require(data.size == expectedSize) {
            "IOS_SacredEquipPrimaryStatus_C.dat size=${data.size}, expected=$expectedSize, count=$count"
        }

        val result = LinkedHashMap<Int, SacredEquipPrimaryDef>(count)
        var off = 4
        repeat(count) {
            val no = readUInt16LE(data, off)
            val attributeKind = data[off + 2].toInt() and 0xFF
            val levels = ArrayList<Int>(16)
            var p = off + 3
            repeat(16) {
                levels += readUInt16LE(data, p)
                p += 2
            }

            require(no !in result) { "SacredEquipPrimary no bị trùng: $no" }
            result[no] = SacredEquipPrimaryDef(no, attributeKind, levels)
            off += RECORD_SIZE
        }

        require(off == data.size)
        return result
    }

    private fun readUInt16LE(data: ByteArray, off: Int): Int =
        (data[off].toInt() and 0xFF) or
            ((data[off + 1].toInt() and 0xFF) shl 8)

    private fun readIntLE(data: ByteArray, off: Int): Int =
        readUInt16LE(data, off) or (readUInt16LE(data, off + 2) shl 16)
}
