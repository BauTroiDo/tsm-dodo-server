package tw.tsm.app.response

import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.SessionSender
import tw.tsm.domain.interfaces.repositories.CharacterRecord
import tw.tsm.domain.valueobjects.PlayerDataDto
import tw.tsm.protocol.DvtRoleCountWire
import tw.tsm.protocol.PacketWriter

/**
 * Tuần tự hoá gói tin liên quan đăng nhập/nhân vật
 *
 * mainKind = 0（mất kết nối）, 1（đăng nhập）, 3（nhân vật）, 5（dữ liệu người chơi）, 9（tạo nhân vật）, 10（nhịp tim）, 85（RoleCount）, 40（phím tắt）
 */
class LoginResponseSender {

    /**
     * S:001-002 Kết quả đăng nhập
     *
     * Định dạng gói tin:
     * +Result(1) [tiếp tục khi thành công] +DBID(2) +GSID(2) +PlayerId(8)
     * +ServerTime(Double/8) +ServerSwitch(1)
     * +L(1)+Account(Big5) +L(1)+Password(Big5) +AccountKind(1) +NetworkDisconnectMessage(4)
     */
    fun sendLoginResult(
        session: SessionSender,
        resultCode: Int,
        dbId: Int,
        gsId: Int,
        playerId: Long,
        serverTime: Double,
        account: String,
        password: String,
        accountKind: Int
    ) {
        val writer = PacketWriter()
            .writeByte(Opcodes.LOGIN)   // mainKind = 1
            .writeByte(2)               // subKind = 2
            .writeByte(0)               // compress
            .writeByte(resultCode)

        if (resultCode == 0) {
            writer
                .writeShortLE(dbId)             // DBID (UInt16)
                .writeShortLE(gsId)             // GSID (UInt16)
                .writeLongLE(playerId)          // PlayerId (Int64)
                .writeDoubleLE(serverTime)      // ServerTime (Double/8B)
                .writeByte(0)                   // ServerSwitch
                .writeBig5WithByteLen(account)  // Tài khoản
                .writeBig5WithByteLen(password) // Mật khẩu
                .writeByte(accountKind)         // Loại liên kết tài khoản
                .writeIntLE(0)                  // DVT: no network-disconnect message override
        }

        session.send(writer.toByteArray())
    }

    /** S:001-003 Đăng nhập thành công*/
    fun sendLoginSuccess(session: SessionSender, needChangePassword: Boolean) {
        session.send(
            PacketWriter()
                .writeByte(Opcodes.LOGIN).writeByte(3).writeByte(0)
                .writeBoolean(needChangePassword)
                .toByteArray()
        )
    }

    /** S:001-013 Hoàn tất chuỗi đăng nhập*/
    fun sendLoginFinished(session: SessionSender, connectCode: Long) {
        session.send(
            PacketWriter()
                .writeByte(Opcodes.LOGIN).writeByte(13).writeByte(0)
                .writeIntLE(connectCode.toInt())
                .toByteArray()
        )
    }

    /**
     * S:005-003 Gói lớn dữ liệu người chơi
     *
     * Nguồn: protocal.lua protocolTable[5][3] → Role.ReceivePlayerData(data)
     * Ghi vào nghiêm ngặt theo thứ tự Read của client
     */
    fun sendPlayerData(session: SessionSender, data: PlayerDataDto) {
        val writer = PacketWriter(256)
            .writeByte(Opcodes.PLAYER_STATUS)   // mainKind = 5
            .writeByte(3)                        // subKind = 3
            .writeByte(0)                        // compress
            // Thuộc tính cơ bản
            .writeByte(data.element)
            .writeUIntLE(data.hp)
            .writeShortLE(data.sp)
            .writeShortLE(data.intelligence)
            .writeShortLE(data.atk)
            .writeShortLE(data.def)
            .writeShortLE(data.agi)
            .writeShortLE(data.hpx)
            .writeShortLE(data.spx)
            .writeByte(data.level)
            .writeIntLE(data.exp)
            .writeShortLE(data.skillPoint)
            .writeShortLE(data.attrPoint)
            .writeIntLE(data.killNum)
            .writeShortLE(data.battleHonor)
            .writeByte(data.govRequireValue)
            .writeShortLE(data.astrolabePoint)
            .writeUIntLE(data.maxHp)
            .writeShortLE(data.maxSp)
            // Giá trị cộng thêm từ trang bị
            .writeIntLE(data.equipAtk)
            .writeIntLE(data.equipDef)
            .writeIntLE(data.equipInt)
            .writeIntLE(data.equipAgi)
            .writeIntLE(data.equipMaxHp)
            .writeIntLE(data.equipMaxSp)
            .writeIntLE(data.equipHpx)
            .writeIntLE(data.equipSpx)

        // Độ thân thiện phe đảng（5 UInt16）
        for (i in 0 until 5) {
            writer.writeShortLE(data.moral.getOrElse(i) { 0 })
        }

        writer
            .writeUIntLE(data.pracCount)
            .writeUIntLE(data.organization)
            .writeByte(data.autoEatCount)

        // Danh sách kỹ năng chuyển sinh lần đầu
        writer.writeShortLE(data.skills.size)
        for ((skillId, skillLv) in data.skills) {
            writer.writeShortLE(skillId)
            writer.writeByte(skillLv)
        }

        // Dữ liệu chuyển sinh lần 3
        writer
            .writeByte(data.turn3Element)
            .writeLongLE(data.turn3Exp)
            .writeShortLE(data.turn3Int)
            .writeShortLE(data.turn3Atk)
            .writeShortLE(data.turn3Def)
            .writeShortLE(data.turn3Agi)
            .writeShortLE(data.turn3Hpx)
            .writeShortLE(data.turn3Spx)

        // Danh sách kỹ năng chuyển sinh lần 3
        writer.writeShortLE(data.turn3Skills.size)
        for ((skillId, skillLv) in data.turn3Skills) {
            writer.writeShortLE(skillId)
            writer.writeByte(skillLv)
        }

        // DVT Role.ReceivePlayerData reuses the hp/sp locals; no second wire copy.
        // Thêm thuộc tính động
        writer.writeByte(data.newAttrs.size)
        for ((attrType, attrValue) in data.newAttrs) {
            writer.writeByte(attrType)
            writer.writeIntLE(attrValue)
        }

        session.send(writer.toByteArray())
    }

    /** S:005-011 Số lần đan dược*/
    fun sendPillCounts(session: SessionSender, attrPill: Int, hpPill: Int, spPill: Int, skillPill: Int) {
        session.send(
            PacketWriter()
                .writeByte(Opcodes.PLAYER_STATUS).writeByte(11).writeByte(0)
                .writeByte(attrPill)
                .writeByte(hpPill)
                .writeByte(spPill)
                .writeByte(skillPill)
                .toByteArray()
        )
    }

    /** S:003-001 Số ngày đăng nhập*/
    fun sendLoginDay(session: SessionSender, loginDay: Int, continueLogin: Int) {
        session.send(
            PacketWriter()
                .writeByte(Opcodes.CHARACTER).writeByte(1).writeByte(0)
                .writeShortLE(loginDay)
                .writeShortLE(continueLogin)
                .toByteArray()
        )
    }

    /** S:003-002 Thời gian trực tuyến hằng ngày*/
    fun sendOnlineTime(session: SessionSender, time: Double) {
        session.send(
            PacketWriter()
                .writeByte(Opcodes.CHARACTER).writeByte(2).writeByte(0)
                .writeDoubleLE(time)
                .toByteArray()
        )
    }

    /** S:003-003 Thời gian thẻ nửa tháng*/
    fun sendHalfMonthCardTime(session: SessionSender, time: Double) {
        session.send(
            PacketWriter()
                .writeByte(Opcodes.CHARACTER).writeByte(3).writeByte(0)
                .writeDoubleLE(time)
                .toByteArray()
        )
    }

    /**
     * S:003-000 Người chơi xuất hiện
     *
     * Nguồn: protocal.lua protocolTable[3][0] → Role.PlayerAppear(index, data)
     */
    fun sendPlayerAppear(session: SessionSender, playerId: Long, record: CharacterRecord, serverId: Int, turn3Element: Int = 0) {
        val writer = PacketWriter(128)
            .writeByte(Opcodes.CHARACTER).writeByte(0).writeByte(0)
            .writeLongLE(playerId)
            .writeByte(record.sex)
            .writeByte(record.element)
            .writeByte(turn3Element)          // Nguyên tố chuyển sinh lần 3
            .writeByte(record.level)
            .writeByte(0)                     // Cờ đánh dấu suy thần
            .writeUIntLE(0)                   // Cờ đánh dấu phúc thần
            .writeShortLE(0)                  // Số tinh linh
            .writeShortLE(record.mapId)
            .writeShortLE(record.x)
            .writeShortLE(record.y)
            .writeByte(0)                     // Ảnh mặt
            .writeByte(record.hair)
            .writeByte(0)                     // Dáng người
            .writeIntLE(record.color1)
            .writeIntLE(record.color2)
            .writeShortLE(0)                  // Số trận thắng PVP
            .writeByte(0)                     // Hạng PVP
            .writeByte(1)                     // Trạng thái trực tuyến
            .writeByte(0)                     // Giải thách đấu NPC
            .writeByte(serverId)
            .writeByte(record.turn)           // Loại chuyển sinh
            .writeByte(record.career)         // Loại nghề nghiệp
            .writeBig5WithByteLen(record.name)
            .writeShortLE(0)                  // Phân khu
            .writeShortLE(0)                  // ID NPC vật cưỡi
            .writeByte(0)                     // Cấp độ vật cưỡi
            .writeByte(0)                     // Số lượng trang bị
            .writeShortLE(record.titleId)     // ID danh hiệu
            .writeUIntLE(0)                   // ID quân đoàn
            .writeByte(0)                     // Số lượng trang bị hiển thị ngoài

        session.send(writer.toByteArray())
    }

    /** S:005-005 Người chơi biến hình: roleId(8) + npcId(2). */
    fun sendPlayerTransform(session: SessionSender, roleId: Long, npcId: Int) {
        session.send(
            PacketWriter(13)
                .writeByte(Opcodes.PLAYER_STATUS).writeByte(5).writeByte(0)
                .writeLongLE(roleId)
                .writeShortLE(npcId)
                .toByteArray()
        )
    }

    /**
     * S:005-012 Người chơi đổi danh hiệu
     *
     * Định dạng: +roleId(8) +titleId(2)
     */
    fun sendTitleChange(target: SessionSender, roleId: Long, titleId: Int) {
        target.send(
            PacketWriter()
                .writeByte(Opcodes.PLAYER_STATUS).writeByte(12).writeByte(0)
                .writeLongLE(roleId)
                .writeShortLE(titleId)
                .toByteArray()
        )
    }

    /** S:009-001 Tạo nhân vật thành công*/
    fun sendCreateCharSuccess(session: SessionSender) {
        session.send(
            PacketWriter(3)
                .writeByte(Opcodes.CREATE_CHAR).writeByte(1).writeByte(0)
                .toByteArray()
        )
    }

    /** S:009-003 Kết quả kiểm tra tên*/
    fun sendCheckNameResult(session: SessionSender, result: Int) {
        session.send(
            PacketWriter(4)
                .writeByte(Opcodes.CREATE_CHAR).writeByte(3).writeByte(0)
                .writeByte(result)
                .toByteArray()
        )
    }

    /** S:010-000 Phản hồi nhịp tim*/
    fun sendKeepalive(session: SessionSender) {
        session.send(
            PacketWriter(3)
                .writeByte(Opcodes.KEEPALIVE).writeByte(0).writeByte(0)
                .toByteArray()
        )
    }

    /** S:000-000: cause(1), messageString(4), NetworkDisconnectMessage(4). */
    fun sendDisconnect(session: SessionSender, reason: Int) {
        session.send(
            PacketWriter()
                .writeByte(Opcodes.DISCONNECT).writeByte(0).writeByte(0)
                .writeByte(reason)
                .writeIntLE(0) // No custom localized message (cause 61).
                .writeIntLE(0) // No diagnostic suffix.
                .toByteArray()
        )
    }

    /** S:003-000 Người chơi khác xuất hiện（bản rút gọn）*/
    fun sendOtherPlayerAppear(target: SessionSender, other: GameSession) {
        val writer = PacketWriter(128)
            .writeByte(Opcodes.CHARACTER).writeByte(0).writeByte(0)
            .writeLongLE(other.playerId)
            .writeByte(other.sex)
            .writeByte(other.element)
            .writeByte(other.turn3Element) // Nguyên tố chuyển sinh lần 3
            .writeByte(other.level)
            .writeByte(0)                  // Cờ đánh dấu suy thần
            .writeUIntLE(0)                // Cờ đánh dấu phúc thần
            .writeShortLE(0)               // Số tinh linh
            .writeShortLE(other.mapId)
            .writeShortLE(other.x)
            .writeShortLE(other.y)
            .writeByte(other.face)
            .writeByte(other.hair)
            .writeByte(0)                  // Dáng người
            .writeIntLE(other.color1)
            .writeIntLE(other.color2)
            .writeShortLE(0)               // Số trận thắng PVP
            .writeByte(0)                  // Hạng PVP
            .writeByte(1)                  // Trạng thái trực tuyến
            .writeByte(0)                  // Giải thách đấu NPC
            .writeByte(other.serverId)
            .writeByte(other.turn)         // Loại chuyển sinh
            .writeByte(other.career)       // Loại nghề nghiệp
            .writeBig5WithByteLen(other.characterName)
            .writeShortLE(0)               // Phân khu
            .writeShortLE(other.mountNpcId) // ID NPC vật cưỡi
            .writeByte(other.mountLevel)   // Cấp độ vật cưỡi
        // Ngoại hình trang bị
        val equips = other.equipItemIds
        writer.writeByte(equips.size)
        for (itemId in equips) {
            writer.writeShortLE(itemId)
        }
        writer
            .writeShortLE(other.titleId)   // ID danh hiệu
            .writeUIntLE(0)                // ID quân đoàn
            .writeByte(0)                  // Số lượng trang bị hiển thị ngoài

        target.send(writer.toByteArray())
    }

    /**
     * S:085-001 Dữ liệu đếm nhân vật
     *
     * Định dạng: số lượng(4 LE) + <<chỉ số(2 LE) + giá trị(4 LE) + giới hạn(4 LE)>>
     */
    fun sendRoleCountData(session: SessionSender, counts: Map<Int, Int>) {
        val writer = PacketWriter(3 + 4 + counts.size * 10)
            .writeByte(Opcodes.ROLE_COUNT_SYSTEM).writeByte(1).writeByte(0)
            .writeIntLE(counts.size)
        for ((index, value) in counts) {
            writer.writeShortLE(index)
                  .writeIntLE(DvtRoleCountWire.encodeValue(index, value))
                  .writeIntLE(0)               // max = 0
        }
        session.send(writer.toByteArray())
    }

    /**
     * S:040-001 Dữ liệu phím tắt
     *
     * Định dạng: PlayerMain [số trang(1)+số phím(1)+<<ID(2)>>] + PlayerSub [số trang(1)+số phím(1)+<<ID(2)>>]
     */
    fun sendHotkeyData(session: SessionSender, mainKeys: List<Int>, subKeys: List<Int>) {
        val writer = PacketWriter(3 + 2 + mainKeys.size * 2 + 2 + subKeys.size * 2)
            .writeByte(40).writeByte(1).writeByte(0)
        // Đoạn PlayerMain（phím tắt kỹ năng）
        writer.writeByte(1)
        writer.writeByte(mainKeys.size)
        for (id in mainKeys) {
            writer.writeShortLE(id)
        }
        // Đoạn PlayerSub（phím tắt vật phẩm）
        writer.writeByte(1)
        writer.writeByte(subKeys.size)
        for (id in subKeys) {
            writer.writeShortLE(id)
        }
        session.send(writer.toByteArray())
    }

    /**
     * S:040-002 kết quả thiết lập phím tắt
     *
     * Định dạng: result(1B) + [nếu thành công] dataIndex(1B) + pageIndex(1B) + keyIndex(1B) + useId(2B LE)
     */
    fun sendHotkeySetResult(session: SessionSender, result: Int,
                            dataIndex: Int = 0, pageIndex: Int = 0,
                            keyIndex: Int = 0, useId: Int = 0) {
        val writer = PacketWriter(if (result == 0) 9 else 4)
            .writeByte(40).writeByte(2).writeByte(0)
            .writeByte(result)
        if (result == 0) {
            writer.writeByte(dataIndex)
            writer.writeByte(pageIndex)
            writer.writeByte(keyIndex)
            writer.writeShortLE(useId)
        }
        session.send(writer.toByteArray())
    }
}
