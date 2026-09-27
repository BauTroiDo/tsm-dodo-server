package tw.tsm.app.response

import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.SessionSender
import tw.tsm.domain.interfaces.repositories.CharacterRecord
import tw.tsm.domain.valueobjects.PlayerDataDto
import tw.tsm.protocol.PacketWriter

/**
 * 登入/角色相關封包序列化
 *
 * mainKind = 0（斷線）, 1（登入）, 3（角色）, 5（玩家資料）, 9（創角）, 10（心跳）, 85（RoleCount）, 40（熱鍵）
 */
class LoginResponseSender {

    /**
     * S:001-002 登入結果
     *
     * 封包格式：
     * +Result(1) [成功時繼續] +DBID(2) +GSID(2) +PlayerId(8)
     * +ServerTime(Double/8) +ServerSwitch(1)
     * +L(1)+Account(Big5) +L(1)+Password(Big5) +AccountKind(1)
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
                .writeBig5WithByteLen(account)  // 帳號
                .writeBig5WithByteLen(password) // 密碼
                .writeByte(accountKind)         // 帳號綁定類型
        }

        session.send(writer.toByteArray())
    }

    /** S:001-003 登入成功 */
    fun sendLoginSuccess(session: SessionSender, needChangePassword: Boolean) {
        session.send(
            PacketWriter()
                .writeByte(Opcodes.LOGIN).writeByte(3).writeByte(0)
                .writeBoolean(needChangePassword)
                .toByteArray()
        )
    }

    /** S:001-013 登入序列完成 */
    fun sendLoginFinished(session: SessionSender, connectCode: Long) {
        session.send(
            PacketWriter()
                .writeByte(Opcodes.LOGIN).writeByte(13).writeByte(0)
                .writeIntLE(connectCode.toInt())
                .toByteArray()
        )
    }

    /**
     * S:005-003 玩家資料大包
     *
     * 來源：protocal.lua protocolTable[5][3] → Role.ReceivePlayerData(data)
     * 嚴格按照客戶端 Read 順序寫入
     */
    fun sendPlayerData(session: SessionSender, data: PlayerDataDto) {
        val writer = PacketWriter(256)
            .writeByte(Opcodes.PLAYER_STATUS)   // mainKind = 5
            .writeByte(3)                        // subKind = 3
            .writeByte(0)                        // compress
            // 基礎屬性
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
            // 裝備加值
            .writeIntLE(data.equipAtk)
            .writeIntLE(data.equipDef)
            .writeIntLE(data.equipInt)
            .writeIntLE(data.equipAgi)
            .writeIntLE(data.equipMaxHp)
            .writeIntLE(data.equipMaxSp)
            .writeIntLE(data.equipHpx)
            .writeIntLE(data.equipSpx)

        // 陣營好感度（5 個 UInt16）
        for (i in 0 until 5) {
            writer.writeShortLE(data.moral.getOrElse(i) { 0 })
        }

        writer
            .writeUIntLE(data.pracCount)
            .writeUIntLE(data.organization)
            .writeByte(data.autoEatCount)

        // 初轉技能列表
        writer.writeShortLE(data.skills.size)
        for ((skillId, skillLv) in data.skills) {
            writer.writeShortLE(skillId)
            writer.writeByte(skillLv)
        }

        // 三轉資料
        writer
            .writeByte(data.turn3Element)
            .writeLongLE(data.turn3Exp)
            .writeShortLE(data.turn3Int)
            .writeShortLE(data.turn3Atk)
            .writeShortLE(data.turn3Def)
            .writeShortLE(data.turn3Agi)
            .writeShortLE(data.turn3Hpx)
            .writeShortLE(data.turn3Spx)

        // 三轉技能列表
        writer.writeShortLE(data.turn3Skills.size)
        for ((skillId, skillLv) in data.turn3Skills) {
            writer.writeShortLE(skillId)
            writer.writeByte(skillLv)
        }

        // HP/SP 再寫一次（Role.lua 行 1700-1701）
        writer
            .writeUIntLE(data.hp)
            .writeShortLE(data.sp)

        // 新增動態屬性
        writer.writeByte(data.newAttrs.size)
        for ((attrType, attrValue) in data.newAttrs) {
            writer.writeByte(attrType)
            writer.writeIntLE(attrValue)
        }

        session.send(writer.toByteArray())
    }

    /** S:005-011 丹藥次數 */
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

    /** S:003-001 登入天數 */
    fun sendLoginDay(session: SessionSender, loginDay: Int, continueLogin: Int) {
        session.send(
            PacketWriter()
                .writeByte(Opcodes.CHARACTER).writeByte(1).writeByte(0)
                .writeShortLE(loginDay)
                .writeShortLE(continueLogin)
                .toByteArray()
        )
    }

    /** S:003-002 每日在線時間 */
    fun sendOnlineTime(session: SessionSender, time: Double) {
        session.send(
            PacketWriter()
                .writeByte(Opcodes.CHARACTER).writeByte(2).writeByte(0)
                .writeDoubleLE(time)
                .toByteArray()
        )
    }

    /** S:003-003 半月卡時間 */
    fun sendHalfMonthCardTime(session: SessionSender, time: Double) {
        session.send(
            PacketWriter()
                .writeByte(Opcodes.CHARACTER).writeByte(3).writeByte(0)
                .writeDoubleLE(time)
                .toByteArray()
        )
    }

    /**
     * S:003-000 玩家出現
     *
     * 來源：protocal.lua protocolTable[3][0] → Role.PlayerAppear(index, data)
     */
    fun sendPlayerAppear(session: SessionSender, playerId: Long, record: CharacterRecord, serverId: Int) {
        val writer = PacketWriter(128)
            .writeByte(Opcodes.CHARACTER).writeByte(0).writeByte(0)
            .writeLongLE(playerId)
            .writeByte(record.sex)
            .writeByte(record.element)
            .writeByte(0)                     // 三轉元素
            .writeByte(record.level)
            .writeByte(0)                     // 衰神標記
            .writeUIntLE(0)                   // 福神標記
            .writeShortLE(0)                  // 精靈 No
            .writeShortLE(record.mapId)
            .writeShortLE(record.x)
            .writeShortLE(record.y)
            .writeByte(0)                     // 臉圖
            .writeByte(record.hair)
            .writeByte(0)                     // 身型
            .writeIntLE(record.color1)
            .writeIntLE(record.color2)
            .writeShortLE(0)                  // PVP 勝場
            .writeByte(0)                     // PVP 名次
            .writeByte(1)                     // 在線狀態
            .writeByte(0)                     // NPC 挑戰賽
            .writeByte(serverId)
            .writeByte(0)                     // 轉生種類
            .writeByte(0)                     // 職業種類
            .writeBig5WithByteLen(record.name)
            .writeShortLE(0)                  // 分區
            .writeShortLE(0)                  // 坐騎 NPC ID
            .writeByte(0)                     // 坐騎等級
            .writeByte(0)                     // 裝備數量
            .writeShortLE(record.titleId)     // 稱號 ID
            .writeUIntLE(0)                   // 軍團 ID
            .writeByte(0)                     // 外顯裝備數量

        session.send(writer.toByteArray())
    }

    /**
     * S:005-012 玩家更換稱號
     *
     * 格式：+roleId(8) +titleId(2)
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

    /** S:009-001 創角成功 */
    fun sendCreateCharSuccess(session: SessionSender) {
        session.send(
            PacketWriter(3)
                .writeByte(Opcodes.CREATE_CHAR).writeByte(1).writeByte(0)
                .toByteArray()
        )
    }

    /** S:009-003 檢查名字結果 */
    fun sendCheckNameResult(session: SessionSender, result: Int) {
        session.send(
            PacketWriter(4)
                .writeByte(Opcodes.CREATE_CHAR).writeByte(3).writeByte(0)
                .writeByte(result)
                .toByteArray()
        )
    }

    /** S:010-000 心跳回應 */
    fun sendKeepalive(session: SessionSender) {
        session.send(
            PacketWriter(3)
                .writeByte(Opcodes.KEEPALIVE).writeByte(0).writeByte(0)
                .toByteArray()
        )
    }

    /** S:000-000 斷線通知 */
    fun sendDisconnect(session: SessionSender, reason: Int) {
        session.send(
            PacketWriter()
                .writeByte(Opcodes.DISCONNECT).writeByte(0).writeByte(0)
                .writeByte(reason)
                .toByteArray()
        )
    }

    /** S:003-000 其他玩家出現（簡易版） */
    fun sendOtherPlayerAppear(target: SessionSender, other: GameSession) {
        val writer = PacketWriter(128)
            .writeByte(Opcodes.CHARACTER).writeByte(0).writeByte(0)
            .writeLongLE(other.playerId)
            .writeByte(other.sex)
            .writeByte(other.element)
            .writeByte(0)                  // 三轉元素
            .writeByte(other.level)
            .writeByte(0)                  // 衰神標記
            .writeUIntLE(0)                // 福神標記
            .writeShortLE(0)               // 精靈 No
            .writeShortLE(other.mapId)
            .writeShortLE(other.x)
            .writeShortLE(other.y)
            .writeByte(other.face)
            .writeByte(other.hair)
            .writeByte(0)                  // 身型
            .writeIntLE(other.color1)
            .writeIntLE(other.color2)
            .writeShortLE(0)               // PVP 勝場
            .writeByte(0)                  // PVP 名次
            .writeByte(1)                  // 在線狀態
            .writeByte(0)                  // NPC 挑戰賽
            .writeByte(other.serverId)
            .writeByte(0)                  // 轉生種類
            .writeByte(0)                  // 職業種類
            .writeBig5WithByteLen(other.characterName)
            .writeShortLE(0)               // 分區
            .writeShortLE(other.mountNpcId) // 坐騎 NPC ID
            .writeByte(other.mountLevel)   // 坐騎等級
        // 裝備外觀
        val equips = other.equipItemIds
        writer.writeByte(equips.size)
        for (itemId in equips) {
            writer.writeShortLE(itemId)
        }
        writer
            .writeShortLE(other.titleId)   // 稱號 ID
            .writeUIntLE(0)                // 軍團 ID
            .writeByte(0)                  // 外顯裝備數量

        target.send(writer.toByteArray())
    }

    /**
     * S:085-001 角色計數資料
     *
     * 格式：數量(4 LE) + <<索引(2 LE) + 數值(4 LE) + 上限(4 LE)>>
     */
    fun sendRoleCountData(session: SessionSender, counts: Map<Int, Int>) {
        val writer = PacketWriter(3 + 4 + counts.size * 10)
            .writeByte(Opcodes.ROLE_COUNT_SYSTEM).writeByte(1).writeByte(0)
            .writeIntLE(counts.size)
        for ((index, value) in counts) {
            writer.writeShortLE(index)
                  .writeIntLE(value)
                  .writeIntLE(0)               // max = 0
        }
        session.send(writer.toByteArray())
    }

    /**
     * S:040-001 熱鍵資料
     *
     * 格式：PlayerMain [頁數(1)+鍵數(1)+<<ID(2)>>] + PlayerSub [頁數(1)+鍵數(1)+<<ID(2)>>]
     */
    fun sendHotkeyData(session: SessionSender, mainKeys: List<Int>, subKeys: List<Int>) {
        val writer = PacketWriter(3 + 2 + mainKeys.size * 2 + 2 + subKeys.size * 2)
            .writeByte(40).writeByte(1).writeByte(0)
        // PlayerMain 段（技能快捷）
        writer.writeByte(1)
        writer.writeByte(mainKeys.size)
        for (id in mainKeys) {
            writer.writeShortLE(id)
        }
        // PlayerSub 段（物品快捷）
        writer.writeByte(1)
        writer.writeByte(subKeys.size)
        for (id in subKeys) {
            writer.writeShortLE(id)
        }
        session.send(writer.toByteArray())
    }

    /**
     * S:040-002 快捷鍵設定結果
     *
     * 格式：result(1B) + [若成功] dataIndex(1B) + pageIndex(1B) + keyIndex(1B) + useId(2B LE)
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
