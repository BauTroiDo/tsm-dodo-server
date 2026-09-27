package tw.tsm.protocol

import tw.tsm.domain.constants.EHuman

/**
 * 戰鬥角色封包序列化 — 對應 FightField.RoleAppear 的讀取格式
 *
 * 負責將戰鬥角色資料序列化為 S:011-250 / S:011-005 的二進位格式。
 * 格式來源：FightField.lua RoleAppear() + FightManager.lua ReciveFightAppearenceData()
 */
object BattleRoleSerializer {

    /**
     * 序列化單個戰鬥角色
     *
     * @param data 戰鬥角色資料
     * @return 序列化後的 bytes
     */
    fun serialize(data: BattleRoleData): ByteArray {
        val writer = PacketWriter(128)

        // 基礎資料（所有角色種類共用）
        writer
            .writeByte(data.warType)           // 戰鬥形式 (1B)
            .writeByte(data.humanKind)         // 角色種類 EHuman (1B)
            .writeLongLE(data.roleId)          // 角色 ID (8B)
            .writeShortLE(data.npcId)          // NPC ID (2B)
            .writeLongLE(data.masterId)        // 主人 ID (8B)
            .writeByte(data.col)               // 列 (1B)
            .writeByte(data.row)               // 行 (1B)
            .writeUIntLE(data.maxHp)           // 最大 HP (4B)
            .writeUIntLE(data.maxSp)           // 最大 SP (4B)
            .writeUIntLE(data.hp)              // 當前 HP (4B)
            .writeUIntLE(data.sp)              // 當前 SP (4B)
            .writeShortLE(data.level)          // 等級 (2B)
            .writeByte(data.upgradeLv)         // 升級等級 (1B)
            .writeByte(data.element)           // 元素 (1B)

        // 條件式外觀資料
        if (data.humanKind == EHuman.PLAYER || data.humanKind == EHuman.PLAYERS ||
            data.humanKind == EHuman.DIVIDE || data.humanKind == EHuman.AUTOMANUAL_PLAYER) {
            // 玩家外觀：ReciveFightAppearenceData
            writePlayerAppearance(writer, data)
        } else if (data.humanKind == EHuman.FOLLOW_NPC || data.humanKind == EHuman.AUTOMANUAL_NPC) {
            // 武將名字 (UTF-8)
            writer.writeUtf8WithByteLen(data.name)
        }
        // RoleAppear always consumes one trailing UInt16 after every fighter,
        // regardless of EHuman kind. DVT uses it as soulEquipId.
        writer.writeShortLE(0)

        return writer.toByteArray()
    }

    /**
     * 序列化所有戰鬥角色
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
     * 寫入玩家外觀資料（ReciveFightAppearenceData 格式）
     */
    private fun writePlayerAppearance(writer: PacketWriter, data: BattleRoleData) {
        writer
            .writeUtf8WithByteLen(data.name)     // 名字 (ByteL+UTF-8)
            .writeByte(data.sex)                  // 性別 (1B)
            .writeByte(data.element)              // 屬性 (1B)
            .writeByte(data.level)                // 等級 (1B)
            .writeByte(0)                         // 衰神標記 (1B)
            .writeUIntLE(0)                       // 福神標記 (4B)
            .writeShortLE(0)                      // 精靈 No (2B)
            .writeByte(data.face)                 // 臉圖 (1B)
            .writeByte(data.hair)                 // 頭型 (1B)
            .writeByte(0)                         // 身型 (1B)
            .writeIntLE(data.color1)              // 色盤一 (4B)
            .writeIntLE(data.color2)              // 色盤二 (4B)
            .writeShortLE(0)                      // PVP 勝場 (2B)
            .writeByte(0)                         // PVP 名次 (1B)
            .writeByte(0)                         // NPC 挑戰賽 (1B)
            .writeByte(data.turn)                 // 轉生種類 (1B)
            .writeByte(data.career)               // 職業種類 (1B)

        // 裝備
        val equips = data.equipItemIds
        writer.writeByte(equips.size)             // 裝備數量 (1B)
        for (itemId in equips) {
            writer.writeShortLE(itemId)           // 裝備 ID (2B)
        }

        // 外顯裝備
        val outfits = data.outfitItemIds
        writer.writeByte(outfits.size)            // 外顯裝備數量 (1B)
        for (itemId in outfits) {
            writer.writeShortLE(itemId)           // 外顯 ID (2B)
        }

        writer
            .writeByte(0)                         // 組隊狀態 (1B)
            .writeIntLE(0)                        // 軍團 ID (4B)
    }
}

/**
 * 戰鬥角色序列化資料
 *
 * 從 BattleFighter 或 GameSession 轉換而來。
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
