package tw.tsm.data.loaders

import tw.tsm.data.DatReader
import tw.tsm.domain.data.MissionAwardDef

object MissionAwardDatLoader {
    fun load(data: ByteArray): Map<Int, MissionAwardDef> {
        require(data.size >= 4)
        val r = DatReader(data)
        val count = r.readInt32()
        require(count >= 0 && data.size.toLong() == 4L + count.toLong() * 26L)
        val rows = List(count) {
            MissionAwardDef(
                id = r.readUInt16(),
                contentId = r.readUInt32(),
                conditionKind = r.readByte(),
                conditionValue = r.readUInt32().toInt(),
                operator = r.readByte(),
                targetValue = r.readUInt32().toInt(),
                itemId = r.readUInt16(),
                itemCount = r.readUInt32().toInt(),
                getFlag = r.readUInt16(),
                activityId = r.readUInt16(),
            )
        }
        require(rows.map { it.id }.toSet().size == rows.size)
        require(rows.all { it.id > 0 && it.activityId > 0 && it.itemId > 0 && it.itemCount > 0 && it.getFlag > 0 })
        return rows.associateBy { it.id }
    }
}
