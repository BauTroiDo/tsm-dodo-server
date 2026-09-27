package tw.tsm.data.loaders

import tw.tsm.data.DatReader
import tw.tsm.domain.data.SkillDef

/**
 * 技能載入器 — 對應 `DataManager.OnLoadSkillData`
 *
 * 來源：`Skill_C.dat`
 * 格式：count(Int32) + count 筆 SkillData.New(reader)
 * Key = 技能 ID（id != 0 才存入）
 *
 * 欄位順序嚴格對照 SkillData.lua 第 4-63 行。
 */
object SkillDatLoader {

    /**
     * 載入技能資料
     */
    fun load(data: ByteArray): Map<Int, SkillDef> {
        val reader = DatReader(data)
        val count = reader.readInt32()
        val result = LinkedHashMap<Int, SkillDef>(count)

        for (i in 0 until count) {
            val name = reader.readUnicodeString()
            val kind = reader.readByte()
            val id = reader.readUInt16()
            val requireSp = reader.readUInt16()
            val element = reader.readByte()
            val numerical = reader.readUInt32()
            val attribute = reader.readByte()
            val level = reader.readByte()
            val fightWay = reader.readByte()
            val fightArea = reader.readByte()
            val round = reader.readByte()
            val spendSecond = reader.readByte()
            val hitStatus = reader.readByte()
            val howMuchTimes = reader.readByte()
            val limitLv = reader.readByte()
            val learnPoint = reader.readByte()
            val levelUpPoint = reader.readByte()
            val maxLv = reader.readByte()

            val preSkills = mutableListOf<Int>()
            val preSkillId1 = reader.readUInt16()
            if (preSkillId1 > 0) preSkills.add(preSkillId1)

            val atkKind = reader.readUInt16()
            val turnKind = reader.readByte()

            val preSkillId2 = reader.readUInt16()
            if (preSkillId2 > 0) preSkills.add(preSkillId2)

            val learnLimit = reader.readByte()
            val useLimit = reader.readUInt16()
            val fightWayGrowType = reader.readUInt16()
            val description = reader.readUnicodeString()

            // 特殊硬編碼前置技能（對照 Lua 54-61 行）
            if (id == 13014) preSkills.add(13012)
            if (id == 14038) {
                preSkills.addAll(listOf(10020, 11020, 12020, 13019))
            }

            if (id != 0) {
                result[id] = SkillDef(
                    name = name, kind = kind, requireSp = requireSp,
                    element = element, numerical = numerical, attribute = attribute,
                    level = level, fightWay = fightWay, fightArea = fightArea,
                    round = round, spendSecond = spendSecond, hitStatus = hitStatus,
                    howMuchTimes = howMuchTimes, limitLv = limitLv,
                    learnPoint = learnPoint, levelUpPoint = levelUpPoint,
                    maxLv = maxLv, preSkills = preSkills,
                    atkKind = atkKind, turnKind = turnKind,
                    learnLimit = learnLimit, useLimit = useLimit,
                    fightWayGrowType = fightWayGrowType, description = description
                )
            }
        }

        return result
    }
}
