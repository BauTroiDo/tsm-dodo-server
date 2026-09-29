package tw.tsm.protocol

import tw.tsm.domain.constants.EHuman

/**
 * Tuần tự hoá gói tin nhân vật chiến đấu — tương ứng định dạng đọc của FightField.RoleAppear
 *
 * Chịu trách nhiệm tuần tự hoá dữ liệu nhân vật chiến đấu sang định dạng nhị phân S:011-250 / S:011-005.
 * Nguồn định dạng: FightField.lua RoleAppear() + FightManager.lua ReciveFightAppearenceData()
 */
object BattleRoleSerializer {

    /**
     * Tuần tự hoá một nhân vật chiến đấu
     *
     * @param data dữ liệu nhân vật chiến đấu
     * @return bytes đã được tuần tự hoá
     */
    fun serialize(data: BattleRoleData): ByteArray {
        val writer = PacketWriter(128)

        // Dữ liệu cơ sở (mọi loại nhân vật dùng chung)
        writer
            .writeByte(data.warType)           // Hình thức chiến đấu (1B)
            .writeByte(data.humanKind)         // Loại nhân vật EHuman (1B)
            .writeLongLE(data.roleId)          // ID nhân vật (8B)
            .writeShortLE(data.npcId)          // NPC ID (2B)
            .writeLongLE(data.masterId)        // ID chủ nhân (8B)
            .writeByte(data.col)               // Cột (1B)
            .writeByte(data.row)               // Hàng (1B)
            .writeUIntLE(data.maxHp)           // HP tối đa (4B)
            .writeUIntLE(data.maxSp)           // SP tối đa (4B)
            .writeUIntLE(data.hp)              // HP hiện tại (4B)
            .writeUIntLE(data.sp)              // SP hiện tại (4B)
            .writeShortLE(data.level)          // Cấp độ (2B)
            .writeByte(data.upgradeLv)         // Cấp tăng cấp (1B)
            .writeByte(data.element)           // Nguyên tố (1B)

        // Dữ liệu ngoại hình có điều kiện
        if (data.humanKind == EHuman.PLAYER || data.humanKind == EHuman.PLAYERS ||
            data.humanKind == EHuman.DIVIDE || data.humanKind == EHuman.AUTOMANUAL_PLAYER) {
            // Ngoại hình người chơi: ReciveFightAppearenceData
            writePlayerAppearance(writer, data)
        } else if (data.humanKind == EHuman.FOLLOW_NPC || data.humanKind == EHuman.AUTOMANUAL_NPC) {
            // Tên tướng (UTF-8)
            writer.writeUtf8WithByteLen(data.name)
        }
        // RoleAppear always consumes one trailing UInt16 after every fighter,
        // regardless of EHuman kind. DVT uses it as soulEquipId.
        writer.writeShortLE(0)

        return writer.toByteArray()
    }

    /**
     * Tuần tự hoá tất cả nhân vật chiến đấu
     */
    fun serializeAll(roles: List<BattleRoleData>): ByteArray {
        val parts = roles.map { serialize(it) }
        val totalSize = parts.sumOf { it.size }
        val result = ByteArray(totalSize)
        var offset = 0
        for (part in parts) {
            part.copyInto(result, offset)
            offset += part.size
        }
        return result
    }

    /**
     * Ghi dữ liệu ngoại hình người chơi (định dạng ReciveFightAppearenceData)
     */
    private fun writePlayerAppearance(writer: PacketWriter, data: BattleRoleData) {
        writer
            .writeUtf8WithByteLen(data.name)     // Tên (ByteL+UTF-8)
            .writeByte(data.sex)                  // Giới tính (1B)
            .writeByte(data.element)              // Thuộc tính (1B)
            .writeByte(data.level)                // Cấp độ (1B)
            .writeByte(0)                         // Cờ suy thần (1B)
            .writeUIntLE(0)                       // Cờ phúc thần (4B)
            .writeShortLE(0)                      // Số tinh linh (2B)
            .writeByte(data.face)                 // Ảnh mặt (1B)
            .writeByte(data.hair)                 // Kiểu đầu (1B)
            .writeByte(0)                         // Dáng người (1B)
            .writeIntLE(data.color1)              // Bảng màu một (4B)
            .writeIntLE(data.color2)              // Bảng màu hai (4B)
            .writeShortLE(0)                      // Số trận PVP thắng (2B)
            .writeByte(0)                         // Thứ hạng PVP (1B)
            .writeByte(0)                         // Giải thách đấu NPC (1B)
            .writeByte(data.turn)                 // Loại chuyển sinh (1B)
            .writeByte(data.career)               // Loại chức nghiệp (1B)

        // trang bị
        val equips = data.equipItemIds
        writer.writeByte(equips.size)             // Số lượng trang bị (1B)
        for (itemId in equips) {
            writer.writeShortLE(itemId)           // ID trang bị (2B)
        }

        // Trang bị hiển thị
        val outfits = data.outfitItemIds
        writer.writeByte(outfits.size)            // Số lượng trang bị hiển thị (1B)
        for (itemId in outfits) {
            writer.writeShortLE(itemId)           // ID hiển thị (2B)
        }

        writer
            .writeByte(0)                         // Trạng thái tổ đội (1B)
            .writeIntLE(0)                        // ID quân đoàn (4B)
    }
}

/**
 * Dữ liệu tuần tự hoá nhân vật chiến đấu
 *
 * Được chuyển đổi từ BattleFighter hoặc GameSession.
 */
data class BattleRoleData(
    val warType: Int,
    val humanKind: Int,
    val roleId: Long,
    val npcId: Int,
    val masterId: Long,
    val col: Int,
    val row: Int,
    val maxHp: Long,
    val maxSp: Long,
    val hp: Long,
    val sp: Long,
    val level: Int,
    val upgradeLv: Int = 0,
    val element: Int,
    val name: String,
    val sex: Int = 0,
    val face: Int = 0,
    val hair: Int = 0,
    val color1: Int = 0,
    val color2: Int = 0,
    val turn: Int = 0,
    val career: Int = 0,
    val equipItemIds: IntArray = intArrayOf(),
    val outfitItemIds: IntArray = intArrayOf()
)
