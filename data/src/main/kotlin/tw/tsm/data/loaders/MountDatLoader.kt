package tw.tsm.data.loaders

import tw.tsm.data.DatReader
import tw.tsm.domain.data.MountDef

/**
 * 坐騎定義載入器 — 對應 `DataManager.OnLoadMountsData`
 *
 * 來源：`Mounts_C.dat`
 * 格式：count(Int32) + count 筆 MountsData.New(reader)
 *
 * MountsData.New 欄位順序（MountsData.lua L4-15）：
 *   npcId(UInt16) + flagId(UInt16) + source(UInt32) + scale(Byte) * 0.01
 */
object MountDatLoader {

    /**
     * 載入坐騎資料
     */
    fun load(data: ByteArray): Map<Int, MountDef> {
        val reader = DatReader(data)
        val count = reader.readInt32()
        val result = LinkedHashMap<Int, MountDef>(count)

        for (i in 0 until count) {
            val npcId = reader.readUInt16()
            val flagId = reader.readUInt16()
            val source = reader.readUInt32()
            val scale = reader.readByte() * 0.01

            if (npcId != 0) {
                result[npcId] = MountDef(npcId, flagId, source, scale)
            }
        }

        return result
    }
}
