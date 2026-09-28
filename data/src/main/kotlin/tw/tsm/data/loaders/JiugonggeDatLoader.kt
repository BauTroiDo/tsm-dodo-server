package tw.tsm.data.loaders

import tw.tsm.data.DatReader
import tw.tsm.domain.data.ActivityTimeDef
import tw.tsm.domain.data.JiugonggeAwardDef
import tw.tsm.domain.data.JiugonggeInfoDef
import tw.tsm.domain.data.JiugonggeMissionDef

/**
 * DVT 九宮格 loaders, 1:1 with:
 * - Data/JiugonggeInfo.lua
 * - Data/JiugonggeMissionData.lua
 * - Data/ActivityTime.lua
 */
object JiugonggeDatLoader {
    fun loadInfo(data: ByteArray): Map<Int, JiugonggeInfoDef> {
        val r = DatReader(data)
        val count = r.readInt32()
        val result = LinkedHashMap<Int, JiugonggeInfoDef>(count)
        repeat(count) {
            val id = r.readUInt16()
            val activityId = r.readUInt16()
            val kind = r.readByte()
            val kindName = r.readUInt32()
            val reset = r.readByte()
            val awards = List(7) {
                JiugonggeAwardDef(
                    itemId = r.readUInt16(),
                    quantity = r.readUInt32(),
                    getFlag = r.readUInt16(),
                )
            }
            if (id > 0) {
                result[id] = JiugonggeInfoDef(id, activityId, kind, kindName, reset, awards)
            }
        }
        return result
    }

    fun loadMissions(data: ByteArray): Map<Int, JiugonggeMissionDef> {
        val r = DatReader(data)
        val count = r.readInt32()
        val result = LinkedHashMap<Int, JiugonggeMissionDef>(count)
        repeat(count) {
            val def = JiugonggeMissionDef(
                id = r.readUInt16(),
                boardNumber = r.readUInt32(),
                kind = r.readByte(),
                kindValue1 = r.readUInt16(),
                descriptionStringId = r.readUInt32(),
                conditionKind = r.readByte(),
                conditionKindValue = r.readUInt32(),
                conditionOperator = r.readByte(),
                conditionValue = r.readUInt32(),
            )
            if (def.id > 0) result[def.id] = def
        }
        return result
    }

    fun loadActivityTimes(data: ByteArray): Map<Int, ActivityTimeDef> {
        val r = DatReader(data)
        val count = r.readInt32()
        val result = LinkedHashMap<Int, ActivityTimeDef>(count)
        repeat(count) {
            val def = ActivityTimeDef(
                id = r.readUInt16(),
                switchKind = r.readByte(),
                startYear = r.readUInt16(),
                startMonth = r.readByte(),
                startDay = r.readByte(),
                startHour = r.readByte(),
                startMinute = r.readByte(),
                endYear = r.readUInt16(),
                endMonth = r.readByte(),
                endDay = r.readByte(),
                endHour = r.readByte(),
                endMinute = r.readByte(),
                openDayMask = r.readByte(),
                openHour = r.readByte(),
                openMinute = r.readByte(),
                closeHour = r.readByte(),
                closeMinute = r.readByte(),
            )
            if (def.id > 0) result[def.id] = def
        }
        return result
    }
}
