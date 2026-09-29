package tw.tsm.app.response

import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.interfaces.MountAttribute
import tw.tsm.domain.interfaces.SessionSender
import tw.tsm.domain.valueobjects.MountData
import tw.tsm.protocol.PacketWriter
import tw.tsm.protocol.writeThingData

/**
 * 坐騎封包序列化 — mainKind=79 的 8 個 S→C 封包
 *
 * 來源：protocal.lua protocolTable[79]
 */
class MountResponseSender {

    /**
     * S:079-001 座騎資料（登入下發）
     *
     * 格式：+等級(1) +點數(2LE)*6 +裝備數量(1) <<+ThingData(35)>> +當前NPC ID(2LE)
     */
    fun sendMountData(session: SessionSender, data: MountData) {
        val equips = data.equips.values.filter { !it.isEmpty }
        val writer = PacketWriter(3 + 1 + 12 + 1 + equips.size * 35 + 2)
            .writeByte(Opcodes.MOUNT).writeByte(1).writeByte(0)
            .writeByte(data.level)

        // 6 個屬性點數（index 1-6）
        for (i in 1..6) {
            writer.writeShortLE(data.attributePoints[i])
        }

        // 裝備
        writer.writeByte(equips.size)
        for (equip in equips) {
            writer.writeThingData(equip)
        }

        // 當前坐騎 NPC ID
        writer.writeShortLE(data.currentNpcId)

        session.send(writer.toByteArray())
    }

    /**
     * S:079-002 設定座騎等級
     *
     * 格式：+等級(1)
     */
    fun sendMountLevel(session: SessionSender, level: Int) {
        val writer = PacketWriter(4)
            .writeByte(Opcodes.MOUNT).writeByte(2).writeByte(0)
            .writeByte(level)
        session.send(writer.toByteArray())
    }

    /**
     * S:079-003 設定座騎點數
     *
     * 格式：+種類(1) +點數(2LE)
     */
    fun sendMountAttributePoint(session: SessionSender, kind: Int, points: Int) {
        val writer = PacketWriter(6)
            .writeByte(Opcodes.MOUNT).writeByte(3).writeByte(0)
            .writeByte(kind)
            .writeShortLE(points)
        session.send(writer.toByteArray())
    }

    /**
     * S:079-004 穿上座騎裝備
     *
     * DVT Lua protocolTable[79][4] 讀 ReadUInt16().
     * 格式：+背包索引(2LE)
     */
    fun sendMountEquip(session: SessionSender, bagIndex: Int) {
        val writer = PacketWriter(5)
            .writeByte(Opcodes.MOUNT).writeByte(4).writeByte(0)
            .writeShortLE(bagIndex)
        session.send(writer.toByteArray())
    }

    /**
     * S:079-005 卸下座騎裝備
     *
     * DVT Lua protocolTable[79][5] 讀 Byte + UInt16.
     * 格式：+裝備部位(1) +背包索引(2LE)
     */
    fun sendMountUnequip(session: SessionSender, fitType: Int, bagIndex: Int) {
        val writer = PacketWriter(6)
            .writeByte(Opcodes.MOUNT).writeByte(5).writeByte(0)
            .writeByte(fitType)
            .writeShortLE(bagIndex)
        session.send(writer.toByteArray())
    }

    /**
     * S:079-006 切換座騎
     *
     * 格式：+NPC ID(2LE)
     */
    fun sendMountSwitch(session: SessionSender, npcId: Int) {
        val writer = PacketWriter(5)
            .writeByte(Opcodes.MOUNT).writeByte(6).writeByte(0)
            .writeShortLE(npcId)
        session.send(writer.toByteArray())
    }

    /**
     * S:079-007 設定玩家座騎（廣播用）
     *
     * 格式：+玩家ID(8LE) +NPC ID(2LE)
     */
    fun sendPlayerMount(session: SessionSender, playerId: Long, npcId: Int) {
        val writer = PacketWriter(13)
            .writeByte(Opcodes.MOUNT).writeByte(7).writeByte(0)
            .writeLongLE(playerId)
            .writeShortLE(npcId)
        session.send(writer.toByteArray())
    }

    /**
     * S:079-008 坐騎數值
     *
     * 格式：+數量(1) <<+種類(1) +正負號(1) +數值(4LE) +參數(4LE)>>
     */
    fun sendMountAttributes(session: SessionSender, attrs: List<MountAttribute>) {
        val writer = PacketWriter(4 + attrs.size * 10)
            .writeByte(Opcodes.MOUNT).writeByte(8).writeByte(0)
            .writeByte(attrs.size)
        for (attr in attrs) {
            writer.writeByte(attr.kind)
                .writeByte(attr.sign)
                .writeIntLE(attr.value)
                .writeIntLE(attr.arg)
        }
        session.send(writer.toByteArray())
    }
}
