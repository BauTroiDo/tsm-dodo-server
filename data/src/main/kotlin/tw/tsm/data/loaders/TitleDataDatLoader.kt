package tw.tsm.data.loaders

import tw.tsm.data.DatReader
import tw.tsm.domain.data.TitleDef

/**
 * Parser for DVT `IOS_TitleData_C.dat`, matching `Data/TitleData.lua`.
 *
 * Fixed row size is 43 bytes after the Int32 record count. The server only
 * needs id/name/flag for validation; the remaining 35 bytes are client-only
 * presentation (HUD texture/colors, cape and effect metadata).
 */
object TitleDataDatLoader {
    fun load(data: ByteArray): Map<Int, TitleDef> {
        val reader = DatReader(data)
        val count = reader.readInt32()
        val result = LinkedHashMap<Int, TitleDef>(count)
        repeat(count) {
            val id = reader.readUInt16()
            val nameStringId = reader.readUInt32()
            val flagId = reader.readUInt16()
            reader.readBytes(35)
            result[id] = TitleDef(id, nameStringId, flagId)
        }
        return result
    }
}
