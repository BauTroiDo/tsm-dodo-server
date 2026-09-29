package tw.tsm.data.loaders

import tw.tsm.data.DatReader
import tw.tsm.domain.data.NpcDef

/**
 * Loader NPC — tương ứng `DataManager.OnLoadNpcData`
 *
 * Nguồn: `Npc_C.dat`
 * Định dạng: count(Int32) + count bản ghi NpcData.New(reader)
 * Key = NPC ID (chỉ lưu khi id != 0)
 *
 * Thứ tự trường đối chiếu nghiêm ngặt dòng 236-305 của NpcData.lua.
 * Role.maxNpcSkill = 3。
 */
object NpcDatLoader {

    private const val MAX_NPC_SKILL = 3

    /**
     * Đọc dữ liệu NPC
     */
    fun load(data: ByteArray): Map<Int, NpcDef> {
        val reader = DatReader(data)
        val count = reader.readInt32()
        val result = LinkedHashMap<Int, NpcDef>(count)

        for (i in 0 until count) {
            val name = reader.readUnicodeString()        // [1] Tên
            val kind = reader.readByte()                  // [2] Loại
            val id = reader.readUInt16()                  // [3] Số hiệu
            val picId = reader.readUInt16()                // [4] Ảnh nhân vật
            val maskId = reader.readUInt16()               // [5] Ảnh che (mask)

            val colorTints = IntArray(4) {
                reader.readInt32()                         // [6-9] Bảng màu
            }

            val canBeCatch = reader.readByte() == 1        // [10]
            val bodyKind = reader.readByte()               // [11]
            val weaponKind = reader.readByte()             // [12]
            val level = reader.readByte()                  // [13]
            val hpBase = reader.readInt32()                // [14]
            val spBase = reader.readInt32()                // [15]

            // [16-21] Thuộc tính: hpx, spx, int, atk, def, agi
            val hpx = reader.readUInt16()
            val spx = reader.readUInt16()
            val intelligence = reader.readUInt16()
            val atk = reader.readUInt16()
            val def = reader.readUInt16()
            val agi = reader.readUInt16()

            val moral = reader.readByte()                  // [22]
            val moralValue = reader.readUInt16()           // [23]
            val element = reader.readByte()                // [24]

            val skills = IntArray(MAX_NPC_SKILL) {
                reader.readUInt16()                         // [25-27]
            }

            val specialSkill = reader.readUInt16()         // [35]
            val turn = reader.readByte()                   // [36]
            val passiveSkill = reader.readUInt16()          // [38]
            val passiveSkillLv = reader.readByte()          // [39]
            val saddleKind = reader.readUInt16()            // [40]
            val upgradeItemId = reader.readUInt16()         // [41]
            val upgradeSkill = reader.readUInt16()          // [42]

            val limitRaw = reader.readUInt16()              // [43]
            val limits = intArrayOf(limitRaw and 0xFF, limitRaw shr 8)

            val rideOffsetH = reader.readUInt16() - 1000    // [44]
            val picOffsetX = reader.readUInt16() - 1000     // [45]
            val picOffsetY = reader.readUInt16() - 1000     // [46]
            val hudOffsetH = reader.readUInt16() - 1000     // [47]
            val shadowKind = reader.readByte()              // [48]
            val rare = reader.readByte()                    // [49]

            if (id != 0) {
                result[id] = NpcDef(
                    name = name, kind = kind, picId = picId, maskId = maskId,
                    colorTints = colorTints, canBeCatch = canBeCatch,
                    bodyKind = bodyKind, weaponKind = weaponKind, level = level,
                    hpBase = hpBase, spBase = spBase,
                    hpx = hpx, spx = spx, intelligence = intelligence,
                    atk = atk, def = def, agi = agi,
                    moral = moral, moralValue = moralValue, element = element,
                    skills = skills, specialSkill = specialSkill, turn = turn,
                    passiveSkill = passiveSkill, passiveSkillLv = passiveSkillLv,
                    saddleKind = saddleKind, upgradeItemId = upgradeItemId,
                    upgradeSkill = upgradeSkill, limits = limits,
                    rideOffsetH = rideOffsetH, picOffsetX = picOffsetX,
                    picOffsetY = picOffsetY, hudOffsetH = hudOffsetH,
                    shadowKind = shadowKind, rare = rare
                )
            }
        }

        return result
    }
}
