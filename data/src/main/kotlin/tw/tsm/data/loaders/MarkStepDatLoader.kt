package tw.tsm.data.loaders

import tw.tsm.data.DatReader

/**
 * Minimal parser for IOS_MarkStep_C.dat.
 *
 * Server-side Eve only needs to know whether a Mark mission owns real step
 * records. Missions with MarkDef.bitId > 0 but no MarkStep records are DVT
 * marker missions: Eve may ADD_STEP +1 to them and later gate on pStyle=3 flag.
 */
object MarkStepDatLoader {
    fun loadMissionIds(data: ByteArray): Set<Int> {
        val reader = DatReader(data)
        val count = reader.readInt32()
        val ids = LinkedHashSet<Int>()
        repeat(count) {
            val missionId = reader.readUInt16()
            ids += missionId
            reader.readByte() // step
            reader.readUnicodeString() // description
            repeat(5) {
                reader.readByte()   // kind
                reader.readUInt16() // id
                reader.readByte()   // count
                reader.readUInt16() // sceneId
                reader.readUInt16() // x
                reader.readUInt16() // y
                reader.readByte()   // eventKind
                reader.readByte()   // eventId
            }
            reader.readUnicodeString() // endGuide
            reader.readUInt16() // endSceneId
            reader.readUInt16() // endX
            reader.readUInt16() // endY
            reader.readByte() // endEventKind
            reader.readByte() // endEventId
            reader.readByte() // teleport
            reader.readByte() // checkTeam/bool
        }
        return ids
    }
}
