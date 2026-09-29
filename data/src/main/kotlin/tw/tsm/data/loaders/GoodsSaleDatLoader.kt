package tw.tsm.data.loaders

import tw.tsm.data.DatReader
import tw.tsm.domain.data.GoodsSaleDef

/**
 * GS_C.dat loader.
 *
 * Exact row layout follows DVT Lua GoodsSaleData.New(reader).
 */
object GoodsSaleDatLoader {
    fun load(data: ByteArray): List<GoodsSaleDef> {
        val reader = DatReader(data)
        val count = reader.readInt32()
        val groupIndices = mutableMapOf<Triple<Int, Int, Int>, Int>()
        val result = ArrayList<GoodsSaleDef>(count)

        repeat(count) {
            val storeKind = reader.readByte()
            val currencyKind = reader.readByte()
            val mainKind = reader.readByte()
            val subKind = reader.readByte()
            val state = reader.readByte()
            val itemId = reader.readUInt16()
            val value = reader.readByte()
            val showCredit = reader.readUInt16()
            val saleCredit = reader.readUInt16()
            val saleKind = reader.readByte()
            val flag = reader.readUInt16()
            val dayCount = reader.readByte()
            val memo = reader.readByte()
            val smallTag = reader.readUInt32()
            val specialEvent = reader.readUInt32()
            val timeKind = reader.readByte()
            val startYear = reader.readUInt16()
            val startMonth = reader.readByte()
            val startDay = reader.readByte()
            val startHour = reader.readByte()
            val endYear = reader.readUInt16()
            val endMonth = reader.readByte()
            val endDay = reader.readByte()
            val endHour = reader.readByte()
            val buyLimitFlag = reader.readUInt16()
            val bitFunction = reader.readByte()

            val key = Triple(storeKind, mainKind, subKind)
            val index = (groupIndices[key] ?: 0) + 1
            groupIndices[key] = index

            result += GoodsSaleDef(
                storeKind = storeKind,
                currencyKind = currencyKind,
                mainKind = mainKind,
                subKind = subKind,
                open = state == 0,
                itemId = itemId,
                value = value,
                showCredit = showCredit,
                saleCredit = saleCredit,
                saleKind = saleKind,
                flag = flag,
                dayCount = dayCount,
                memo = memo,
                smallTag = smallTag,
                specialEvent = specialEvent,
                timeKind = timeKind,
                startYear = startYear,
                startMonth = startMonth,
                startDay = startDay,
                startHour = startHour,
                endYear = endYear,
                endMonth = endMonth,
                endDay = endDay,
                endHour = endHour,
                buyLimitFlag = buyLimitFlag,
                bitFunction = bitFunction,
                pageItemIndex = index,
            )
        }
        return result
    }
}
