package tw.tsm.app.response

import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.interfaces.MountAttribute
import tw.tsm.domain.interfaces.SessionSender
import tw.tsm.domain.valueobjects.MountData
import tw.tsm.protocol.PacketWriter
import tw.tsm.protocol.writeThingData

/**
 * Tuần tự hoá gói tin vật cưỡi — 8 gói S→C của mainKind=79
 *
 * Nguồn: protocal.lua protocolTable[79]
 */
class MountResponseSender {

    /**
     * S:079-001 Dữ liệu vật cưỡi (gửi xuống khi đăng nhập)
     *
     * Định dạng: +cấp độ(1) +điểm(2LE)*6 +số lượng trang bị(1) <<+ThingData(35)>> +NPC ID hiện tại(2LE)
     */
    fun sendMountData(session: SessionSender, data: MountData) {
        val equips = data.equips.values.filter { !it.isEmpty }
        val writer = PacketWriter(3 + 1 + 12 + 1 + equips.size * 35 + 2)
            .writeByte(Opcodes.MOUNT).writeByte(1).writeByte(0)
            .writeByte(data.level)

        // 6 điểm thuộc tính（index 1-6）
        for (i in 1..6) {
            writer.writeShortLE(data.attributePoints[i])
        }

        // trang bị
        writer.writeByte(equips.size)
        for (equip in equips) {
            writer.writeThingData(equip)
        }

        // ID NPC vật cưỡi hiện tại
        writer.writeShortLE(data.currentNpcId)

        session.send(writer.toByteArray())
    }

    /**
     * S:079-002 Thiết lập cấp độ vật cưỡi
     *
     * Định dạng: +cấp độ(1)
     */
    fun sendMountLevel(session: SessionSender, level: Int) {
        val writer = PacketWriter(4)
            .writeByte(Opcodes.MOUNT).writeByte(2).writeByte(0)
            .writeByte(level)
        session.send(writer.toByteArray())
    }

    /**
     * S:079-003 Thiết lập điểm vật cưỡi
     *
     * Định dạng: +loại(1) +điểm(2LE)
     */
    fun sendMountAttributePoint(session: SessionSender, kind: Int, points: Int) {
        val writer = PacketWriter(6)
            .writeByte(Opcodes.MOUNT).writeByte(3).writeByte(0)
            .writeByte(kind)
            .writeShortLE(points)
        session.send(writer.toByteArray())
    }

    /**
     * S:079-004 Mặc trang bị cho vật cưỡi
     *
     * DVT Lua protocolTable[79][4] đọc ReadUInt16().
     * Định dạng: +chỉ số túi đồ(2LE)
     */
    fun sendMountEquip(session: SessionSender, bagIndex: Int) {
        val writer = PacketWriter(5)
            .writeByte(Opcodes.MOUNT).writeByte(4).writeByte(0)
            .writeShortLE(bagIndex)
        session.send(writer.toByteArray())
    }

    /**
     * S:079-005 Tháo trang bị vật cưỡi
     *
     * DVT Lua protocolTable[79][5] đọc Byte + UInt16.
     * Định dạng: +vị trí trang bị(1) +chỉ số túi đồ(2LE)
     */
    fun sendMountUnequip(session: SessionSender, fitType: Int, bagIndex: Int) {
        val writer = PacketWriter(6)
            .writeByte(Opcodes.MOUNT).writeByte(5).writeByte(0)
            .writeByte(fitType)
            .writeShortLE(bagIndex)
        session.send(writer.toByteArray())
    }

    /**
     * S:079-006 Chuyển đổi vật cưỡi
     *
     * Định dạng: +NPC ID(2LE)
     */
    fun sendMountSwitch(session: SessionSender, npcId: Int) {
        val writer = PacketWriter(5)
            .writeByte(Opcodes.MOUNT).writeByte(6).writeByte(0)
            .writeShortLE(npcId)
        session.send(writer.toByteArray())
    }

    /**
     * S:079-007 Thiết lập vật cưỡi của người chơi (dùng để phát rộng)
     *
     * Định dạng: +ID người chơi(8LE) +NPC ID(2LE)
     */
    fun sendPlayerMount(session: SessionSender, playerId: Long, npcId: Int) {
        val writer = PacketWriter(13)
            .writeByte(Opcodes.MOUNT).writeByte(7).writeByte(0)
            .writeLongLE(playerId)
            .writeShortLE(npcId)
        session.send(writer.toByteArray())
    }

    /**
     * S:079-008 Chỉ số vật cưỡi
     *
     * Định dạng: +số lượng(1) <<+loại(1) +dấu âm dương(1) +chỉ số(4LE) +tham số(4LE)>>
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
