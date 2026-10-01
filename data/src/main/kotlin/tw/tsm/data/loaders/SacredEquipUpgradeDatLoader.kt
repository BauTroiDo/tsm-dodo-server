package tw.tsm.data.loaders

import tw.tsm.domain.data.SacredEquipUpgradeDef

/**
 * DVT Data/IOS_SacredEquipUpgrade_C.dat.
 * Lua SacredEquipUpgradeData.New(reader):
 * lv(U8) + pro(U8) + kind(U8) + spend[1..6](U16).
 */
object SacredEquipUpgradeDatLoader {
    private const val RECORD_SIZE = 15

    fun load(data: ByteArray): Map<Int, SacredEquipUpgradeDef> {
        require(data.size >= 4) { "IOS_SacredEquipUpgrade_C.dat quá ngắn: ${data.size}" }
        val count = readIntLE(data, 0)
        require(count >= 0) { "Số record SacredEquipUpgrade âm: $count" }
        val expected = 4 + count * RECORD_SIZE
        require(data.size == expected) {
            "IOS_SacredEquipUpgrade_C.dat size=${data.size}, expected=$expected, count=$count"
        }

        val result = LinkedHashMap<Int, SacredEquipUpgradeDef>(count)
        var off = 4
        repeat(count) {
            val level = data[off].toInt() and 0xFF
            val success = data[off + 1].toInt() and 0xFF
            val kind = data[off + 2].toInt() and 0xFF
            val spend = ArrayList<Int>(6)
            var p = off + 3
            repeat(6) {
                spend += readUInt16LE(data, p)
                p += 2
            }
            require(level !in result) { "SacredEquipUpgrade level bị trùng: $level" }
            result[level] = SacredEquipUpgradeDef(level, success, kind, spend)
            off += RECORD_SIZE
        }
        require(off == data.size)
        return result
    }

    private fun readUInt16LE(data: ByteArray, off: Int): Int =
        (data[off].toInt() and 0xFF) or ((data[off + 1].toInt() and 0xFF) shl 8)

    private fun readIntLE(data: ByteArray, off: Int): Int =
        readUInt16LE(data, off) or (readUInt16LE(data, off + 2) shl 16)
}
