package tw.tsm.app.response

import tw.tsm.domain.constants.EAttribute
import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.interfaces.SessionSender
import tw.tsm.domain.valueobjects.EquipBonusData
import tw.tsm.protocol.PacketWriter

/**
 * tuần tự hoá gói tin liên quan chiến đấu/thuộc tính/kỹ năng/PK
 *
 * mainKind = 8 (thuộc tính/kỹ năng), 11 (quản lý chiến đấu), 33 (cài đặt PK), 50 (kết quả kỹ năng),
 *            51 (thay đổi thuộc tính), 52 (đếm ngược), 53 (hồi phục trạng thái/rơi đồ)
 */
class BattleResponseSender {

    // ── Sửa thuộc tính (mainKind=8) ──

    /** S:008-002: absolute attribute value for the owner's follow pet. */
    fun sendFollowNpcAttribute(session: SessionSender, slot: Int, attrKind: Int, value: Int, arg: Int) {
        session.send(PacketWriter(16)
            .writeByte(8).writeByte(2).writeByte(0)
            .writeByte(4).writeShortLE(slot).writeByte(attrKind).writeByte(1)
            .writeIntLE(value).writeIntLE(arg).toByteArray())
    }

    /**
     * S:008-001 sửa thuộc tính nhân vật chính
     * Định dạng: +attrKind(1) +sign(1) +value(4LE) +arg(4LE)
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
     * S:008-014 thuộc tính thế giới nhân vật chính.
     * DVT: attrKind Byte + sign Byte + value Int64 + arg Int32.
     */
    fun sendWorldAttribute(session: SessionSender, attrKind: Int, value: Long, isPositive: Boolean, arg: Int) {
        val writer = PacketWriter(17)
            .writeByte(8).writeByte(14).writeByte(0)
            .writeByte(attrKind)
            .writeByte(if (isPositive) 1 else 2)
            .writeLongLE(value)
            .writeIntLE(arg)
        session.send(writer.toByteArray())
    }

    /**
     * Gửi cập nhật chỉ số cộng thêm của trang bị (8 gói S:008-001 + MaxHP/HP/MaxSP/SP)
     *
     * tham khảo tskero `TSCharacter.refreshBonus()` — sau khi mặc/tháo trang bị gửi từng giá trị thuộc tính trang bị,
     * client nhận được EquipMaxHp(207)/EquipHpx(218) sẽ tự tính lại MaxHP.
     */
    fun sendEquipBonusUpdate(
        session: SessionSender,
        bonus: EquipBonusData,
        maxHp: Long,
        maxSp: Int,
        hp: Long,
        sp: Int
    ) {
        // 8 thuộc tính trang bị (client tự kích hoạt RefreshMaxHP/RefreshMaxSP)
        sendSetAttribute(session, EAttribute.EQUIP_MAX_HP, bonus.equipMaxHp, true, 0)
        sendSetAttribute(session, EAttribute.EQUIP_MAX_SP, bonus.equipMaxSp, true, 0)
        sendSetAttribute(session, EAttribute.EQUIP_ATK, bonus.equipAtk, true, 0)
        sendSetAttribute(session, EAttribute.EQUIP_DEF, bonus.equipDef, true, 0)
        sendSetAttribute(session, EAttribute.EQUIP_INT, bonus.equipInt, true, 0)
        sendSetAttribute(session, EAttribute.EQUIP_AGI, bonus.equipAgi, true, 0)
        sendSetAttribute(session, EAttribute.EQUIP_HPX, bonus.equipHpx, true, 0)
        sendSetAttribute(session, EAttribute.EQUIP_SPX, bonus.equipSpx, true, 0)

        // DVT Mobile advanced attributes are direct percentages (not Equip* shadow attrs).
        sendSetAttribute(session, EAttribute.ATK_PERCENT, bonus.atkPercent, true, 0)
        sendSetAttribute(session, EAttribute.DEF_PERCENT, bonus.defPercent, true, 0)
        sendSetAttribute(session, EAttribute.HP_PERCENT, bonus.hpPercent, true, 0)
        sendSetAttribute(session, EAttribute.INT_PERCENT, bonus.intPercent, true, 0)
        sendSetAttribute(session, EAttribute.CRITICAL, bonus.crit, true, 0)
        sendSetAttribute(session, EAttribute.CRIT_DAMAGE, bonus.critDamage, true, 0)
        sendSetAttribute(session, EAttribute.DODGE, bonus.dodge, true, 0)
        sendSetAttribute(session, EAttribute.HIT, bonus.hit, true, 0)
        sendSetAttribute(session, EAttribute.INCREASE_TOTAL_DAMAGE, bonus.increaseTotalDamage, true, 0)
        sendSetAttribute(session, EAttribute.DAMAGE_REDUCTION, bonus.damageReduction, true, 0)
        sendSetAttribute(session, EAttribute.REDUCE_TOTAL_DAMAGE, bonus.reduceTotalDamage, true, 0)
        sendSetAttribute(session, EAttribute.INCREASE_DAMAGE_TAKEN_SELF, bonus.increaseDamageTakenSelf, true, 0)

        // gửi bổ sung giá trị tuyệt đối HP/SP, đảm bảo đồng bộ với server (tham khảo tskero refreshBonus cuối cùng cũng gửi HP/SP)
        sendSetAttribute(session, EAttribute.MAX_HP, maxHp.toInt(), true, 0)
        sendSetAttribute(session, EAttribute.HP, hp.toInt(), true, 0)
        sendSetAttribute(session, EAttribute.MAX_SP, maxSp, true, 0)
        sendSetAttribute(session, EAttribute.SP, sp, true, 0)
    }

    /**
     * S:008-013 dữ liệu kỹ năng nhân vật chính
     *
     * Định dạng: +element(1) +skillPoint(2LE) +skillCount(2LE) <<+skillId(2LE) +level(1)>>
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

    /** S:008-012 kết quả thiết lập nguyên tố sáng/tối chuyển sinh 3.*/
    fun sendTurn3ElementResult(session: SessionSender, result: Int) {
        session.send(
            PacketWriter()
                .writeByte(8).writeByte(12).writeByte(0)
                .writeByte(result)
                .toByteArray()
        )
    }

    /** S:023-077 thông tin Thiên Chu.*/
    fun sendTurn2Balls(session: SessionSender, available: Int, active: Set<Int>) {
        val writer = PacketWriter(4 + active.size)
            .writeByte(23).writeByte(77).writeByte(0)
            .writeByte(available.coerceIn(0, 255))
        active.filter { it in 1..12 }.sorted().forEach { writer.writeByte(it) }
        session.send(writer.toByteArray())
    }

    /** S:023-078 ô kỹ năng nhị chuyển; wire index 1..8, client đọc xong +5.*/
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

    // ── Quản lý chiến đấu (mainKind=11) ──

    /**
     * S:011-250 khởi tạo chiến đấu
     *
     * Định dạng: +terrain(1) +eventNo(2LE) +<<dữ liệu RoleAppear>>
     */
    fun sendBattleInit(session: SessionSender, terrain: Int, eventNo: Int, roleData: ByteArray) {
        val writer = PacketWriter(6 + roleData.size)
            .writeByte(Opcodes.BATTLE_MANAGE).writeByte(250).writeByte(0)
            .writeByte(terrain)
            .writeShortLE(eventNo)
        session.send(writer.toByteArray() + roleData)
    }

    /** S:011-000 kết thúc chiến đấu*/
    fun sendBattleEnd(session: SessionSender, playerId: Long, npcIndex: Int) {
        val writer = PacketWriter(13)
            .writeByte(Opcodes.BATTLE_MANAGE).writeByte(0).writeByte(0)
            .writeLongLE(playerId)
            .writeShortLE(npcIndex)
        session.send(writer.toByteArray())
    }

    /** S:011-001 nhân vật rời chiến đấu*/
    fun sendBattleExitFight(session: SessionSender, col: Int, row: Int) {
        val writer = PacketWriter(5)
            .writeByte(Opcodes.BATTLE_MANAGE).writeByte(1).writeByte(0)
            .writeByte(col)
            .writeByte(row)
        session.send(writer.toByteArray())
    }

    /** S:011-003 thông báo lỗi chiến đấu*/
    fun sendBattleError(session: SessionSender, errorKind: Int) {
        val writer = PacketWriter(4)
            .writeByte(Opcodes.BATTLE_MANAGE).writeByte(3).writeByte(0)
            .writeByte(errorKind)
        session.send(writer.toByteArray())
    }

    /**
     * S:011-004 Cập nhật loại chiến đấu của người chơi trong cảnh
     *
     * Định dạng: <<+loại nhân vật(1) +ID người chơi(8 LE) +NPCID(2 LE) +loại chiến đấu(1)>>
     */
    fun sendSceneWarTypeUpdate(session: SessionSender, entries: ByteArray) {
        val writer = PacketWriter(3)
            .writeByte(Opcodes.BATTLE_MANAGE).writeByte(4).writeByte(0)
        session.send(writer.toByteArray() + entries)
    }

    /**
     * S:011-005 Thông tin nhân vật tham chiến (người chơi cũ trong chiến trường nhận nhân vật tham chiến mới)
     *
     * Định dạng: <<+dữ liệu nhân vật chiến đấu(?)>> — cùng định dạng BattleRoleData
     */
    fun sendBattleJoinRoleAppear(session: SessionSender, roleData: ByteArray) {
        val writer = PacketWriter(3)
            .writeByte(Opcodes.BATTLE_MANAGE).writeByte(5).writeByte(0)
        session.send(writer.toByteArray() + roleData)
    }

    /** S:011-010 loại chiến trường*/
    fun sendBattleFieldType(session: SessionSender, warStyle: Int, rounds: Int, limitType: Int, limitSwitch: Int) {
        val writer = PacketWriter(11)
            .writeByte(Opcodes.BATTLE_MANAGE).writeByte(10).writeByte(0)
            .writeByte(warStyle)
            .writeShortLE(rounds)
            .writeByte(limitType)
            .writeIntLE(limitSwitch)
        session.send(writer.toByteArray())
    }

    // ── Kết quả kỹ năng (mainKind=50) ──

    /** S:050-001 kết quả kỹ năng tấn công*/
    fun sendBattleSkillResult(session: SessionSender, data: ByteArray) {
        val writer = PacketWriter(3)
            .writeByte(50).writeByte(1).writeByte(0)
        session.send(writer.toByteArray() + data)
    }

    /** S:050-002 kết quả dùng vật phẩm trong chiến đấu*/
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

    // ── Thay đổi thuộc tính (mainKind=51) ──

    /** S:051-001 sửa thuộc tính tức thời trong chiến đấu*/
    fun sendBattleAttrChange(session: SessionSender, data: ByteArray) {
        val writer = PacketWriter(3)
            .writeByte(51).writeByte(1).writeByte(0)
        session.send(writer.toByteArray() + data)
    }

    // ── Đếm ngược (mainKind=52) ──

    /** S:052-001 đếm ngược bắt đầu chiến đấu*/
    fun sendBattleCountdown(session: SessionSender) {
        val writer = PacketWriter(3)
            .writeByte(52).writeByte(1).writeByte(0)
        session.send(writer.toByteArray())
    }

    // ── Hồi phục trạng thái/rơi đồ (mainKind=53) ──

    /** S:053-001 hồi phục trạng thái chiến đấu*/
    fun sendBattleRestoreStatus(session: SessionSender, data: ByteArray) {
        val writer = PacketWriter(3)
            .writeByte(53).writeByte(1).writeByte(0)
        session.send(writer.toByteArray() + data)
    }

    /** S:053-003 đánh bay trong chiến đấu*/
    fun sendBattleFlyOut(session: SessionSender, col: Int, row: Int) {
        val writer = PacketWriter(5)
            .writeByte(53).writeByte(3).writeByte(0)
            .writeByte(col)
            .writeByte(row)
        session.send(writer.toByteArray())
    }

    /**
     * S:053-004 rơi đồ trong chiến đấu
     *
     * Định dạng: +ID vật phẩm(2) +cột rơi đồ(1) +hàng rơi đồ(1) +cột nhận đồ(1) +hàng nhận đồ(1)
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

    // ── Thiết lập PK (mainKind=33) ──

    /** S:033-001 yêu cầu phản hồi PK*/
    fun sendPkResult(session: SessionSender, resultCode: Int) {
        val writer = PacketWriter(4)
            .writeByte(Opcodes.PK_SETTING).writeByte(1).writeByte(0)
            .writeByte(resultCode)
        session.send(writer.toByteArray())
    }

    /** S:033-002 thiết lập công tắc PVP*/
    fun sendPkSwitch(session: SessionSender, pkEnabled: Boolean, joinEnabled: Boolean) {
        val writer = PacketWriter(5)
            .writeByte(Opcodes.PK_SETTING).writeByte(2).writeByte(0)
            .writeByte(if (pkEnabled) 1 else 0)
            .writeByte(if (joinEnabled) 1 else 0)
        session.send(writer.toByteArray())
    }

    /** S:023-069 đồng bộ ngoại hình đổi nghề/chuyển sinh của người chơi.*/
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
