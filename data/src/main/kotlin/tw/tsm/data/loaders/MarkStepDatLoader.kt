package tw.tsm.data.loaders

import tw.tsm.data.DatReader
import tw.tsm.domain.data.MarkNavigationTarget
import tw.tsm.domain.data.MarkStepDef
import tw.tsm.domain.data.MarkStepRequirement

/**
 * DVT IOS_MarkStep_C.dat parser.
 *
 * MarkData.ReadStep in the native DVT Lua client is the wire source of truth.
 * Every MarkStep contains 5 condition targets and a distinct completion target;
 * dropping those fields previously prevented server-side parity diagnostics.
 *
 * The file stores NO areaId. Lua initializes it to 0 and the client must derive
 * a navigable area from DoorGroupData; do NOT fabricate area IDs in this parser.
 */
object MarkStepDatLoader {
    fun loadSteps(data: ByteArray): Map<Int, Map<Int, MarkStepDef>> {
        val reader = DatReader(data)
        require(data.size >= 4) { "Invalid MarkStep header" }
        val count = reader.readInt32()
        require(count in 0..100_000) { "Invalid MarkStep count $count" }
        val steps = LinkedHashMap<Int, MutableMap<Int, MarkStepDef>>()

        repeat(count) {
            val missionId = reader.readUInt16()
            val stepNo = reader.readByte()
            val description = reader.readUnicodeString()
            val conditions = List(5) {
                val kind = reader.readByte()
                val id = reader.readUInt16()
                val required = reader.readByte()
                val target = MarkNavigationTarget(
                    sceneId = reader.readUInt16(),
                    x = reader.readUInt16(),
                    y = reader.readUInt16(),
                    eventKind = reader.readByte(),
                    eventId = reader.readByte(),
                )
                MarkStepRequirement(kind, id, required, target)
            }
            val endGuide = reader.readUnicodeString()
            val endTarget = MarkNavigationTarget(
                sceneId = reader.readUInt16(),
                x = reader.readUInt16(),
                y = reader.readUInt16(),
                eventKind = reader.readByte(),
                eventId = reader.readByte(),
            )
            val teleport = reader.readByte()
            val checkTeam = reader.readByte() == 1

            require(missionId > 0) { "Bad MarkStep mission=$missionId" }
            val byStep = steps.getOrPut(missionId) { linkedMapOf() }
            // Match Lua DataManager: a repeated (missionId, step) overwrites
            // the earlier value. Do not crash startup on a duplicate source row.
            byStep[stepNo] =
                MarkStepDef(missionId, stepNo, description, conditions, endGuide, endTarget, teleport, checkTeam)
        }
        require(reader.position() == data.size) {
            "MarkStep cursor=${reader.position()} does not match file=${data.size}"
        }
        return steps
    }

    /** Compatibility for Eve marker missions with bitId but no real step. */
    fun loadMissionIds(data: ByteArray): Set<Int> = loadSteps(data).keys
}
