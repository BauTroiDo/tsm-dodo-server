package tw.tsm.app.response

import tw.tsm.domain.constants.EAttribute
import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.interfaces.SessionSender
import tw.tsm.domain.valueobjects.EquipBonusData
import tw.tsm.protocol.PacketWriter

/**
 * 戰鬥/屬性/技能/PK 相關封包序列化
 *
 * mainKind = 8（屬性/技能）, 11（戰鬥管理）, 33（PK 設定）, 50（技能結果）,
 *            51（屬性變化）, 52（倒數）, 53（狀態回復/掉寶）
 */
class BattleResponseSender {

    // ── 屬性修改 (mainKind=8) ──

    /** S:008-002: absolute attribute value for the owner's follow pet. */
    fun sendFollowNpcAttribute(session: SessionSender, slot: Int, attrKind: Int, value: Int, arg: Int) {
        session.send(PacketWriter(16)
            .writeByte(8).writeByte(2).writeByte(0)
            .writeByte(4).writeShortLE(slot).writeByte(attrKind).writeByte(1)
            .writeIntLE(value).writeIntLE(arg).toByteArray())
    }

    /**
     * S:008-001 主角屬性修改
     * 格式：+attrKind(1) +sign(1) +value(4LE) +arg(4LE)
     */
    fun sendSetAttribute(session: SessionSender, attrKind: Int, value: Int, isPositive: Boolean, arg: Int) {
        val writer = PacketWriter(14)
            .writeByte(8).writeByte(1).writeByte(0)
            .writeByte(attrKind)
            .writeByte(if (isPositive) 1 else 2)
            .writeIntLE(value)
            .writeIntLE(arg)
        session.send(writer.toByteArray())
    }

    /**
     * 發送裝備加成更新（8 筆 S:008-001 + MaxHP/HP/MaxSP/SP）
     *
     * 參考 tskero `TSCharacter.refreshBonus()` — 穿脫裝備後逐筆發送裝備屬性值，
     * 客戶端收到 EquipMaxHp(207)/EquipHpx(218) 時會自動重算 MaxHP。
     */
    fun sendEquipBonusUpdate(
        session: SessionSender,
        bonus: EquipBonusData,
        maxHp: Long,
        maxSp: Int,
        hp: Long,
        sp: Int
    ) {
        // 8 筆裝備屬性（客戶端自動觸發 RefreshMaxHP/RefreshMaxSP）
        sendSetAttribute(session, EAttribute.EQUIP_MAX_HP, bonus.equipMaxHp, true, 0)
        sendSetAttribute(session, EAttribute.EQUIP_MAX_SP, bonus.equipMaxSp, true, 0)
        sendSetAttribute(session, EAttribute.EQUIP_ATK, bonus.equipAtk, true, 0)
        sendSetAttribute(session, EAttribute.EQUIP_DEF, bonus.equipDef, true, 0)
        sendSetAttribute(session, EAttribute.EQUIP_INT, bonus.equipInt, true, 0)
        sendSetAttribute(session, EAttribute.EQUIP_AGI, bonus.equipAgi, true, 0)
        sendSetAttribute(session, EAttribute.EQUIP_HPX, bonus.equipHpx, true, 0)
        sendSetAttribute(session, EAttribute.EQUIP_SPX, bonus.equipSpx, true, 0)

        // 補發 HP/SP 絕對值，確保與伺服器同步（參考 tskero refreshBonus 最後也發 HP/SP）
        sendSetAttribute(session, EAttribute.MAX_HP, maxHp.toInt(), true, 0)
        sendSetAttribute(session, EAttribute.HP, hp.toInt(), true, 0)
        sendSetAttribute(session, EAttribute.MAX_SP, maxSp, true, 0)
        sendSetAttribute(session, EAttribute.SP, sp, true, 0)
    }

    /**
     * S:008-013 主角技能資料
     *
     * 格式：+element(1) +skillPoint(2LE) +skillCount(2LE) <<+skillId(2LE) +level(1)>>
     */
    fun sendPlayerSkillData(session: SessionSender, element: Int, skillPoint: Int, skills: Map<Int, Int>) {
        val writer = PacketWriter(3 + 1 + 2 + 2 + skills.size * 3)
            .writeByte(8).writeByte(13).writeByte(0)
            .writeByte(element)
            .writeShortLE(skillPoint)
            .writeShortLE(skills.size)
        for ((skillId, level) in skills) {
            writer.writeShortLE(skillId)
            writer.writeByte(level)
        }
        session.send(writer.toByteArray())
    }

    /** S:023-077 天珠資訊。 */
    fun sendTurn2Balls(session: SessionSender, available: Int, active: Set<Int>) {
        val writer = PacketWriter(4 + active.size)
            .writeByte(23).writeByte(77).writeByte(0)
            .writeByte(available.coerceIn(0, 255))
        active.filter { it in 1..12 }.sorted().forEach { writer.writeByte(it) }
        session.send(writer.toByteArray())
    }

    /** S:023-078 二轉技能槽；wire index 1..8，client 讀取後 +5。 */
    fun sendTurn2HeroSkills(session: SessionSender, heroSkills: Map<Int, Int>) {
        val valid = heroSkills.filterKeys { it in 6..13 }.toSortedMap()
        val writer = PacketWriter(3 + valid.size * 3)
            .writeByte(23).writeByte(78).writeByte(0)
        valid.forEach { (slot, skillId) ->
            writer.writeByte(slot - 5)
            writer.writeShortLE(skillId)
        }
        session.send(writer.toByteArray())
    }

    // ── 戰鬥管理 (mainKind=11) ──

    /**
     * S:011-250 戰鬥初始化
     *
     * 格式：+terrain(1) +eventNo(2LE) +<<RoleAppear 資料>>
     */
    fun sendBattleInit(session: SessionSender, terrain: Int, eventNo: Int, roleData: ByteArray) {
        val writer = PacketWriter(6 + roleData.size)
            .writeByte(Opcodes.BATTLE_MANAGE).writeByte(250).writeByte(0)
            .writeByte(terrain)
            .writeShortLE(eventNo)
        session.send(writer.toByteArray() + roleData)
    }

    /** S:011-000 結束戰鬥 */
    fun sendBattleEnd(session: SessionSender, playerId: Long, npcIndex: Int) {
        val writer = PacketWriter(13)
            .writeByte(Opcodes.BATTLE_MANAGE).writeByte(0).writeByte(0)
            .writeLongLE(playerId)
            .writeShortLE(npcIndex)
        session.send(writer.toByteArray())
    }

    /** S:011-001 角色離開戰鬥 */
    fun sendBattleExitFight(session: SessionSender, col: Int, row: Int) {
        val writer = PacketWriter(5)
            .writeByte(Opcodes.BATTLE_MANAGE).writeByte(1).writeByte(0)
            .writeByte(col)
            .writeByte(row)
        session.send(writer.toByteArray())
    }

    /** S:011-003 戰鬥錯誤訊息 */
    fun sendBattleError(session: SessionSender, errorKind: Int) {
        val writer = PacketWriter(4)
            .writeByte(Opcodes.BATTLE_MANAGE).writeByte(3).writeByte(0)
            .writeByte(errorKind)
        session.send(writer.toByteArray())
    }

    /**
     * S:011-004 場景玩家戰鬥類型更新
     *
     * 格式：<<+角色種類(1) +玩家ID(8 LE) +NPCID(2 LE) +戰鬥類型(1)>>
     */
    fun sendSceneWarTypeUpdate(session: SessionSender, entries: ByteArray) {
        val writer = PacketWriter(3)
            .writeByte(Opcodes.BATTLE_MANAGE).writeByte(4).writeByte(0)
        session.send(writer.toByteArray() + entries)
    }

    /**
     * S:011-005 參戰角色資訊（戰場舊玩家收到新參戰角色）
     *
     * 格式：<<+戰鬥角色資料(?)>> — 同 BattleRoleData 格式
     */
    fun sendBattleJoinRoleAppear(session: SessionSender, roleData: ByteArray) {
        val writer = PacketWriter(3)
            .writeByte(Opcodes.BATTLE_MANAGE).writeByte(5).writeByte(0)
        session.send(writer.toByteArray() + roleData)
    }

    /** S:011-010 戰場類型 */
    fun sendBattleFieldType(session: SessionSender, warStyle: Int, rounds: Int, limitType: Int, limitSwitch: Int) {
        val writer = PacketWriter(11)
            .writeByte(Opcodes.BATTLE_MANAGE).writeByte(10).writeByte(0)
            .writeByte(warStyle)
            .writeShortLE(rounds)
            .writeByte(limitType)
            .writeIntLE(limitSwitch)
        session.send(writer.toByteArray())
    }

    // ── 技能結果 (mainKind=50) ──

    /** S:050-001 技能攻擊結果 */
    fun sendBattleSkillResult(session: SessionSender, data: ByteArray) {
        val writer = PacketWriter(3)
            .writeByte(50).writeByte(1).writeByte(0)
        session.send(writer.toByteArray() + data)
    }

    /** S:050-002 戰鬥使用物品結果 */
    fun sendBattleUseItem(session: SessionSender, col: Int, row: Int, itemId: Int, count: Int, tgtCol: Int, tgtRow: Int) {
        val writer = PacketWriter(10)
            .writeByte(50).writeByte(2).writeByte(0)
            .writeByte(col)
            .writeByte(row)
            .writeShortLE(itemId)
            .writeByte(count)
            .writeByte(tgtCol)
            .writeByte(tgtRow)
        session.send(writer.toByteArray())
    }

    // ── 屬性變化 (mainKind=51) ──

    /** S:051-001 戰鬥中即時屬性修改 */
    fun sendBattleAttrChange(session: SessionSender, data: ByteArray) {
        val writer = PacketWriter(3)
            .writeByte(51).writeByte(1).writeByte(0)
        session.send(writer.toByteArray() + data)
    }

    // ── 倒數 (mainKind=52) ──

    /** S:052-001 戰鬥開始倒數 */
    fun sendBattleCountdown(session: SessionSender) {
        val writer = PacketWriter(3)
            .writeByte(52).writeByte(1).writeByte(0)
        session.send(writer.toByteArray())
    }

    // ── 狀態回復/掉寶 (mainKind=53) ──

    /** S:053-001 戰鬥狀態回復 */
    fun sendBattleRestoreStatus(session: SessionSender, data: ByteArray) {
        val writer = PacketWriter(3)
            .writeByte(53).writeByte(1).writeByte(0)
        session.send(writer.toByteArray() + data)
    }

    /** S:053-003 戰鬥打飛 */
    fun sendBattleFlyOut(session: SessionSender, col: Int, row: Int) {
        val writer = PacketWriter(5)
            .writeByte(53).writeByte(3).writeByte(0)
            .writeByte(col)
            .writeByte(row)
        session.send(writer.toByteArray())
    }

    /**
     * S:053-004 戰鬥掉寶
     *
     * 格式：+物品ID(2) +掉寶列(1) +掉寶行(1) +得寶列(1) +得寶行(1)
     */
    fun sendBattleDrop(session: SessionSender, itemId: Int, fromCol: Int, fromRow: Int, toCol: Int, toRow: Int) {
        val writer = PacketWriter(9)
            .writeByte(53).writeByte(4).writeByte(0)
            .writeShortLE(itemId)
            .writeByte(fromCol)
            .writeByte(fromRow)
            .writeByte(toCol)
            .writeByte(toRow)
        session.send(writer.toByteArray())
    }

    // ── PK 設定 (mainKind=33) ──

    /** S:033-001 要求 PK 回應 */
    fun sendPkResult(session: SessionSender, resultCode: Int) {
        val writer = PacketWriter(4)
            .writeByte(Opcodes.PK_SETTING).writeByte(1).writeByte(0)
            .writeByte(resultCode)
        session.send(writer.toByteArray())
    }

    /** S:033-002 設定 PVP 開關 */
    fun sendPkSwitch(session: SessionSender, pkEnabled: Boolean, joinEnabled: Boolean) {
        val writer = PacketWriter(5)
            .writeByte(Opcodes.PK_SETTING).writeByte(2).writeByte(0)
            .writeByte(if (pkEnabled) 1 else 0)
            .writeByte(if (joinEnabled) 1 else 0)
        session.send(writer.toByteArray())
    }

    /** S:023-069 玩家轉職/轉生外觀同步。 */
    fun sendPlayerCareerChange(
        session: SessionSender,
        playerId: Long,
        hair: Int,
        color1: Int,
        color2: Int,
        turn: Int,
        career: Int
    ) {
        session.send(
            PacketWriter()
                .writeByte(23).writeByte(69).writeByte(0)
                .writeLongLE(playerId)
                .writeByte(hair)
                .writeIntLE(color1)
                .writeIntLE(color2)
                .writeByte(turn)
                .writeByte(career)
                .toByteArray()
        )
    }
}
