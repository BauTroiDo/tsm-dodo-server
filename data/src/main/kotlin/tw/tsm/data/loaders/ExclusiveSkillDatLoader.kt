package tw.tsm.data.loaders

import tw.tsm.data.DatReader
import tw.tsm.domain.data.ExclusiveSkillDef
import tw.tsm.domain.data.ExclusiveSkillEffectDef

/**
 * Loader for DVT IOS_ExclusiveSkill_C.dat.
 *
 * Binary contract: count(Int32) + count * 27-byte records.
 * Field semantics are deliberately kept raw until DVT evidence proves them.
 */
object ExclusiveSkillDatLoader {
    fun load(data: ByteArray): Map<Int, Map<Int, ExclusiveSkillDef>> {
        val reader = DatReader(data)
        val count = reader.readInt32()
        val bySkill = linkedMapOf<Int, MutableMap<Int, ExclusiveSkillDef>>()

        repeat(count) {
            val skillId = reader.readUInt16()
            val level = reader.readByte()
            val headerRaw = List(6) { reader.readByte() }
            val effects = List(2) {
                ExclusiveSkillEffectDef(
                    kind = reader.readByte(),
                    arg1 = reader.readByte(),
                    arg2 = reader.readByte(),
                    parameter = reader.readUInt16(),
                    value = reader.readUInt32(),
                )
            }

            if (skillId != 0) {
                val levels = bySkill.getOrPut(skillId) { linkedMapOf() }
                check(levels.put(level, ExclusiveSkillDef(skillId, level, headerRaw, effects)) == null) {
                    "Duplicate IOS_ExclusiveSkill_C.dat record: skill=$skillId level=$level"
                }
            }
        }

        return bySkill.mapValues { (_, levels) -> levels.toMap() }
    }
}
