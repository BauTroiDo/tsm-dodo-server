package tw.tsm.data.loaders

import tw.tsm.data.DatReader
import tw.tsm.domain.data.DispatchBonusDef
import tw.tsm.domain.data.DispatchDef

/**
 * Trình nạp dữ liệu phái cử
 *
 * Nguồn: DispatchData.lua
 */
object DispatchDatLoader {

    /**
     * Nạp Dispatch_C.dat
     *
     * Định dạng: count(Int32) + count bản ghi DispatchData.New(reader)
     * Mỗi bản ghi: mainKind(1) + subKind(1) + minLv(1) + awardId(2) + expKind(1) + exp(4)
     *
     * @return Map<mainKind, Map<subKind, DispatchDef>>
     */
    fun loadDispatch(data: ByteArray): Map<Int, Map<Int, List<DispatchDef>>> {
        val reader = DatReader(data)
        val count = reader.readInt32()
        val result = LinkedHashMap<Int, MutableMap<Int, MutableList<DispatchDef>>>()

        for (i in 0 until count) {
            val mainKind = reader.readByte()
            val subKind = reader.readByte()
            val minLv = reader.readByte()
            val awardId = reader.readUInt16()
            val expKind = reader.readByte()
            val exp = reader.readUInt32().toLong()

            result.getOrPut(mainKind) { LinkedHashMap() }.getOrPut(subKind) { mutableListOf() }.add(DispatchDef(
                minLv = minLv,
                awardId = awardId,
                expKind = expKind,
                exp = exp
            ))
        }

        return result
    }

    /**
     * Nạp DispatchBonus_C.dat
     *
     * Định dạng: count(Int32) + count bản ghi DispatchBonusData.New(reader)
     * Mỗi bản ghi: id(1) + conditionKind(1) + conditionValue(1) + effectIndex(1) + effectKind(1) + effectValue(2) + effectContent(4)
     *
     * @return Map<id, DispatchBonusDef>
     */
    fun loadBonus(data: ByteArray): Map<Int, DispatchBonusDef> {
        val reader = DatReader(data)
        val count = reader.readInt32()
        val result = LinkedHashMap<Int, DispatchBonusDef>(count)

        for (i in 0 until count) {
            val id = reader.readByte()
            val conditionKind = reader.readByte()
            val conditionValue = reader.readByte()
            val effectIndex = reader.readByte()
            val effectKind = reader.readByte()
            val effectValue = reader.readUInt16()
            val effectContent = reader.readUInt32().toLong()

            result[id] = DispatchBonusDef(
                conditionKind = conditionKind,
                conditionValue = conditionValue,
                effectIndex = effectIndex,
                effectKind = effectKind,
                effectValue = effectValue,
                effectContent = effectContent
            )
        }

        return result
    }
}
