package tw.tsm.data.loaders

import tw.tsm.data.DatReader
import tw.tsm.domain.data.AnotherWorldLvDef

/** DVT Data/AnotherWroldLvData.lua: count(Int32), id(UInt16), level(Byte), turn(Byte). */
object AnotherWorldLvDatLoader {
    fun load(bytes: ByteArray): Map<Int, AnotherWorldLvDef> {
        val reader = DatReader(bytes)
        val count = reader.readInt32()
        require(count in 1..255) { "Invalid DVT AnotherWorldLv record count: $count" }
        val result = LinkedHashMap<Int, AnotherWorldLvDef>(count)
        repeat(count) {
            val id = reader.readUInt16()
            val level = reader.readByte()
            val turn = reader.readByte()
            require(id in 1..255 && level > 0 && turn in 0..3) {
                "Invalid DVT AnotherWorldLv id=$id level=$level turn=$turn"
            }
            require(id !in result) { "Duplicate DVT AnotherWorldLv id=$id" }
            result[id] = AnotherWorldLvDef(id, level, turn)
        }
        require(!reader.canRead()) { "IOS_AnotherWorldLv_C.dat has trailing data" }
        return result
    }
}
